package univer.university.dto.response;

import univer.university.entity.enums.Role;

import java.math.BigDecimal;

public final class StaffResponses {
    private StaffResponses() {
    }

    public record StaffStat(Long userId, String fullName, Role role, BigDecimal todayRevenue, BigDecimal monthRevenue,
                            long checks, BigDecimal averageCheck, BigDecimal grossProfit, long returnsCount,
                            BigDecimal returnsSum, BigDecimal share) {
    }
}
