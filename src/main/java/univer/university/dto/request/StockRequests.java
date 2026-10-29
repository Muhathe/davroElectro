package univer.university.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.util.List;

public final class StockRequests {
    private StockRequests() {
    }

    public record IncomingItem(@NotNull Long productId, @NotNull @DecimalMin("0.001") BigDecimal qty,
                               @NotNull @DecimalMin("0") BigDecimal cost) {
    }

    /** paid = to'langan qism; qolgani ta'minotchi qarziga yoziladi (14 kun). */
    public record Incoming(Long supplierId, @NotNull @DecimalMin("0") BigDecimal paid, PayMethod method,
                           @NotEmpty @Valid List<IncomingItem> items) {
    }

    public record Adjust(@NotNull Long productId, @NotNull @DecimalMin("0") BigDecimal newQty, @NotBlank String note) {
    }
}
