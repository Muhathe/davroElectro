package univer.university.component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pul hisobi: faqat BigDecimal, HALF_UP, 2 xona. */
public final class MoneyUtils {

    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private MoneyUtils() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    /** JPQL agregat natijasini (BigDecimal/Number/null) BigDecimal ga o'tkazadi. */
    public static BigDecimal bd(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(String.valueOf(v));
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static BigDecimal round(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** price * (100 - percent) / 100, 2 xonagacha yaxlitlangan. */
    public static BigDecimal applyDiscount(BigDecimal price, BigDecimal percent) {
        BigDecimal p = nz(percent);
        if (p.signum() == 0) {
            return round(price);
        }
        return round(price.multiply(HUNDRED.subtract(p)).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    /** a / b (b = 0 bo'lsa 0). */
    public static BigDecimal ratio(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return nz(a).divide(b, 4, RoundingMode.HALF_UP);
    }
}
