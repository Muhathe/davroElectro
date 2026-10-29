package univer.university.component;

import univer.university.entity.Product;
import univer.university.entity.enums.Unit;
import univer.university.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class QtyUtils {

    private QtyUtils() {
    }

    public static BigDecimal scale(BigDecimal q) {
        return MoneyUtils.nz(q).setScale(3, RoundingMode.HALF_UP);
    }

    /** DONA tovar faqat butun sonda; METR kasr bo'lishi mumkin. */
    public static BigDecimal normalize(Product p, BigDecimal qty) {
        BigDecimal q = scale(qty);
        if (q.signum() <= 0) {
            throw new BusinessException("Miqdor 0 dan katta bo'lishi kerak: " + p.getName());
        }
        if (p.getUnit() == Unit.DONA && q.stripTrailingZeros().scale() > 0) {
            throw new BusinessException("Dona birligidagi tovar butun sonda bo'lishi kerak: " + p.getName());
        }
        return q;
    }
}
