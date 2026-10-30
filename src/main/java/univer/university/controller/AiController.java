package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.AiRequests;
import univer.university.dto.response.AiResponses.Answer;
import univer.university.service.AiService;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService service;

    @PostMapping("/chat")
    @PreAuthorize("@perm.create('AI')")
    public Answer chat(@Valid @RequestBody AiRequests.Chat req) {
        return service.chat(req.message(), req.history());
    }

    @GetMapping("/quick-insight")
    @PreAuthorize("@perm.view('AI')")
    public Answer quickInsight() {
        return service.quickInsight();
    }
}
