package univer.university.dto.response;

import univer.university.entity.enums.StockStatus;
import univer.university.entity.enums.Unit;

import java.math.BigDecimal;
import java.util.List;

public final class ProductResponses {
    private ProductResponses() {
    }

    public record ProductDto(Long id, String name, String barcode, String category, Unit unit, BigDecimal avgCost,
                             BigDecimal lastCost, BigDecimal price, BigDecimal qty, BigDecimal minQty,
                             Long supplierId, boolean active) {
    }

    public record SupplierDto(Long id, String name, String phone) {
    }

    public record RowError(int row, String message) {
    }

    public record ImportResult(int total, int imported, List<RowError> errors) {
    }

    public record StockRow(Long id, String name, String barcode, String category, Unit unit, BigDecimal qty,
                           BigDecimal minQty, BigDecimal avgCost, BigDecimal price, BigDecimal value,
                           BigDecimal sold30, BigDecimal dailySales, BigDecimal coverDays, StockStatus status,
                           BigDecimal frozenValue) {
    }

    public record StockList(List<StockRow> items, BigDecimal totalValue, BigDecimal frozenValue,
                            long outOfStock, long lowStock, long dead, long excess) {
    }

    public record OrderSuggestion(Long productId, String name, Long supplierId, String supplierName,
                                  BigDecimal qty, BigDecimal minQty, BigDecimal dailySales, BigDecimal suggestQty,
                                  BigDecimal estimatedCost) {
    }

    public record MovementDto(Long id, Long productId, String productName, String type, BigDecimal qty,
                              BigDecimal cost, Long refId, java.time.LocalDateTime createdAt) {
    }

    public record IncomingResult(Long purchaseId, BigDecimal total, BigDecimal paid, BigDecimal debt) {
    }
}
