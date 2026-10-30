package univer.university.fiscal;

import univer.university.entity.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Provayderga yuboriladigan chek. Vozvratda originalCheckNo va originalFiscalSign to'ldiriladi.
 * payments — to'lov qismlari (eski sotuvda va vozvratda bo'sh, paymentType ishlatiladi).
 */
public record FiscalReceipt(Type type, Long docId, String docNo, String originalCheckNo, String originalFiscalSign,
                            LocalDateTime createdAt, PaymentType paymentType, BigDecimal total,
                            BigDecimal receivedAmount, List<Line> lines, Map<PaymentType, BigDecimal> payments) {

    public enum Type {
        SALE, RETURN
    }

    public record Line(Long productId, String name, String mxikCode, String packageCode, BigDecimal vatPercent,
                       BigDecimal qty, BigDecimal price, BigDecimal total) {
    }
}
