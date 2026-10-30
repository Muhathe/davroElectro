package univer.university.dto.response;

import java.time.LocalDateTime;

public final class AuditResponses {
    private AuditResponses() {
    }

    public record AuditLogDto(Long id, Long userId, String userName, String action, String entity, Long entityId,
                              String details, LocalDateTime createdAt) {
    }
}
