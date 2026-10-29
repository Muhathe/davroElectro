package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.CustomerRequests;
import univer.university.dto.response.CustomerResponses.CustomerDto;
import univer.university.service.CustomerService;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    @GetMapping
    @PreAuthorize("@perm.view('MIJOZLAR') or @perm.view('KASSA')")
    public List<CustomerDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.view('MIJOZLAR') or @perm.view('KASSA')")
    public CustomerDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('MIJOZLAR')")
    public CustomerDto create(@Valid @RequestBody CustomerRequests.Save req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.edit('MIJOZLAR')")
    public CustomerDto update(@PathVariable Long id, @Valid @RequestBody CustomerRequests.Save req) {
        return service.update(id, req);
    }
}
