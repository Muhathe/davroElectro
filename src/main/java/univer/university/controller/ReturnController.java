package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.ReturnRequests;
import univer.university.dto.response.ReturnResponses.ReturnDto;
import univer.university.dto.response.ReturnResponses.SaleForReturn;
import univer.university.service.ReturnService;

import java.util.List;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService service;

    @GetMapping("/sales/{checkNo}")
    @PreAuthorize("@perm.view('VOZVRAT')")
    public SaleForReturn findSale(@PathVariable String checkNo) {
        return service.findSale(checkNo);
    }

    @GetMapping
    @PreAuthorize("@perm.view('VOZVRAT')")
    public List<ReturnDto> recent() {
        return service.recent();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.view('VOZVRAT')")
    public ReturnDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('VOZVRAT')")
    public ReturnDto create(@Valid @RequestBody ReturnRequests.Create req) {
        return service.create(req);
    }
}
