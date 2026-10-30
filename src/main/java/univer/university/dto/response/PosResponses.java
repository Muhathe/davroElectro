package univer.university.dto.response;

import univer.university.entity.enums.PaymentType;
import univer.university.entity.enums.SaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PosResponses {
    private PosResponses() {
    }

    public record SaleLine(Long productId, String name, BigDecimal qty, BigDecimal price, BigDecimal lineTotal) {
    }

    public record SaleDto(Long id, String checkNo, LocalDateTime createdAt, Long userId, String cashierName,
                          Long customerId, String customerName, PaymentType paymentType, BigDecimal discountPercent,
                          BigDecimal total, SaleStatus status, List<SaleLine> items) {
    }

    public record CashBalance(BigDecimal total, BigDecimal naqd, BigDecimal karta) {
    }

    /** Sahifalangan sotuvlar tarixi; count va total butun filtr bo'yicha. */
    public record SaleHistory(List<SaleDto> items, long count, BigDecimal total, int page, int size) {
    }

    public record DayCashSummary(java.time.LocalDate date, long checks, BigDecimal total, List<SaleDto> sales) {
    }
}
