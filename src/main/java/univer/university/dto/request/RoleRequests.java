package univer.university.dto.request;

import jakarta.validation.constraints.NotNull;
import univer.university.entity.enums.Module;
import univer.university.entity.enums.Role;

public final class RoleRequests {
    private RoleRequests() {
    }

    public record PermissionItem(@NotNull Role role, @NotNull Module module,
                                 boolean canView, boolean canCreate, boolean canEdit, boolean canDelete) {
    }

    public record UserPermissionItem(@NotNull Module module,
                                     boolean canView, boolean canCreate, boolean canEdit, boolean canDelete) {
    }
}
