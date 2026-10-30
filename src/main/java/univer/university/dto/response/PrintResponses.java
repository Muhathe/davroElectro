package univer.university.dto.response;

import univer.university.entity.enums.PrintJobStatus;
import univer.university.entity.enums.PrintJobType;

import java.time.LocalDateTime;

public final class PrintResponses {
    private PrintResponses() {
    }

    public record PrintJobDto(Long id, PrintJobType type, Long refId, PrintJobStatus status, LocalDateTime createdAt,
                              LocalDateTime printedAt, int attempts, String error) {
    }

    /** Agentga: data — printerga o'zgarishsiz yuboriladigan ESC/POS baytlari (base64). */
    public record AgentJob(Long id, PrintJobType type, String data) {
    }

    /** agentOnline: agent oxirgi 60 soniyada navbatni so'ragan. */
    public record PrintStatus(boolean agentOnline, LocalDateTime agentLastSeen, String printerName, long queued,
                              boolean autoPrint) {
    }
}
