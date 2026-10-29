package univer.university.event;

import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseCancelledEvent(Long expenseId, BigDecimal amount, PayMethod method, boolean ownerDraw, LocalDate date) {
}
