package univer.university.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.dto.request.RoleRequests.PermissionItem;
import univer.university.dto.response.RoleResponses.PermissionRow;
import univer.university.entity.RolePermission;
import univer.university.entity.enums.Module;
import univer.university.entity.enums.Role;
import univer.university.repository.RolePermissionRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PermissionMatrixService {

    public enum Action { VIEW, CREATE, EDIT, DELETE }

    private final RolePermissionRepository repository;
    private final Map<String, RolePermission> cache = new ConcurrentHashMap<>();

    private static String key(Role r, Module m) {
        return r.name() + ":" + m.name();
    }

    @Transactional(readOnly = true)
    public boolean allowed(Role role, Module module, Action action) {
        if (role == Role.EGASI) {
            return true;
        }
        RolePermission p = cache.get(key(role, module));
        if (p == null) {
            p = repository.findByRoleAndModule(role, module).orElse(null);
            if (p == null) {
                return false;
            }
            cache.put(key(role, module), p);
        }
        return switch (action) {
            case VIEW -> p.isCanView();
            case CREATE -> p.isCanCreate();
            case EDIT -> p.isCanEdit();
            case DELETE -> p.isCanDelete();
        };
    }

    @Transactional(readOnly = true)
    public List<PermissionRow> matrix() {
        List<PermissionRow> rows = new ArrayList<>();
        for (RolePermission p : repository.findAll()) {
            rows.add(new PermissionRow(p.getRole(), p.getModule(), p.isCanView(), p.isCanCreate(), p.isCanEdit(), p.isCanDelete()));
        }
        rows.sort(Comparator.comparing(PermissionRow::role).thenComparing(PermissionRow::module));
        return rows;
    }

    @Transactional
    public List<PermissionRow> update(List<PermissionItem> items) {
        for (PermissionItem i : items) {
            if (i.role() == Role.EGASI) {
                continue; // EGASI ruxsatlari o'zgarmaydi
            }
            RolePermission p = repository.findByRoleAndModule(i.role(), i.module())
                    .orElseGet(() -> RolePermission.builder().role(i.role()).module(i.module()).build());
            p.setCanView(i.canView());
            p.setCanCreate(i.canCreate());
            p.setCanEdit(i.canEdit());
            p.setCanDelete(i.canDelete());
            repository.save(p);
        }
        cache.clear();
        return matrix();
    }

    public void clearCache() {
        cache.clear();
    }
}
