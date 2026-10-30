package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import univer.university.dto.request.PrintRequests;
import univer.university.dto.response.PrintResponses.AgentJob;
import univer.university.dto.response.PrintResponses.PrintJobDto;
import univer.university.dto.response.PrintResponses.PrintStatus;
import univer.university.dto.response.PrintResponses.TerminalDto;
import univer.university.dto.response.PrintResponses.TerminalInstall;
import univer.university.entity.enums.PrintJobType;
import univer.university.service.PrintService;

import java.util.List;

/**
 * Chek printeri. Brauzer o'zi turgan kassani X-Kassa-Id sarlavhasida yuboradi (kassa yaratilganda qaytgan id),
 * shunda chek aynan shu kompyuter printeridan chiqadi.
 */
@RestController
@RequiredArgsConstructor
public class PrintController {

    public static final String KASSA = "X-Kassa-Id";
    private static final String TOKEN = "X-Agent-Token";
    private static final MediaType TEXT = new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);

    private final PrintService print;

    @GetMapping("/api/print/status")
    @PreAuthorize("isAuthenticated()")
    public PrintStatus status(@RequestHeader(value = KASSA, required = false) String kassa) {
        return print.status(kassa);
    }

    @GetMapping("/api/print/jobs")
    @PreAuthorize("@perm.view('KASSA')")
    public List<PrintJobDto> jobs() {
        return print.recent();
    }

    /** Test chek: terminalId (yoki X-Kassa-Id) dagi kassaga, ikkalasi ham bo'lmasa istalgan kassaga. */
    @PostMapping("/api/print/test")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('PRINTER')")
    public PrintJobDto test(@RequestParam(required = false) String terminalId,
                            @RequestHeader(value = KASSA, required = false) String kassa) {
        return print.enqueue(PrintJobType.TEST, null, terminalId != null ? terminalId : kassa);
    }

    /** Chekni qayta chop etish. */
    @PostMapping("/api/pos/sales/{id}/print")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.view('KASSA')")
    public PrintJobDto printSale(@PathVariable Long id, @RequestHeader(value = KASSA, required = false) String kassa) {
        return print.enqueue(PrintJobType.SALE, id, kassa);
    }

    @PostMapping("/api/returns/{id}/print")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.view('VOZVRAT')")
    public PrintJobDto printReturn(@PathVariable Long id, @RequestHeader(value = KASSA, required = false) String kassa) {
        return print.enqueue(PrintJobType.RETURN, id, kassa);
    }

    // ---------- kassalar (print-agent o'rnatilgan kompyuterlar) ----------

    @GetMapping("/api/print/terminals")
    @PreAuthorize("@perm.view('KASSA')")
    public List<TerminalDto> terminals() {
        return print.terminals();
    }

    /** Yangi kassa: javobda shu kompyuterda ishga tushiriladigan DavronPrinter.cmd matni. */
    @PostMapping("/api/print/terminals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('PRINTER')")
    public TerminalInstall createTerminal(@Valid @RequestBody(required = false) PrintRequests.CreateTerminal req) {
        return print.createTerminal(req, baseUrl());
    }

    /** Qayta o'rnatish fayli (eski fayl va agent tokeni bekor bo'ladi). */
    @PostMapping("/api/print/terminals/{id}/installer")
    @PreAuthorize("@perm.edit('PRINTER')")
    public TerminalInstall reissue(@PathVariable Long id) {
        return print.reissue(id, baseUrl());
    }

    @PutMapping("/api/print/terminals/{id}")
    @PreAuthorize("@perm.edit('PRINTER')")
    public TerminalDto rename(@PathVariable Long id, @Valid @RequestBody PrintRequests.RenameTerminal req) {
        return print.rename(id, req);
    }

    @DeleteMapping("/api/print/terminals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.delete('PRINTER')")
    public void deleteTerminal(@PathVariable Long id) {
        print.deleteTerminal(id);
    }

    // ---------- print-agent (JWT emas, X-Agent-Token bilan) ----------

    /** 200 — vazifa, 204 — wait soniya ichida vazifa bo'lmadi (yoki printer ulanmagan). */
    @GetMapping("/api/print-agent/jobs/next")
    public ResponseEntity<AgentJob> next(@RequestHeader(value = TOKEN, required = false) String token,
                                         @RequestHeader(value = "X-Agent-Printer", required = false) String printer,
                                         @RequestHeader(value = "X-Agent-Host", required = false) String host,
                                         @RequestHeader(value = "X-Agent-Version", required = false) String version,
                                         @RequestParam(defaultValue = "20") int wait) {
        return print.next(token, printer, host, version, wait).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/print-agent/jobs/{id}/done")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void done(@RequestHeader(value = TOKEN, required = false) String token, @PathVariable Long id) {
        print.done(token, id);
    }

    @PostMapping("/api/print-agent/jobs/{id}/failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void failed(@RequestHeader(value = TOKEN, required = false) String token, @PathVariable Long id,
                       @RequestBody(required = false) PrintRequests.AgentFailure body) {
        print.failed(token, id, body == null ? null : body.error());
    }

    @GetMapping("/api/print-agent/me")
    public TerminalDto agentSelf(@RequestHeader(value = TOKEN, required = false) String token) {
        return print.agentSelf(token);
    }

    @PostMapping("/api/print-agent/test")
    @ResponseStatus(HttpStatus.CREATED)
    public PrintJobDto agentTest(@RequestHeader(value = TOKEN, required = false) String token) {
        return print.agentTest(token);
    }

    /** DavronPrinter.cmd yuklab oladigan o'rnatuvchi skript. */
    @GetMapping("/api/print-agent/installer")
    public ResponseEntity<String> installer(@RequestHeader(value = TOKEN, required = false) String token) {
        return ResponseEntity.ok().contentType(TEXT).body(print.installerScript(token));
    }

    /** Agentning joriy versiyasi (skript SHA-256 si); agent soatda bir solishtirib, o'zini yangilaydi. */
    @GetMapping("/api/print-agent/version")
    public ResponseEntity<String> version(@RequestHeader(value = TOKEN, required = false) String token) {
        return ResponseEntity.ok().contentType(TEXT).body(print.agentVersion(token));
    }

    @GetMapping("/api/print-agent/script")
    public ResponseEntity<byte[]> script(@RequestHeader(value = TOKEN, required = false) String token) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(print.agentScript(token));
    }

    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
