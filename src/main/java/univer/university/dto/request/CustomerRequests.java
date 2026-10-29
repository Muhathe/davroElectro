package univer.university.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import univer.university.entity.enums.CustomerType;

import java.math.BigDecimal;

public final class CustomerRequests {
    private CustomerRequests() {
    }

    public record Save(@NotBlank String name, @NotNull CustomerType type, String phone,
                       @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent, Boolean active) {
    }
}
