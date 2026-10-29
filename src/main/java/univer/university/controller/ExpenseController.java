package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.ExpenseRequests;
import univer.university.dto.response.ExpenseResponses.ExpenseDto;
import univer.university.dto.response.ExpenseResponses.ExpenseList;
import univer.university.entity.enums.PayMethod;
import univer.university.service.ExpenseService;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService service;

    @GetMapping
    @PreAuthorize("@perm.view('HARAJATLAR')")
    public ExpenseList list(@RequestParam(required = false) String month,
                            @RequestParam(required = false) Long categoryId,
                            @RequestParam(required = false) PayMethod method,
                            @RequestParam(required = false) Long userId) {
        return service.list(month, categoryId, method, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('HARAJATLAR')")
    public ExpenseDto create(@Valid @RequestBody ExpenseRequests.Create req) {
        return service.create(req);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@perm.edit('HARAJATLAR')")
    public ExpenseDto cancel(@PathVariable Long id, @Valid @RequestBody ExpenseRequests.Cancel req) {
        return service.cancel(id, req);
    }
}
