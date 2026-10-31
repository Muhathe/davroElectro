package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.PosRequests;
import univer.university.dto.response.PosResponses.ShiftDto;
import univer.university.service.ShiftService;

import java.time.LocalDate;
import java.util.List;

/** Kassa smenalari: ochish, yopish, hisobot. */
@RestController
@RequestMapping("/api/pos/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService service;

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('SMENA')")
    public ShiftDto open(@Valid @RequestBody PosRequests.OpenShift req) {
        return service.openShift(req);
    }

    @PostMapping("/close")
    @PreAuthorize("@perm.create('SMENA')")
    public ShiftDto close(@Valid @RequestBody PosRequests.CloseShift req) {
        return service.closeShift(req);
    }

    /** Joriy foydalanuvchining ochiq smenasi. */
    @GetMapping("/current")
    @PreAuthorize("@perm.view('SMENA')")
    public ShiftDto current() {
        return service.currentShift();
    }

    /** Smena tarixi. */
    @GetMapping
    @PreAuthorize("@perm.view('SMENA')")
    public List<ShiftDto> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.history(from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.view('SMENA')")
    public ShiftDto get(@PathVariable Long id) {
        return service.get(id);
    }
}
