package univer.university.fiscal;

import java.time.LocalDateTime;

public record FiscalResult(String terminalId, String receiptSeq, String fiscalSign, String qrUrl,
                           LocalDateTime fiscalizedAt) {
}
