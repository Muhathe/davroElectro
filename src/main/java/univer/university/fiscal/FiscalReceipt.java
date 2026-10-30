package univer.university.fiscal;

import univer.university.entity.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Provayderga yuboriladigan chek. Vozvratda originalCheckNo va originalFiscalSign to'ldiriladi. */
public record FiscalReceipt(Type type, Long docId, String docNo, String originalCheckNo, String originalFiscalSign,
                            LocalDateTime createdAt, PaymentType paymentType, BigDecimal total,
                            BigDecimal receivedAmount, List<Line> lines) {

    public enum Type {
        SALE, RETURN
    }

    public record Line(Long productId, String name, String mxikCode, String packageCode, BigDecimal vatPercent,
                       BigDecimal qty, BigDecimal price, BigDecimal total) {
    }
}
