package univer.university.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DebtResponses {
    private DebtResponses() {
    }

    public record CustomerDebt(Long customerId, String customerName, BigDecimal credit, BigDecimal returnedToDebt,
                               BigDecimal paid, BigDecimal debt) {
    }

    public record CustomerPaymentDto(Long id, Long customerId, BigDecimal amount, String method,
                                     LocalDateTime createdAt, BigDecimal debtAfter) {
    }

    public record SupplierDebtDto(Long id, Long purchaseId, Long supplierId, String supplierName,
                                  BigDecimal amount, BigDecimal remaining, LocalDate dueDate,
                                  boolean closed, boolean overdue) {
    }
}
