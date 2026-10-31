package univer.university.dto.response;

import univer.university.entity.enums.Tone;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FinanceResponses {
    private FinanceResponses() {
    }

    /**
     * Oylik moliyaviy xulosasi.
     * grossMarginPercent — yalpi foyda foizi (grossProfit / revenue * 100); revenue = 0 bo'lsa null.
     * monthlyCashIn — shu oy kassaga tushgan jami pul (barcha to'lov turlari).
     * monthlyCashOut — shu oy kassadan chiqgan jami pul.
     * cashBalance — hozirgi umumiy kassa qoldig'i (barcha vaqt).
     */
    public record Summary(String month, BigDecimal sales, BigDecimal returns, BigDecimal revenue, BigDecimal cogs,
                          BigDecimal grossProfit, BigDecimal grossMarginPercent,
                          BigDecimal opex, BigDecimal netProfit, BigDecimal ownerDraw,
                          BigDecimal cashBalance, BigDecimal monthlyCashIn, BigDecimal monthlyCashOut,
                          BigDecimal customerDebt, BigDecimal supplierDebt,
                          long salesCount, BigDecimal averageCheck, List<ExpenseResponses.CategoryTotal> expensesByCategory) {
    }

    public record PlanDto(String month, BigDecimal revenue, BigDecimal grossProfit, BigDecimal opex,
                          BigDecimal netProfit, BigDecimal ownerDraw) {
    }

    public record PlanFactRow(String metric, BigDecimal plan, BigDecimal fact, BigDecimal forecast,
                              BigDecimal ratio, Tone tone) {
    }

    public record PlanFact(PlanDto plan, int dayOfMonth, int daysInMonth, List<PlanFactRow> rows) {
    }

    public record DailyPoint(LocalDate date, BigDecimal revenue, BigDecimal returns, long checks) {
    }

    public record RecurringDto(Long id, String label, String category, int dayOfMonth, BigDecimal amount,
                               BigDecimal percentOfPrevRevenue, boolean ownerDraw, boolean active) {
    }

    public record ForecastDay(LocalDate date, BigDecimal inflow, BigDecimal outflow, BigDecimal balance,
                              List<String> events) {
    }

    public record Forecast(BigDecimal startBalance, List<ForecastDay> days, BigDecimal minBalance, LocalDate minDate,
                           LocalDate firstNegativeDate, boolean risk, String warning) {
    }
}
