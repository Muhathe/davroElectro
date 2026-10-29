package univer.university.dto.request;

import jakarta.validation.constraints.NotBlank;

public final class SupplierRequests {
    private SupplierRequests() {
    }

    public record Save(@NotBlank String name, String phone) {
    }
}
