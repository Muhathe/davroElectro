package univer.university.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AiRequests {
    private AiRequests() {
    }

    public record Turn(@NotBlank @Pattern(regexp = "user|assistant") String role, @NotBlank @Size(max = 4000) String content) {
    }

    /** history — suhbatning oldingi xabarlari (frontend saqlaydi), keyingi savol avvalgisini bilishi uchun. */
    public record Chat(@NotBlank @Size(max = 2000) String message, @Valid @Size(max = 10) List<Turn> history) {
    }
}
