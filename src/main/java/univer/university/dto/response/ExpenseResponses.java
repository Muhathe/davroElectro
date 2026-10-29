package univer.university.dto.response;

import univer.university.entity.enums.ExpenseStatus;
import univer.university.entity.enums.ExpenseType;
import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ExpenseResponses {
    private ExpenseResponses() {
    }

    public record CategoryDto(Long id, String name, ExpenseType type) {
    }

    public record ExpenseDto(Long id, Long categoryId, String categoryName, ExpenseType categoryType,
                             BigDecimal amount, PayMethod paymentMethod, String note, ExpenseStatus status,
                             String cancelReason, Long userId, String userName,
                             LocalDateTime createdAt, LocalDateTime cancelledAt) {
    }

    public record CategoryTotal(String category, BigDecimal amount) {
    }

    public record ExpenseList(List<ExpenseDto> items, BigDecimal total, List<CategoryTotal> byCategory) {
    }
}
