package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.ExpenseRequests;
import univer.university.dto.response.ExpenseResponses.CategoryDto;
import univer.university.service.ExpenseService;

import java.util.List;

@RestController
@RequestMapping("/api/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseService service;

    @GetMapping
    @PreAuthorize("@perm.view('HARAJATLAR') or @perm.view('KASSA_HARAJAT')")
    public List<CategoryDto> list() {
        return service.listCategories();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('HARAJATLAR')")
    public CategoryDto create(@Valid @RequestBody ExpenseRequests.Category req) {
        return service.createCategory(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.edit('HARAJATLAR')")
    public CategoryDto update(@PathVariable Long id, @Valid @RequestBody ExpenseRequests.Category req) {
        return service.updateCategory(id, req);
    }
}
