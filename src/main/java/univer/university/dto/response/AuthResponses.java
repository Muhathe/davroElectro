package univer.university.dto.response;

import univer.university.dto.response.RoleResponses.PermissionRow;
import univer.university.entity.enums.Role;

import java.time.LocalDateTime;
import java.util.List;

public final class AuthResponses {
    private AuthResponses() {
    }

    public record UserDto(Long id, String username, String fullName, Role role, boolean active, LocalDateTime createdAt) {
    }

    public record LoginResponse(String token, long expiresInMs, UserDto user, List<PermissionRow> permissions) {
    }

    /** Joriy foydalanuvchi va uning ruxsatlari (sahifa yangilanganda frontend uchun). */
    public record MeResponse(UserDto user, List<PermissionRow> permissions) {
    }
}
