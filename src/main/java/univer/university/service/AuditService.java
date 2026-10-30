package univer.university.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.CurrentUserProvider;
import univer.university.component.DateUtils;
import univer.university.dto.response.AuditResponses.AuditLogDto;
import univer.university.entity.AuditLog;
import univer.university.entity.User;
import univer.university.repository.AuditLogRepository;
import univer.university.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUserProvider currentUser;
    private final UserRepository users;

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

    /** Kim, qachon, nimani o'zgartirgan: davr, xodim va obyekt turi bo'yicha filtr. */
    @Transactional(readOnly = true)
    public List<AuditLogDto> list(LocalDate from, LocalDate to, Long userId, String entity, int page, int size) {
        LocalDate f = from == null ? DateUtils.today().minusDays(30) : from;
        LocalDate t = to == null ? DateUtils.today() : to;
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), DateUtils.startOfDay(f)));
            p.add(cb.lessThan(root.get("createdAt"), DateUtils.startOfDay(t.plusDays(1))));
            if (userId != null) {
                p.add(cb.equal(root.get("userId"), userId));
            }
            if (entity != null && !entity.isBlank()) {
                p.add(cb.equal(root.get("entity"), entity.trim()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        List<AuditLog> logs = repository.findAll(spec, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 500)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"))).getContent();
        Map<Long, String> names = users.findAllById(logs.stream().map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return logs.stream().map(l -> new AuditLogDto(l.getId(), l.getUserId(), names.get(l.getUserId()), l.getAction(),
                l.getEntity(), l.getEntityId(), l.getDetails(), l.getCreatedAt())).toList();
    }
}
