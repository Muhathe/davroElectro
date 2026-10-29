package univer.university.component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

public final class DateUtils {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");

    private DateUtils() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime startOfDay(LocalDate d) {
        return d.atStartOfDay();
    }

    /** Oyning birinchi kuni 00:00. */
    public static LocalDateTime monthStart(YearMonth ym) {
        return ym.atDay(1).atStartOfDay();
    }

    /** Keyingi oyning birinchi kuni 00:00 (yopiq emas, ya'ni &lt; bilan solishtiriladi). */
    public static LocalDateTime monthEnd(YearMonth ym) {
        return ym.plusMonths(1).atDay(1).atStartOfDay();
    }

    public static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(today());
        }
        return YearMonth.parse(month.trim());
    }
}
