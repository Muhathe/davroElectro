package univer.university.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {
    }

    public record Login(@NotBlank String username, @NotBlank String password) {
    }

    public record ChangePassword(@NotBlank String oldPassword,
                                 @NotBlank @Size(min = 6, max = 100) String newPassword) {
    }
}
