package univer.university.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.PrintRequests;
import univer.university.dto.response.PrintResponses.AgentJob;
import univer.university.dto.response.PrintResponses.PrintJobDto;
import univer.university.dto.response.PrintResponses.PrintStatus;
import univer.university.entity.enums.PrintJobType;
import univer.university.service.PrintService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PrintController {

    private static final String TOKEN = "X-Agent-Token";

    private final PrintService print;

    @GetMapping("/api/print/status")
    @PreAuthorize("isAuthenticated()")
    public PrintStatus status() {
        return print.status();
    }

    @GetMapping("/api/print/jobs")
    @PreAuthorize("@perm.view('KASSA')")
    public List<PrintJobDto> jobs() {
        return print.recent();
    }

    @PostMapping("/api/print/test")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EGASI', 'ADMIN')")
    public PrintJobDto test() {
        return print.enqueue(PrintJobType.TEST, null);
    }

    /** Chekni qayta chop etish. */
    @PostMapping("/api/pos/sales/{id}/print")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.view('KASSA')")
    public PrintJobDto printSale(@PathVariable Long id) {
        return print.enqueue(PrintJobType.SALE, id);
    }

    @PostMapping("/api/returns/{id}/print")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.view('VOZVRAT')")
    public PrintJobDto printReturn(@PathVariable Long id) {
        return print.enqueue(PrintJobType.RETURN, id);
    }

    // ---------- print-agent (JWT emas, X-Agent-Token bilan) ----------

    /** 200 — vazifa, 204 — wait soniya ichida vazifa bo'lmadi. */
    @GetMapping("/api/print-agent/jobs/next")
    public ResponseEntity<AgentJob> next(@RequestHeader(value = TOKEN, required = false) String token,
                                         @RequestHeader(value = "X-Agent-Printer", required = false) String printer,
                                         @RequestParam(defaultValue = "20") int wait) {
        return print.next(token, printer, wait).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
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
}
