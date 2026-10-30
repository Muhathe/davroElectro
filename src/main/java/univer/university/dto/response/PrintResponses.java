package univer.university.dto.response;

import univer.university.entity.enums.PrintJobStatus;
import univer.university.entity.enums.PrintJobType;

import java.time.LocalDateTime;

public final class PrintResponses {
    private PrintResponses() {
    }

    public record PrintJobDto(Long id, PrintJobType type, Long refId, Long terminalId, PrintJobStatus status,
                              LocalDateTime createdAt, LocalDateTime printedAt, int attempts, String error) {
    }

    /** Agentga: data — printerga o'zgarishsiz yuboriladigan ESC/POS baytlari (base64). */
    public record AgentJob(Long id, PrintJobType type, String data) {
    }

    /**
     * agentOnline: agent oxirgi 60 soniyada bog'langan; printerConnected: agent chek printerini topgan.
     * X-Kassa-Id bilan so'ralsa — shu kassa, aks holda istalgan kassa bo'yicha.
     */
    public record PrintStatus(Long terminalId, String terminalName, boolean agentOnline, boolean printerConnected,
                              LocalDateTime agentLastSeen, String printerName, long queued, boolean autoPrint) {
    }

    public record TerminalDto(Long id, String name, String hostName, boolean online, boolean printerConnected,
                              String printerName, LocalDateTime lastSeen, String agentVersion, LocalDateTime createdAt) {
    }

    /**
     * script — kassa kompyuterida ikki marta bosiladigan DavronPrinter.cmd matni (ichida shu kassaning tokeni).
     * Frontend uni fileName nomi bilan yuklab beradi va terminal.id ni shu brauzerda (X-Kassa-Id) eslab qoladi.
     */
    public record TerminalInstall(TerminalDto terminal, String fileName, String script) {
    }
}
