package univer.university.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.response.AccountingResponses.*;
import univer.university.service.AccountingService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accounting")
@RequiredArgsConstructor
public class AccountingController {

    private final AccountingService service;

    @GetMapping("/journal")
    @PreAuthorize("@perm.view('BUXGALTERIYA')")
    public List<EntryDto> journal(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) String account,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "100") int size) {
        return service.journal(from, to, account, page, size);
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("@perm.view('BUXGALTERIYA')")
    public TrialBalance trialBalance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.trialBalance(from, to);
    }

    @GetMapping("/profit-loss")
    @PreAuthorize("@perm.view('BUXGALTERIYA')")
    public ProfitLoss profitLoss(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.profitLoss(from, to);
    }

    @GetMapping("/accounts")
    @PreAuthorize("@perm.view('BUXGALTERIYA')")
    public List<AccountDto> accounts() {
        return service.accounts();
    }

    @GetMapping("/balance-sheet")
    @PreAuthorize("@perm.view('BUXGALTERIYA')")
    public BalanceSheet balanceSheet(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.balanceSheet(date);
    }

    @GetMapping("/cash-book")
    @PreAuthorize("@perm.view('BUXGALTERIYA')")
    public CashBook cashBook(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.cashBook(from, to);
    }
}
