package univer.university.dto.response;

import univer.university.entity.enums.FiscalStatus;
import univer.university.entity.enums.PaymentType;
import univer.university.entity.enums.SaleStatus;
import univer.university.entity.enums.ShiftStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PosResponses {
    private PosResponses() {
    }

    public record SaleLine(Long productId, String name, BigDecimal qty, BigDecimal price, BigDecimal lineTotal) {
    }

    public record FiscalInfo(FiscalStatus status, String terminalId, String receiptSeq, String fiscalSign, String qrUrl,
                             LocalDateTime fiscalizedAt, String error) {
    }

    public record PaymentLine(PaymentType method, BigDecimal amount) {
    }

    /** subtotal - discountAmount = total; receivedAmount/change faqat naqdda; creditAmount — nasiyaga o'tgan qism. */
    public record SaleDto(Long id, String checkNo, LocalDateTime createdAt, Long userId, String cashierName,
                          Long customerId, String customerName, PaymentType paymentType, BigDecimal discountPercent,
                          BigDecimal subtotal, BigDecimal discountAmount, BigDecimal total,
                          BigDecimal receivedAmount, BigDecimal change, SaleStatus status, FiscalInfo fiscal,
                          List<SaleLine> items, BigDecimal creditAmount, List<PaymentLine> payments) {
    }

    public record CashBalance(BigDecimal total, BigDecimal naqd, BigDecimal karta) {
    }

    /** Sahifalangan sotuvlar tarixi; count va total butun filtr bo'yicha. */
    public record SaleHistory(List<SaleDto> items, long count, BigDecimal total, int page, int size) {
    }

    public record DayCashSummary(LocalDate date, long checks, BigDecimal total, List<SaleDto> sales) {
    }

    /**
     * Kassa smenasi ma'lumotlari.
     * durationMinutes — smena davomiyligi (minutlarda); OPEN holatida hozirga qadar.
     * salesCount/salesTotal — smena davomidagi sotuvlar.
     * naqdIn/naqdOut — smena davomida naqd pul kirim/chiqim.
     * difference — closingCash - expectedCash (musbat=ortiqcha, manfiy=kamomad).
     */
    public record ShiftDto(Long id, Long userId, String cashierName, ShiftStatus status,
                           LocalDateTime openedAt, LocalDateTime closedAt, long durationMinutes,
                           BigDecimal openingCash, BigDecimal closingCash,
                           BigDecimal expectedCash, BigDecimal difference,
                           long salesCount, BigDecimal salesTotal,
                           BigDecimal naqdIn, BigDecimal naqdOut,
                           String openNote, String closeNote) {
    }
}
