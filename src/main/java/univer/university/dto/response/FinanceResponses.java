package univer.university.dto.response;

import univer.university.entity.enums.Tone;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FinanceResponses {
    private FinanceResponses() {
    }

    public record Summary(String month, BigDecimal sales, BigDecimal returns, BigDecimal revenue, BigDecimal cogs,
                          BigDecimal grossProfit, BigDecimal opex, BigDecimal netProfit, BigDecimal ownerDraw,
                          BigDecimal cashBalance, BigDecimal customerDebt, BigDecimal supplierDebt,
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
