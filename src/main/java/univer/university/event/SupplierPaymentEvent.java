package univer.university.event;

import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierPaymentEvent(Long debtId, BigDecimal amount, PayMethod method, LocalDate date) {
}
