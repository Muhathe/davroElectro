package univer.university.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;

public final class DebtRequests {
    private DebtRequests() {
    }

    public record Payment(@NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull PayMethod method) {
    }
}
