package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.RoleRequests.PermissionItem;
import univer.university.dto.response.RoleResponses.PermissionRow;
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
}
