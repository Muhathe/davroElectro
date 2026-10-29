package univer.university.event;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Sklad tuzatishi. valueDelta &lt; 0 — kamomad (yo'qotish), &gt; 0 — ortiqcha. */
public record StockAdjustedEvent(Long productId, BigDecimal valueDelta, LocalDate date) {
}
