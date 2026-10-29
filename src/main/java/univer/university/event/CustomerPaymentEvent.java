package univer.university.event;

import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerPaymentEvent(Long paymentId, BigDecimal amount, PayMethod method, LocalDate date) {
}
