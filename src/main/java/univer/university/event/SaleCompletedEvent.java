package univer.university.event;

import univer.university.entity.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Sotuv yakunlandi (checkout tranzaksiyasi ichida chiqariladi, jurnal BEFORE_COMMIT da yoziladi). parts — to'lov qismlari. */
public record SaleCompletedEvent(Long saleId, String checkNo, BigDecimal total, BigDecimal cogs,
                                 Map<PaymentType, BigDecimal> parts, LocalDate date) {
}
