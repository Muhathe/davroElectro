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

    public record Payment(@NotNull PaymentType method,
                          @NotNull @DecimalMin(value = "0.01", message = "To'lov summasi 0 dan katta bo'lishi kerak") BigDecimal amount) {
    }

    /**
     * Bitta usul: paymentType. Bir nechta usul: payments (paymentType e'tiborsiz qoldiriladi);
     * NAQD/KARTA/CLICK dan ortgan qoldiq avtomatik mijozning nasiyasiga o'tadi. receivedAmount — naqd qismi uchun.
     */
    public record Checkout(Long customerId, PaymentType paymentType, BigDecimal receivedAmount,
                           @NotEmpty @Valid List<Item> items, @Valid List<Payment> payments) {
    }
}
