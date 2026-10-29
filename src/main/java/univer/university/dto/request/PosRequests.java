package univer.university.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import univer.university.entity.enums.PaymentType;

import java.math.BigDecimal;
import java.util.List;

public final class PosRequests {
    private PosRequests() {
    }

    public record Item(@NotNull Long productId, @NotNull @DecimalMin(value = "0.001", message = "Miqdor 0 dan katta bo'lishi kerak") BigDecimal qty) {
    }

    public record Checkout(Long customerId, @NotNull PaymentType paymentType, BigDecimal receivedAmount,
                           @NotEmpty @Valid List<Item> items) {
    }
}
