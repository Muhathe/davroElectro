package univer.university.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PrintRequests {
    private PrintRequests() {
    }

    public record AgentFailure(String error) {
    }

    /** name bo'sh bo'lsa "Kassa N". */
    public record CreateTerminal(@Size(max = 60) String name) {
    }

    public record RenameTerminal(@NotBlank @Size(max = 60) String name) {
    }
}
