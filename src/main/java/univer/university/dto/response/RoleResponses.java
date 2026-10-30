package univer.university.dto.response;

import univer.university.entity.enums.Module;
import univer.university.entity.enums.Role;

public final class RoleResponses {
    private RoleResponses() {
    }

    public record PermissionRow(Role role, Module module, boolean canView, boolean canCreate, boolean canEdit, boolean canDelete) {
    }

    /** Xodimning amaldagi ruxsati; custom = true — rol emas, shaxsiy ruxsat ishlayapti. */
    public record UserPermissionRow(Module module, boolean canView, boolean canCreate, boolean canEdit, boolean canDelete,
                                    boolean custom) {
    }

    /** Modul amallari ta'rifi; null — bu amal modulda mavjud emas, ekranda ko'rsatilmaydi. */
    public record Capability(Module module, String title, String view, String create, String edit, String delete) {
    }
}
