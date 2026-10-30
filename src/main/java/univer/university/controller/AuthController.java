package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.AuthRequests;
import univer.university.dto.response.AuthResponses.LoginResponse;
import univer.university.dto.response.AuthResponses.MeResponse;
import univer.university.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody AuthRequests.Login req) {
        return service.login(req);
    }

    /** Har qanday tizimga kirgan foydalanuvchi uchun. */
    @GetMapping("/me")
    public MeResponse me() {
        return service.me();
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody AuthRequests.ChangePassword req) {
        service.changePassword(req);
    }
}
