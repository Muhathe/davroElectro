package univer.university.dto.request;

import jakarta.validation.constraints.NotBlank;

public final class AuthRequests {
    private AuthRequests() {
    }

    public record Login(@NotBlank String username, @NotBlank String password) {
    }
}
