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
}
