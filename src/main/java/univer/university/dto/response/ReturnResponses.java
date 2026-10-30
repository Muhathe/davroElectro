package univer.university.dto.response;

import univer.university.entity.enums.PaymentType;
import univer.university.entity.enums.ReturnReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ReturnResponses {
    private ReturnResponses() {
    }

    public record ReturnableLine(Long productId, String name, BigDecimal soldQty, BigDecimal returnedQty,
                                 BigDecimal availableQty, BigDecimal price) {
    }

    public record SaleForReturn(Long saleId, String checkNo, LocalDateTime createdAt, PaymentType paymentType,
                                String customerName, BigDecimal total, List<ReturnableLine> items) {
    }

    public record ReturnDto(Long id, Long saleId, String checkNo, ReturnReason reason, BigDecimal total,
                            boolean toDebt, LocalDateTime createdAt, List<ReturnableLine> items,
                            PosResponses.FiscalInfo fiscal) {
    }
}
