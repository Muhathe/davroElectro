package univer.university.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SettingsRequests {
    private SettingsRequests() {
    }

    public record Store(@NotBlank @Size(max = 120) String name, @Size(max = 250) String address,
                        @Size(max = 40) String phone,
                        @Pattern(regexp = "^$|^\\d{9}$|^\\d{14}$", message = "STIR 9 yoki JSHSHIR 14 ta raqam") String inn,
                        @Size(max = 500) String receiptFooter) {
    }
}
