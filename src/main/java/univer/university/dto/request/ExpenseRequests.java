package univer.university.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import univer.university.entity.enums.ExpenseType;
import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;

public final class ExpenseRequests {
    private ExpenseRequests() {
    }

    public record Create(@NotNull Long categoryId,
                         @NotNull @DecimalMin(value = "0.01", message = "Summa 0 dan katta bo'lishi kerak") BigDecimal amount,
                         @NotNull PayMethod paymentMethod,
                         String note) {
    }

    public record Cancel(@NotBlank(message = "Bekor qilish sababi majburiy") String reason) {
    }

    public record Category(@NotBlank String name, @NotNull ExpenseType type) {
    }
}
