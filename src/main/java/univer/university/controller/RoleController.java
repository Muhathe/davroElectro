package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.RoleRequests.PermissionItem;
import univer.university.dto.request.RoleRequests.UserPermissionItem;
import univer.university.dto.response.RoleResponses.Capability;
import univer.university.dto.response.RoleResponses.PermissionRow;
import univer.university.dto.response.RoleResponses.UserPermissionRow;
import univer.university.security.PermissionCatalog;
import univer.university.security.PermissionMatrixService;
import univer.university.service.AuditService;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final PermissionMatrixService matrix;
    private final AuditService audit;

    @GetMapping("/matrix")
    @PreAuthorize("@perm.view('XODIMLAR')")
    public List<PermissionRow> get() {
        return matrix.matrix();
    }

    @PutMapping("/matrix")
    @PreAuthorize("@perm.edit('XODIMLAR')")
    public List<PermissionRow> update(@RequestBody List<@Valid PermissionItem> items) {
        List<PermissionRow> res = matrix.update(items);
        audit.log("UPDATE", "RolePermission", null, "matritsa o'zgartirildi: " + items.size() + " qator");
        return res;
    }

    /** Modullar va ulardagi haqiqiy amallar: ruxsatlar ekrani shu ro'yxatdan quriladi. */
    @GetMapping("/capabilities")
    @PreAuthorize("@perm.view('XODIMLAR')")
    public List<Capability> capabilities() {
        return PermissionCatalog.ALL;
    }

    @GetMapping("/users/{userId}/permissions")
    @PreAuthorize("@perm.view('XODIMLAR')")
    public List<UserPermissionRow> userPermissions(@PathVariable Long userId) {
        return matrix.userMatrix(userId);
    }

    /** Faqat yuborilgan modullar shaxsiy ruxsatga o'tadi; qolganlari rol ruxsatida qoladi. */
    @PutMapping("/users/{userId}/permissions")
    @PreAuthorize("@perm.edit('XODIMLAR')")
    public List<UserPermissionRow> updateUserPermissions(@PathVariable Long userId,
                                                         @RequestBody List<@Valid UserPermissionItem> items) {
        List<UserPermissionRow> res = matrix.updateUser(userId, items);
        audit.log("UPDATE", "UserPermission", userId, "shaxsiy ruxsat: " + items.size() + " modul");
        return res;
    }

    /** Barcha shaxsiy ruxsatlarni bekor qilib, xodimni rol ruxsatiga qaytaradi. */
    @DeleteMapping("/users/{userId}/permissions")
    @PreAuthorize("@perm.edit('XODIMLAR')")
    public List<UserPermissionRow> resetUserPermissions(@PathVariable Long userId) {
        List<UserPermissionRow> res = matrix.resetUser(userId);
        audit.log("UPDATE", "UserPermission", userId, "shaxsiy ruxsatlar bekor qilindi");
        return res;
    }
}
