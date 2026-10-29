package univer.university.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class FinanceRequests {
    private FinanceRequests() {
    }

    public record Plan(@NotBlank String month, @NotNull BigDecimal revenue, @NotNull BigDecimal grossProfit,
                       @NotNull BigDecimal opex, @NotNull BigDecimal netProfit, @NotNull BigDecimal ownerDraw) {
    }

    public record Recurring(@NotBlank String label, String category,
                            @Min(1) @Max(31) int dayOfMonth,
                            BigDecimal amount, BigDecimal percentOfPrevRevenue,
                            boolean ownerDraw, Boolean active) {
    }
}
