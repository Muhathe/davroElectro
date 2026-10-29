package univer.university.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import univer.university.entity.enums.Role;

public final class UserRequests {
    private UserRequests() {
    }

    public record Create(@NotBlank @Size(min = 3, max = 50) String username,
                         @NotBlank @Size(min = 6, max = 100) String password,
                         @NotBlank String fullName,
                         @NotNull Role role) {
    }

    public record Update(String fullName, Role role, Boolean active,
                         @Size(min = 6, max = 100) String password) {
    }
}
