package univer.university.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import univer.university.component.CurrentUserProvider;
import univer.university.dto.request.RoleRequests.PermissionItem;
import univer.university.dto.request.RoleRequests.UserPermissionItem;
import univer.university.dto.response.RoleResponses.PermissionRow;
import univer.university.dto.response.RoleResponses.UserPermissionRow;
import univer.university.entity.RolePermission;
import univer.university.entity.User;
import univer.university.entity.UserPermission;
import univer.university.entity.enums.Module;
import univer.university.entity.enums.Role;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.RolePermissionRepository;
import univer.university.repository.UserPermissionRepository;
import univer.university.repository.UserRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionMatrixService {

    public enum Action { VIEW, CREATE, EDIT, DELETE }

    private final RolePermissionRepository repository;
    private final UserPermissionRepository userPermissions;
    private final UserRepository users;
    private final CurrentUserProvider currentUser;
    private final Map<String, RolePermission> cache = new ConcurrentHashMap<>();
    private final Map<String, Optional<UserPermission>> userCache = new ConcurrentHashMap<>();

    private static String key(Role r, Module m) {
        return r.name() + ":" + m.name();
    }

    /** Xodim tekshiruvi: shaxsiy ruxsat bo'lsa u, bo'lmasa rol ruxsati. */
    @Transactional(readOnly = true)
    public boolean allowed(Long userId, Role role, Module module, Action action) {
        if (role == Role.EGASI) {
            return true;
        }
        Optional<UserPermission> own = userCache.computeIfAbsent(userId + ":" + module.name(),
                k -> userPermissions.findByUserIdAndModule(userId, module));
        if (own.isEmpty()) {
            return allowed(role, module, action);
        }
        UserPermission p = own.get();
        return switch (action) {
            case VIEW -> p.isCanView();
            case CREATE -> p.isCanCreate();
            case EDIT -> p.isCanEdit();
            case DELETE -> p.isCanDelete();
        };
    }

    /** Xodimning har bir moduldagi amaldagi ruxsati. */
    @Transactional(readOnly = true)
    public List<PermissionRow> forUser(Long userId, Role role) {
        return userMatrix(userId, role).stream().map(p -> new PermissionRow(role, p.module(), p.canView(), p.canCreate(),
                p.canEdit(), p.canDelete())).toList();
    }

    @Transactional(readOnly = true)
    public List<UserPermissionRow> userMatrix(Long userId) {
        return userMatrix(userId, findUser(userId).getRole());
    }

    private List<UserPermissionRow> userMatrix(Long userId, Role role) {
        Map<Module, UserPermission> own = userPermissions.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserPermission::getModule, p -> p));
        List<UserPermissionRow> rows = new ArrayList<>();
        for (Module m : Module.values()) {
            UserPermission p = role == Role.EGASI ? null : own.get(m);
            if (p != null) {
                rows.add(new UserPermissionRow(m, p.isCanView(), p.isCanCreate(), p.isCanEdit(), p.isCanDelete(), true));
            } else {
                rows.add(new UserPermissionRow(m, allowed(role, m, Action.VIEW), allowed(role, m, Action.CREATE),
                        allowed(role, m, Action.EDIT), allowed(role, m, Action.DELETE), false));
            }
        }
        return rows;
    }

    /** Berilgan modullar uchun xodimga shaxsiy ruxsat o'rnatadi (boshqa modullari o'zgarmaydi). */
    @Transactional
    public List<UserPermissionRow> updateUser(Long userId, List<UserPermissionItem> items) {
        User target = guardUserEdit(userId);
        for (UserPermissionItem i : items) {
            UserPermission p = userPermissions.findByUserIdAndModule(userId, i.module())
                    .orElseGet(() -> UserPermission.builder().userId(userId).module(i.module()).build());
            // ko'rishsiz amal ma'nosiz
            p.setCanView(i.canView() || i.canCreate() || i.canEdit() || i.canDelete());
            p.setCanCreate(i.canCreate());
            p.setCanEdit(i.canEdit());
            p.setCanDelete(i.canDelete());
            userPermissions.save(p);
        }
        clearCacheAfterCommit();
        userPermissions.flush();
        return userMatrix(userId, target.getRole());
    }

    /** Shaxsiy ruxsatlarni o'chiradi: xodim yana rol ruxsati bilan ishlaydi. */
    @Transactional
    public List<UserPermissionRow> resetUser(Long userId) {
        User target = guardUserEdit(userId);
        userPermissions.deleteByUserId(userId);
        userPermissions.flush();
        clearCacheAfterCommit();
        return userMatrix(userId, target.getRole());
    }

    private User guardUserEdit(Long userId) {
        var editor = currentUser.current();
        if (editor.role() != Role.EGASI && editor.role() != Role.ADMIN) {
            throw new BusinessException("Xodim ruxsatlarini faqat EGASI va ADMIN o'zgartira oladi", HttpStatus.FORBIDDEN);
        }
        User target = findUser(userId);
        if (target.getRole() == Role.EGASI) {
            throw new BusinessException("EGASI ruxsatlari o'zgarmaydi", HttpStatus.FORBIDDEN);
        }
        if (editor.role() == Role.ADMIN && target.getRole() == Role.ADMIN) {
            throw new BusinessException("ADMIN o'zi va boshqa ADMIN ruxsatlarini o'zgartira olmaydi", HttpStatus.FORBIDDEN);
        }
        return target;
    }

    private User findUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi"));
    }

    /** Kesh commit dan keyin tozalanadi, aks holda parallel so'rov eski qiymatni qayta keshlashi mumkin. */
    private void clearCacheAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                clearCache();
            }
        });
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

    /** Matritsani faqat EGASI va ADMIN o'zgartiradi; ADMIN o'z rolining ruxsatlarini o'zgartira olmaydi. */
    @Transactional
    public List<PermissionRow> update(List<PermissionItem> items) {
        Role editor = currentUser.current().role();
        if (editor != Role.EGASI && editor != Role.ADMIN) {
            throw new BusinessException("Ruxsatlar matritsasini faqat EGASI va ADMIN o'zgartira oladi", HttpStatus.FORBIDDEN);
        }
        if (editor == Role.ADMIN && items.stream().anyMatch(i -> i.role() == Role.ADMIN)) {
            throw new BusinessException("ADMIN o'z rolining ruxsatlarini o'zgartira olmaydi", HttpStatus.FORBIDDEN);
        }
        for (PermissionItem i : items) {
            if (i.role() == Role.EGASI) {
                continue; // EGASI ruxsatlari o'zgarmaydi
            }
            RolePermission p = repository.findByRoleAndModule(i.role(), i.module())
                    .orElseGet(() -> RolePermission.builder().role(i.role()).module(i.module()).build());
            p.setCanView(i.canView() || i.canCreate() || i.canEdit() || i.canDelete());
            p.setCanCreate(i.canCreate());
            p.setCanEdit(i.canEdit());
            p.setCanDelete(i.canDelete());
            repository.save(p);
        }
        clearCacheAfterCommit();
        return matrix();
    }

    public void clearCache() {
        cache.clear();
        userCache.clear();
    }
}
