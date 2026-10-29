package univer.university.dto.response;

import univer.university.entity.enums.Module;
import univer.university.entity.enums.Role;

public final class RoleResponses {
    private RoleResponses() {
    }

    public record PermissionRow(Role role, Module module, boolean canView, boolean canCreate, boolean canEdit, boolean canDelete) {
    }
}
