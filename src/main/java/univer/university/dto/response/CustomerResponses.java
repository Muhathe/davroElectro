package univer.university.dto.response;

import univer.university.entity.enums.CustomerType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CustomerResponses {
    private CustomerResponses() {
    }

    public record CustomerDto(Long id, String name, CustomerType type, String phone, BigDecimal discountPercent,
                              boolean active, LocalDateTime createdAt, long purchases, BigDecimal totalSpent,
                              BigDecimal grossProfit, BigDecimal debt, LocalDateTime lastPurchase, String state) {
    }
}
