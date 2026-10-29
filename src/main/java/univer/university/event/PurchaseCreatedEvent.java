package univer.university.event;

import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseCreatedEvent(Long purchaseId, BigDecimal total, BigDecimal paid, PayMethod method, LocalDate date) {
}
