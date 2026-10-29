package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.DebtRequests;
import univer.university.dto.response.DebtResponses.*;
import univer.university.service.DebtService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService service;

    @GetMapping("/customers/{id}/debt")
    @PreAuthorize("@perm.view('QARZLAR') or @perm.view('MIJOZLAR')")
    public CustomerDebt customerDebt(@PathVariable Long id) {
        return service.customerDebt(id);
    }

    @PostMapping("/customers/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('QARZLAR')")
    public CustomerPaymentDto accept(@PathVariable Long id, @Valid @RequestBody DebtRequests.Payment req) {
        return service.acceptPayment(id, req);
    }

    @GetMapping("/debts/customers")
    @PreAuthorize("@perm.view('QARZLAR')")
    public List<CustomerDebt> debtors() {
        return service.debtors();
    }

    @GetMapping("/debts/suppliers")
    @PreAuthorize("@perm.view('QARZLAR')")
    public List<SupplierDebtDto> supplierDebts(@RequestParam(defaultValue = "true") boolean onlyOpen) {
        return service.supplierDebts(onlyOpen);
    }

    @PostMapping("/debts/suppliers/{id}/pay")
    @PreAuthorize("@perm.edit('QARZLAR')")
    public SupplierDebtDto pay(@PathVariable Long id, @Valid @RequestBody DebtRequests.Payment req) {
        return service.paySupplier(id, req);
    }
}
