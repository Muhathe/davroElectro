package univer.university.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import univer.university.entity.enums.ReturnReason;

import java.math.BigDecimal;
import java.util.List;

public final class ReturnRequests {
    private ReturnRequests() {
    }

    public record Item(@NotNull Long productId, @NotNull @DecimalMin("0.001") BigDecimal qty) {
    }

    public record Create(@NotBlank String checkNo, @NotNull ReturnReason reason,
                         @NotEmpty @Valid List<Item> items) {
    }
}
