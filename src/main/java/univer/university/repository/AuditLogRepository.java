package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univer.university.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
