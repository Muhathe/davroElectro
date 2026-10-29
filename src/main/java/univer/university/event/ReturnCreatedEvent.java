package univer.university.event;

import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

/** method = null bo'lsa vozvrat nasiyaga (mijoz qarzidan) yozilgan. */
public record ReturnCreatedEvent(Long returnId, BigDecimal total, BigDecimal cogs, PayMethod method, LocalDate date) {
}
