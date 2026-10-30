package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.ExpenseRequests;
import univer.university.dto.request.PosRequests;
import univer.university.dto.response.ExpenseResponses.ExpenseDto;
import univer.university.dto.response.PosResponses.CashBalance;
import univer.university.dto.response.PosResponses.DayCashSummary;
import univer.university.dto.response.PosResponses.SaleDto;
import univer.university.dto.response.PosResponses.SaleHistory;
import univer.university.entity.enums.PaymentType;
import univer.university.service.CashService;
import univer.university.service.ExpenseService;
import univer.university.service.PosService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosService pos;
    private final ExpenseService expenses;
    private final CashService cash;

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('KASSA')")
    public SaleDto checkout(@Valid @RequestBody PosRequests.Checkout req) {
        return pos.checkout(req);
    }

    /** Kassadan harajat kiritish (ExpenseService.create ni chaqiradi). */
    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('KASSA_HARAJAT')")
    public ExpenseDto expense(@Valid @RequestBody ExpenseRequests.Create req) {
        return expenses.create(req);
    }

    @GetMapping("/cash/balance")
    @PreAuthorize("@perm.view('KASSA')")
    public CashBalance balance() {
        return cash.balances();
    }

    @GetMapping("/sales")
    @PreAuthorize("@perm.view('KASSA')")
    public DayCashSummary sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return pos.salesOfDay(date);
    }

    @GetMapping("/sales/history")
    @PreAuthorize("@perm.view('KASSA')")
    public SaleHistory history(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) Long userId,
                               @RequestParam(required = false) Long customerId,
                               @RequestParam(required = false) PaymentType paymentType,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        return pos.history(from, to, userId, customerId, paymentType, page, size);
    }

    /** Chekni qayta chop etish uchun. */
    @GetMapping("/sales/{id}")
    @PreAuthorize("@perm.view('KASSA')")
    public SaleDto sale(@PathVariable Long id) {
        return pos.get(id);
    }

    @GetMapping("/sales/check/{checkNo}")
    @PreAuthorize("@perm.view('KASSA')")
    public SaleDto saleByCheck(@PathVariable String checkNo) {
        return pos.getByCheckNo(checkNo);
    }
}
