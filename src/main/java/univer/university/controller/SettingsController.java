package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.SettingsRequests;
import univer.university.dto.response.SettingsResponses.StoreSettingsDto;
import univer.university.service.SettingsService;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService service;

    /** Chek chop etish uchun — barcha rollarga. */
    @GetMapping("/store")
    @PreAuthorize("isAuthenticated()")
    public StoreSettingsDto store() {
        return service.store();
    }

    @PutMapping("/store")
    @PreAuthorize("hasRole('EGASI')")
    public StoreSettingsDto updateStore(@Valid @RequestBody SettingsRequests.Store req) {
        return service.updateStore(req);
    }
}
