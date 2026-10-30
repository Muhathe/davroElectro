package univer.university.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import univer.university.component.CurrentUserProvider;
import univer.university.component.DateUtils;
import univer.university.component.ReceiptRenderer;
import univer.university.dto.response.PrintResponses.AgentJob;
import univer.university.dto.response.PrintResponses.PrintJobDto;
import univer.university.dto.response.PrintResponses.PrintStatus;
import univer.university.dto.response.SettingsResponses.StoreSettingsDto;
import univer.university.entity.PrintJob;
import univer.university.entity.enums.PrintJobStatus;
import univer.university.entity.enums.PrintJobType;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.PrintJobRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Kassa printeri navbati. Server printerga to'g'ridan-to'g'ri ulana olmaydi (printer kassa kompyuterida, USB),
 * shuning uchun kassadagi print-agent shu navbatni so'rab (long-poll) turadi va ESC/POS baytlarini printerga beradi.
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

    private final PrintJobRepository jobs;
    private final PosService pos;
    private final ReturnService returns;
    private final SettingsService settings;
    private final ReceiptRenderer renderer;
    private final CurrentUserProvider currentUser;
    private final TransactionTemplate tx;

    @Value("${app.print.auto:true}")
    private boolean auto;

    @Value("${app.print.agent-token:}")
    private String agentToken;

    @Value("${app.print.max-age-minutes:15}")
    private int maxAgeMinutes;

    private volatile LocalDateTime agentLastSeen;
    private volatile String agentPrinter;

    /** Sotuv/vozvrat commit bo'lgandan keyin; navbatga qo'yish xatosi sotuvga ta'sir qilmaydi. */
    public void autoEnqueue(PrintJobType type, Long refId) {
        if (!auto) {
            return;
        }
        try {
            enqueue(type, refId);
        } catch (RuntimeException e) {
            log.warn("Chekni chop etish navbatiga qo'yib bo'lmadi: {} #{}", type, refId, e);
        }
    }

    @Transactional
    public PrintJobDto enqueue(PrintJobType type, Long refId) {
        if (type == PrintJobType.SALE) {
            pos.get(refId);
        } else if (type == PrintJobType.RETURN) {
            returns.get(refId);
        }
        PrintJob j = jobs.save(PrintJob.builder().type(type).refId(refId).status(PrintJobStatus.NEW)
                .createdAt(DateUtils.now()).userId(currentUser.id()).build());
        return toDto(j);
    }

    @Transactional(readOnly = true)
    public List<PrintJobDto> recent() {
        return jobs.findTop50ByOrderByIdDesc().stream().map(PrintService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PrintStatus status() {
        LocalDateTime seen = agentLastSeen;
        boolean online = seen != null && seen.isAfter(DateUtils.now().minusSeconds(ONLINE_SECONDS));
        return new PrintStatus(online, seen, agentPrinter, jobs.countByStatus(PrintJobStatus.NEW), auto);
    }

    // ---------- print-agent ----------

    /** Long-poll: wait soniyagacha yangi vazifa kutadi; bo'lmasa bo'sh. */
    public Optional<AgentJob> next(String token, String printer, int waitSeconds) {
        checkAgent(token);
        long deadline = System.currentTimeMillis() + Math.max(0, Math.min(waitSeconds, MAX_WAIT_SECONDS)) * 1000L;
        while (true) {
            agentLastSeen = DateUtils.now();
            agentPrinter = printer;
            Optional<AgentJob> job = claim();
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
        checkAgent(token);
        PrintJob j = find(id);
        j.setStatus(PrintJobStatus.DONE);
        j.setPrintedAt(DateUtils.now());
        j.setError(null);
    }

    @Transactional
    public void failed(String token, Long id, String error) {
        checkAgent(token);
        PrintJob j = find(id);
        j.setError(truncate(error == null || error.isBlank() ? "Printer xatosi" : error));
        j.setStatus(j.getAttempts() >= MAX_ATTEMPTS ? PrintJobStatus.FAILED : PrintJobStatus.NEW);
    }

    private Optional<AgentJob> claim() {
        // chek matnini yaratib bo'lmasa (masalan sotuv o'chirilgan) keyingisiga o'tamiz
        for (int i = 0; i < 5; i++) {
            PrintJob j = tx.execute(s -> {
                LocalDateTime now = DateUtils.now();
                jobs.expireOlderThan(now.minusMinutes(maxAgeMinutes));
                jobs.releaseStuck(now.minusSeconds(STUCK_SECONDS));
                List<PrintJob> next = jobs.lockNext(PageRequest.of(0, 1));
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

    private void checkAgent(String token) {
        if (agentToken == null || agentToken.isBlank()) {
            throw new BusinessException("Print-agent sozlanmagan (PRINT_AGENT_TOKEN)", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (token == null || !MessageDigest.isEqual(agentToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("Agent tokeni noto'g'ri", HttpStatus.UNAUTHORIZED);
        }
    }

    private PrintJob find(Long id) {
        return jobs.findById(id).orElseThrow(() -> new NotFoundException("Chop etish vazifasi topilmadi"));
    }

    private static String truncate(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    private static PrintJobDto toDto(PrintJob j) {
        return new PrintJobDto(j.getId(), j.getType(), j.getRefId(), j.getStatus(), j.getCreatedAt(), j.getPrintedAt(),
                j.getAttempts(), j.getError());
    }
}
