package univer.university.event;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Boshlang'ich kassa va sklad qoldig'i (egasi kapitali hisobiga). */
public record OpeningBalanceEvent(BigDecimal cash, BigDecimal stock, LocalDate date) {
}
