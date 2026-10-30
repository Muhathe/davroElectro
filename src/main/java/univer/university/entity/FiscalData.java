package univer.university.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import univer.university.entity.enums.FiscalStatus;

import java.time.LocalDateTime;

/** Fiskal chek natijasi (sotuv va vozvratda). Fiskal o'chirilgan bo'lsa barcha ustunlar bo'sh. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class FiscalData {

    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_status", length = 10)
    private FiscalStatus status;

    @Column(name = "fiscal_terminal_id", length = 40)
    private String terminalId;

    @Column(name = "fiscal_receipt_seq", length = 40)
    private String receiptSeq;

    @Column(name = "fiscal_sign", length = 64)
    private String fiscalSign;

    @Column(name = "fiscal_qr_url", length = 500)
    private String qrUrl;

    @Column(name = "fiscalized_at")
    private LocalDateTime fiscalizedAt;

    @Column(name = "fiscal_error", length = 500)
    private String error;

    @Column(name = "fiscal_attempts")
    private Integer attempts;

    public static FiscalData pending() {
        FiscalData f = new FiscalData();
        f.setStatus(FiscalStatus.PENDING);
        f.setAttempts(0);
        return f;
    }
}
