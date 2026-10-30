package univer.university.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import univer.university.component.CurrentUserProvider;
import univer.university.component.DateUtils;
import univer.university.component.ReceiptRenderer;
import univer.university.dto.request.PrintRequests;
import univer.university.dto.response.PrintResponses.AgentJob;
import univer.university.dto.response.PrintResponses.PrintJobDto;
import univer.university.dto.response.PrintResponses.PrintStatus;
import univer.university.dto.response.PrintResponses.TerminalDto;
import univer.university.dto.response.PrintResponses.TerminalInstall;
import univer.university.dto.response.SettingsResponses.StoreSettingsDto;
import univer.university.entity.PrintJob;
import univer.university.entity.PrintTerminal;
import univer.university.entity.enums.PrintJobStatus;
import univer.university.entity.enums.PrintJobType;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.PrintJobRepository;
import univer.university.repository.PrintTerminalRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Kassa printeri navbati. Server printerga to'g'ridan-to'g'ri ulana olmaydi (printer kassa kompyuterida, USB),
 * shuning uchun har bir kassa kompyuteridagi print-agent shu navbatni so'rab (long-poll) turadi va ESC/POS baytlarini
 * printerga beradi. Kassalar ({@link PrintTerminal}) o'z tokeni bilan ishlaydi; chek X-Kassa-Id dagi kassaga yo'naltiriladi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintService {

    private static final int MAX_ATTEMPTS = 3;
    /** Agent olgan, lekin shu vaqt ichida javob bermagan vazifa qayta navbatga qaytadi. */
    private static final int STUCK_SECONDS = 120;
    private static final int ONLINE_SECONDS = 60;
    private static final int MAX_WAIT_SECONDS = 25;
    /** Agent holati xotirada; bazaga shu oraliqda (yoki o'zgarganda) yoziladi. */
    private static final int PERSIST_SECONDS = 300;
    /** Eski umumiy token (PRINT_AGENT_TOKEN) bilan ishlovchi agent xotiradagi kaliti. */
    private static final long LEGACY = 0L;
    private static final Pattern SAFE_URL = Pattern.compile("^https?://[A-Za-z0-9.\\-]+(:\\d{1,5})?(/[A-Za-z0-9._\\-/]*)?$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PrintJobRepository jobs;
    private final PrintTerminalRepository terminals;
    private final PosService pos;
    private final ReturnService returns;
    private final SettingsService settings;
    private final ReceiptRenderer renderer;
    private final CurrentUserProvider currentUser;
    private final TransactionTemplate tx;

    @Value("${app.print.auto:true}")
    private boolean auto;

    @Value("${app.print.agent-token:}")
    private String legacyToken;

    @Value("${app.print.max-age-minutes:15}")
    private int maxAgeMinutes;

    /** O'rnatish faylidagi server manzili; bo'sh bo'lsa so'rov kelgan manzil. */
    @Value("${app.print.public-url:}")
    private String publicUrl;

    private final Map<Long, Presence> presence = new ConcurrentHashMap<>();

    private byte[] agentScript;
    private String agentVersion;
    private String installerScript;
    private String cmdTemplate;

    /** terminalId null — eski umumiy token. */
    private record Agent(Long terminalId) {
        long key() {
            return terminalId == null ? LEGACY : terminalId;
        }
    }

    private record Presence(LocalDateTime seen, String printer, String host, String version, LocalDateTime persistedAt) {
    }

    @PostConstruct
    void loadAgentFiles() {
        agentScript = resource("print-agent/print-agent.ps1");
        agentVersion = HexFormat.of().formatHex(sha256(agentScript));
        installerScript = new String(resource("print-agent/install.ps1"), StandardCharsets.UTF_8);
        cmdTemplate = new String(resource("print-agent/DavronPrinter.cmd"), StandardCharsets.UTF_8);
    }

    // ---------- navbat ----------

    /** Sotuv/vozvrat commit bo'lgandan keyin; navbatga qo'yish xatosi sotuvga ta'sir qilmaydi. */
    public void autoEnqueue(PrintJobType type, Long refId, String kassaId) {
        if (!auto) {
            return;
        }
        try {
            enqueue(type, refId, kassaId);
        } catch (RuntimeException e) {
            log.warn("Chekni chop etish navbatiga qo'yib bo'lmadi: {} #{}", type, refId, e);
        }
    }

    /** kassaId — brauzer yuborgan X-Kassa-Id; noma'lum yoki o'chirilgan kassa bo'lsa istalgan kassa chiqaradi. */
    @Transactional
    public PrintJobDto enqueue(PrintJobType type, Long refId, String kassaId) {
        if (type == PrintJobType.SALE) {
            pos.get(refId);
        } else if (type == PrintJobType.RETURN) {
            returns.get(refId);
        }
        return toDto(create(type, refId, activeTerminal(kassaId).map(PrintTerminal::getId).orElse(null), currentUser.id()));
    }

    private PrintJob create(PrintJobType type, Long refId, Long terminalId, Long userId) {
        return jobs.save(PrintJob.builder().type(type).refId(refId).terminalId(terminalId).status(PrintJobStatus.NEW)
                .createdAt(DateUtils.now()).userId(userId).build());
    }

    @Transactional(readOnly = true)
    public List<PrintJobDto> recent() {
        return jobs.findTop50ByOrderByIdDesc().stream().map(PrintService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PrintStatus status(String kassaId) {
        long queued = jobs.countByStatus(PrintJobStatus.NEW);
        Optional<PrintTerminal> terminal = activeTerminal(kassaId);
        if (terminal.isPresent()) {
            TerminalDto t = toDto(terminal.get());
            return new PrintStatus(t.id(), t.name(), t.online(), t.printerConnected(), t.lastSeen(), t.printerName(),
                    queued, auto);
        }
        // kassa ko'rsatilmagan: printeri ulangan onlayn agent bo'lsa o'sha, bo'lmasa oxirgi ko'ringan
        Presence best = presence.values().stream()
                .max(Comparator.comparing((Presence p) -> isOnline(p.seen()) && hasText(p.printer()))
                        .thenComparing(Presence::seen))
                .orElse(null);
        if (best == null) {
            return new PrintStatus(null, null, false, false, null, null, queued, auto);
        }
        boolean online = isOnline(best.seen());
        return new PrintStatus(null, null, online, online && hasText(best.printer()), best.seen(), best.printer(),
                queued, auto);
    }

    // ---------- kassalar ----------

    @Transactional(readOnly = true)
    public List<TerminalDto> terminals() {
        return terminals.findByActiveTrueOrderById().stream().map(this::toDto).toList();
    }

    @Transactional
    public TerminalInstall createTerminal(PrintRequests.CreateTerminal req, String requestBaseUrl) {
        String server = serverUrl(requestBaseUrl);
        String name = req == null || !hasText(req.name()) ? "Kassa " + (terminals.countByActiveTrue() + 1) : req.name().trim();
        String token = newToken();
        PrintTerminal t = terminals.save(PrintTerminal.builder().name(name).tokenHash(hash(token)).active(true)
                .createdAt(DateUtils.now()).createdBy(currentUser.id()).build());
        return install(t, token, server);
    }

    /** Yangi o'rnatish fayli; eski fayl va shu kassadagi agent tokeni bekor bo'ladi. */
    @Transactional
    public TerminalInstall reissue(Long id, String requestBaseUrl) {
        String server = serverUrl(requestBaseUrl);
        PrintTerminal t = terminal(id);
        String token = newToken();
        t.setTokenHash(hash(token));
        return install(t, token, server);
    }

    @Transactional
    public TerminalDto rename(Long id, PrintRequests.RenameTerminal req) {
        PrintTerminal t = terminal(id);
        t.setName(req.name().trim());
        return toDto(t);
    }

    /** Kassani o'chirish: agenti endi ulana olmaydi, unga yo'naltirilgan cheklar navbatda eskiradi. */
    @Transactional
    public void deleteTerminal(Long id) {
        terminal(id).setActive(false);
        presence.remove(id);
    }

    // ---------- print-agent ----------

    /** Long-poll: wait soniyagacha yangi vazifa kutadi; bo'lmasa bo'sh. Printer topilmagan agent faqat "tirikman" deydi. */
    public Optional<AgentJob> next(String token, String printer, String host, String version, int waitSeconds) {
        Agent agent = agent(token);
        long deadline = System.currentTimeMillis() + Math.max(0, Math.min(waitSeconds, MAX_WAIT_SECONDS)) * 1000L;
        while (true) {
            touch(agent, printer, host, version);
            if (!hasText(printer)) {
                return Optional.empty();
            }
            Optional<AgentJob> job = claim(agent);
            if (job.isPresent() || System.currentTimeMillis() >= deadline) {
                return job;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }

    @Transactional
    public void done(String token, Long id) {
        PrintJob j = agentJob(agent(token), id);
        j.setStatus(PrintJobStatus.DONE);
        j.setPrintedAt(DateUtils.now());
        j.setError(null);
    }

    @Transactional
    public void failed(String token, Long id, String error) {
        PrintJob j = agentJob(agent(token), id);
        j.setError(truncate(error == null || error.isBlank() ? "Printer xatosi" : error));
        j.setStatus(j.getAttempts() >= MAX_ATTEMPTS ? PrintJobStatus.FAILED : PrintJobStatus.NEW);
    }

    /** O'rnatuvchi chaqiradi: shu kassaga test chek. */
    @Transactional
    public PrintJobDto agentTest(String token) {
        return toDto(create(PrintJobType.TEST, null, agent(token).terminalId(), null));
    }

    @Transactional(readOnly = true)
    public TerminalDto agentSelf(String token) {
        Agent agent = agent(token);
        if (agent.terminalId() != null) {
            return toDto(terminal(agent.terminalId()));
        }
        Presence p = presence.get(LEGACY);
        LocalDateTime seen = p == null ? null : p.seen();
        String printer = p == null ? null : p.printer();
        return new TerminalDto(null, "Umumiy agent", p == null ? null : p.host(), isOnline(seen),
                isOnline(seen) && hasText(printer), printer, seen, p == null ? null : p.version(), null);
    }

    public byte[] agentScript(String token) {
        agent(token);
        return agentScript;
    }

    public String agentVersion(String token) {
        agent(token);
        return agentVersion;
    }

    public String installerScript(String token) {
        agent(token);
        return installerScript;
    }

    private Optional<AgentJob> claim(Agent agent) {
        // chek matnini yaratib bo'lmasa (masalan sotuv o'chirilgan) keyingisiga o'tamiz
        for (int i = 0; i < 5; i++) {
            PrintJob j = tx.execute(s -> {
                LocalDateTime now = DateUtils.now();
                jobs.expireOlderThan(now.minusMinutes(maxAgeMinutes));
                jobs.releaseStuck(now.minusSeconds(STUCK_SECONDS));
                List<PrintJob> next = jobs.lockNext(agent.terminalId(), PageRequest.of(0, 1));
                if (next.isEmpty()) {
                    return null;
                }
                PrintJob job = next.get(0);
                if (job.getAttempts() >= MAX_ATTEMPTS) {
                    job.setStatus(PrintJobStatus.FAILED);
                    job.setError(truncate("Printer " + MAX_ATTEMPTS + " marta javob bermadi"));
                    return job;
                }
                job.setAttempts(job.getAttempts() + 1);
                job.setStatus(PrintJobStatus.PRINTING);
                job.setTakenAt(now);
                return job;
            });
            if (j == null) {
                return Optional.empty();
            }
            if (j.getStatus() == PrintJobStatus.FAILED) {
                continue;
            }
            try {
                byte[] data = render(j.getType(), j.getRefId());
                return Optional.of(new AgentJob(j.getId(), j.getType(), Base64.getEncoder().encodeToString(data)));
            } catch (RuntimeException e) {
                log.warn("Chek matnini yaratib bo'lmadi: print job #{}", j.getId(), e);
                tx.executeWithoutResult(s -> {
                    PrintJob job = find(j.getId());
                    job.setStatus(PrintJobStatus.FAILED);
                    job.setError(truncate("Chek matnini yaratib bo'lmadi: " + e.getMessage()));
                });
            }
        }
        return Optional.empty();
    }

    private byte[] render(PrintJobType type, Long refId) {
        StoreSettingsDto store = settings.store();
        return switch (type) {
            case SALE -> renderer.sale(pos.get(refId), store);
            case RETURN -> renderer.saleReturn(returns.get(refId), store);
            case TEST -> renderer.test(store, DateUtils.now());
        };
    }

    /** Kassa tokeni yoki eski umumiy token (PRINT_AGENT_TOKEN). */
    private Agent agent(String token) {
        if (!hasText(token)) {
            throw new BusinessException("Agent tokeni yo'q", HttpStatus.UNAUTHORIZED);
        }
        Optional<PrintTerminal> t = terminals.findByTokenHashAndActiveTrue(hash(token));
        if (t.isPresent()) {
            return new Agent(t.get().getId());
        }
        if (hasText(legacyToken)
                && MessageDigest.isEqual(legacyToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            return new Agent(null);
        }
        throw new BusinessException("Agent tokeni noto'g'ri yoki kassa o'chirilgan", HttpStatus.UNAUTHORIZED);
    }

    private void touch(Agent agent, String printer, String host, String version) {
        LocalDateTime now = DateUtils.now();
        String p = clip(printer, 120);
        String h = clip(host, 100);
        String v = clip(version, 64);
        Presence old = presence.get(agent.key());
        boolean changed = old == null || !Objects.equals(old.printer(), p) || !Objects.equals(old.host(), h)
                || !Objects.equals(old.version(), v);
        LocalDateTime persistedAt = old == null ? null : old.persistedAt();
        if (agent.terminalId() != null && (changed || persistedAt == null
                || persistedAt.isBefore(now.minusSeconds(PERSIST_SECONDS)))) {
            tx.executeWithoutResult(s -> terminals.findById(agent.terminalId()).ifPresent(t -> {
                t.setLastSeen(now);
                t.setPrinterName(p);
                t.setHostName(h);
                t.setAgentVersion(v);
            }));
            persistedAt = now;
        }
        presence.put(agent.key(), new Presence(now, p, h, v, persistedAt));
    }

    private PrintJob agentJob(Agent agent, Long id) {
        PrintJob j = find(id);
        if (j.getTerminalId() != null && !j.getTerminalId().equals(agent.terminalId())) {
            throw new NotFoundException("Chop etish vazifasi topilmadi");
        }
        return j;
    }

    private Optional<PrintTerminal> activeTerminal(String kassaId) {
        if (!hasText(kassaId)) {
            return Optional.empty();
        }
        try {
            return terminals.findById(Long.parseLong(kassaId.trim())).filter(PrintTerminal::isActive);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private PrintTerminal terminal(Long id) {
        return terminals.findById(id).filter(PrintTerminal::isActive)
                .orElseThrow(() -> new NotFoundException("Kassa topilmadi"));
    }

    private TerminalInstall install(PrintTerminal t, String token, String server) {
        String script = cmdTemplate.replace("{{SERVER}}", server).replace("{{TOKEN}}", token)
                .replace("{{ID}}", String.valueOf(t.getId()))
                .replace("\r\n", "\n").replace("\n", "\r\n");
        return new TerminalInstall(toDto(t), "DavronPrinter-kassa" + t.getId() + ".cmd", script);
    }

    private String serverUrl(String requestBaseUrl) {
        String url = (hasText(publicUrl) ? publicUrl : requestBaseUrl).trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!SAFE_URL.matcher(url).matches()) {
            throw BusinessException.badRequest("Server manzilini aniqlab bo'lmadi (PRINT_PUBLIC_URL ni sozlang): " + url);
        }
        return url;
    }

    private TerminalDto toDto(PrintTerminal t) {
        Presence p = presence.get(t.getId());
        LocalDateTime seen = p != null ? p.seen() : t.getLastSeen();
        String printer = p != null ? p.printer() : t.getPrinterName();
        boolean online = isOnline(seen);
        return new TerminalDto(t.getId(), t.getName(), p != null ? p.host() : t.getHostName(), online,
                online && hasText(printer), printer, seen, p != null ? p.version() : t.getAgentVersion(), t.getCreatedAt());
    }

    private static boolean isOnline(LocalDateTime seen) {
        return seen != null && seen.isAfter(DateUtils.now().minusSeconds(ONLINE_SECONDS));
    }

    private PrintJob find(Long id) {
        return jobs.findById(id).orElseThrow(() -> new NotFoundException("Chop etish vazifasi topilmadi"));
    }

    private static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String hash(String token) {
        return HexFormat.of().formatHex(sha256(token.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] resource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Resurs topilmadi: " + path, e);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String clip(String s, int max) {
        if (!hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String truncate(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    private static PrintJobDto toDto(PrintJob j) {
        return new PrintJobDto(j.getId(), j.getType(), j.getRefId(), j.getTerminalId(), j.getStatus(), j.getCreatedAt(),
                j.getPrintedAt(), j.getAttempts(), j.getError());
    }
}
