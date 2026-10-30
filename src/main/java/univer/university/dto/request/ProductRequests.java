package univer.university.dto.request;

import jakarta.validation.constraints.*;
import univer.university.entity.enums.Unit;

import java.math.BigDecimal;

public final class ProductRequests {
    private ProductRequests() {
    }

    /**
     * mxikCode/packageCode/vatPercent: null — o'zgarmaydi, bo'sh satr — tozalanadi.
     * initialQty/initialCost: faqat yangi tovarda (POST); initialQty majburiy, 0 dan katta bo'lsa initialCost ham.
     * Keyingi tovar faqat kirim orqali qo'shiladi, PUT bu maydonlarni e'tiborsiz qoldiradi.
     */
    public record Save(@NotBlank String name, @NotBlank String barcode, String category,
                       @NotNull Unit unit,
                       @NotNull @DecimalMin("0") BigDecimal price,
                       BigDecimal minQty, Long supplierId, Boolean active,
                       @Pattern(regexp = "^$|^\\d{17}$", message = "MXIK kodi 17 ta raqamdan iborat") String mxikCode,
                       @Size(max = 20) String packageCode,
                       @DecimalMin("0") @DecimalMax("100") BigDecimal vatPercent,
                       @DecimalMin("0") BigDecimal initialQty,
                       @DecimalMin("0") BigDecimal initialCost) {
    }
}
