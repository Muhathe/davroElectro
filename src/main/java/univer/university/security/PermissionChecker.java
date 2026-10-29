package univer.university.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import univer.university.entity.enums.Module;
import univer.university.security.PermissionMatrixService.Action;

/** @PreAuthorize("@perm.view('KASSA')") ko'rinishida ishlatiladi. */
@Component("perm")
@RequiredArgsConstructor
public class PermissionChecker {

    private final PermissionMatrixService matrix;

    public boolean view(String module) {
        return check(module, Action.VIEW);
    }

    public boolean create(String module) {
        return check(module, Action.CREATE);
    }

    public boolean edit(String module) {
        return check(module, Action.EDIT);
    }

    public boolean delete(String module) {
        return check(module, Action.DELETE);
    }

    private boolean check(String module, Action action) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof UserPrincipal p)) {
            return false;
        }
        return matrix.allowed(p.role(), Module.valueOf(module), action);
    }
}
