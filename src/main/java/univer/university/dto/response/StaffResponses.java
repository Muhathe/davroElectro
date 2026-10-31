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

    /**
     * Sotuvchi/kassir uchun — narx va foyda ma'lumotlarsiz.
     * share — umumiy sotuvlar sonidagi ulushi (pul emas, son bo'yicha).
     */
    public record MyStat(Long userId, String fullName, Role role, long checks, long returnsCount, BigDecimal share) {
    }
}
