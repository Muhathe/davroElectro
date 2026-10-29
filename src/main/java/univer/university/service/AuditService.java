package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import univer.university.component.CurrentUserProvider;
import univer.university.component.DateUtils;
import univer.university.entity.AuditLog;
import univer.university.repository.AuditLogRepository;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUserProvider currentUser;

    public void log(String action, String entity, Long entityId, String details) {
        Long uid = null;
        try {
            uid = currentUser.id();
        } catch (RuntimeException ignored) {
            // tizim amali (seed) — foydalanuvchi yo'q
        }
        repository.save(AuditLog.builder().userId(uid).action(action).entity(entity).entityId(entityId)
                .details(details).createdAt(DateUtils.now()).build());
    }
}
