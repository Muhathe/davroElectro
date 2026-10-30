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
                                String customerName, BigDecimal total, List<ReturnableLine> items, String cashierName) {
    }

    /** userId/userName — vozvratni rasmiylashtirgan xodim. */
    public record ReturnDto(Long id, Long saleId, String checkNo, ReturnReason reason, BigDecimal total,
                            boolean toDebt, LocalDateTime createdAt, List<ReturnableLine> items,
                            PosResponses.FiscalInfo fiscal, Long userId, String userName) {
    }

    public record SaleSearch(List<SaleForReturn> items, long count, int page, int size) {
    }

    /** count va total butun filtr bo'yicha. */
    public record ReturnSearch(List<ReturnDto> items, long count, BigDecimal total, int page, int size) {
    }

    public record SellerOption(Long id, String fullName) {
    }
}
