package univer.university.dto.request;

import jakarta.validation.constraints.NotBlank;

public final class AiRequests {
    private AiRequests() {
    }

    public record Chat(@NotBlank String message) {
    }
}
