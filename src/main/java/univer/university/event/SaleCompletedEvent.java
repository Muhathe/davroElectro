package univer.university.event;

import univer.university.entity.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Sotuv yakunlandi (checkout tranzaksiyasi ichida chiqariladi, jurnal BEFORE_COMMIT da yoziladi). */
public record SaleCompletedEvent(Long saleId, String checkNo, BigDecimal total, BigDecimal cogs,
                                 PaymentType paymentType, LocalDate date) {
}
