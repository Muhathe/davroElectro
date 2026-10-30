package univer.university.dto.request;

import jakarta.validation.constraints.*;
import univer.university.entity.enums.Unit;

import java.math.BigDecimal;

public final class ProductRequests {
    private ProductRequests() {
    }

    /** mxikCode/packageCode/vatPercent: null — o'zgarmaydi, bo'sh satr — tozalanadi. */
    public record Save(@NotBlank String name, @NotBlank String barcode, String category,
                       @NotNull Unit unit,
                       @NotNull @DecimalMin("0") BigDecimal price,
                       BigDecimal minQty, Long supplierId, Boolean active,
                       @Pattern(regexp = "^$|^\\d{17}$", message = "MXIK kodi 17 ta raqamdan iborat") String mxikCode,
                       @Size(max = 20) String packageCode,
                       @DecimalMin("0") @DecimalMax("100") BigDecimal vatPercent) {
    }
}
