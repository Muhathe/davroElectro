package univer.university.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import univer.university.entity.enums.Unit;

import java.math.BigDecimal;

public final class ProductRequests {
    private ProductRequests() {
    }

    public record Save(@NotBlank String name, @NotBlank String barcode, String category,
                       @NotNull Unit unit,
                       @NotNull @DecimalMin("0") BigDecimal price,
                       BigDecimal minQty, Long supplierId, Boolean active) {
    }
}
