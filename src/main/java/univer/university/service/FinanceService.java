package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.dto.request.FinanceRequests;
import univer.university.dto.response.FinanceResponses.*;
import univer.university.entity.FinancePlan;
import univer.university.entity.RecurringPayment;
import univer.university.entity.enums.ExpenseType;
import univer.university.entity.enums.Tone;
import univer.university.exception.NotFoundException;
import univer.university.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinanceService {

    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final SaleReturnRepository returns;
    private final SaleReturnItemRepository returnItems;
    private final ExpenseRepository expenses;
    private final FinancePlanRepository plans;
    private final RecurringPaymentRepository recurring;
    private final ExpenseService expenseService;
    private final DebtService debts;
    private final CashService cash;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Summary summary(String month) {
        YearMonth ym = DateUtils.parseMonth(month);
        LocalDateTime from = DateUtils.monthStart(ym);
        LocalDateTime to = DateUtils.monthEnd(ym);
        BigDecimal sold = MoneyUtils.nz(sales.sumTotal(from, to));
        BigDecimal ret = MoneyUtils.nz(returns.sumTotal(from, to));
        BigDecimal revenue = sold.subtract(ret);
        BigDecimal cogs = MoneyUtils.nz(saleItems.sumCost(from, to)).subtract(MoneyUtils.nz(returnItems.sumCost(from, to)));
        BigDecimal gross = revenue.subtract(cogs);
        BigDecimal draw = MoneyUtils.nz(expenses.sumActiveByType(ExpenseType.OWNER_DRAW, from, to));
        BigDecimal opex = MoneyUtils.nz(expenses.sumActive(from, to)).subtract(draw);
        long count = sales.countInRange(from, to);
        return new Summary(ym.toString(), MoneyUtils.round(sold), MoneyUtils.round(ret), MoneyUtils.round(revenue),
                MoneyUtils.round(cogs), MoneyUtils.round(gross), MoneyUtils.round(opex), MoneyUtils.round(gross.subtract(opex)),
                MoneyUtils.round(draw), cash.balance(), debts.totalCustomerDebt(), debts.totalSupplierDebt(), count,
                count == 0 ? MoneyUtils.zero() : MoneyUtils.round(sold.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)),
                expenseService.list(ym.toString(), null, null, null).byCategory());
    }

    // ---------- reja ----------

    @Transactional(readOnly = true)
    public PlanDto plan(String month) {
        String m = DateUtils.parseMonth(month).toString();
        return plans.findByMonth(m).map(FinanceService::toDto)
                .orElse(new PlanDto(m, MoneyUtils.zero(), MoneyUtils.zero(), MoneyUtils.zero(), MoneyUtils.zero(), MoneyUtils.zero()));
    }

    @Transactional
    public PlanDto savePlan(FinanceRequests.Plan req) {
        String m = DateUtils.parseMonth(req.month()).toString();
        FinancePlan p = plans.findByMonth(m).orElseGet(() -> FinancePlan.builder().month(m).build());
        p.setRevenue(MoneyUtils.round(req.revenue()));
        p.setGrossProfit(MoneyUtils.round(req.grossProfit()));
        p.setOpex(MoneyUtils.round(req.opex()));
        p.setNetProfit(MoneyUtils.round(req.netProfit()));
        p.setOwnerDraw(MoneyUtils.round(req.ownerDraw()));
        plans.save(p);
        audit.log("UPDATE", "FinancePlan", p.getId(), m + ": tushum=" + p.getRevenue());
        return toDto(p);
    }

    /** Reja/fakt: oy oxiriga prognoz = fakt / (kun - 0.5) * oydagi kunlar. */
    @Transactional(readOnly = true)
    public PlanFact planFact(String month) {
        YearMonth ym = DateUtils.parseMonth(month);
        PlanDto plan = plan(ym.toString());
        Summary s = summary(ym.toString());
        LocalDate today = DateUtils.today();
        YearMonth cur = YearMonth.from(today);
        int days = ym.lengthOfMonth();
        int day = ym.equals(cur) ? today.getDayOfMonth() : (ym.isBefore(cur) ? days : 0);
        List<PlanFactRow> rows = List.of(
                row("Tushum", plan.revenue(), s.revenue(), day, days, true),
                row("Yalpi foyda", plan.grossProfit(), s.grossProfit(), day, days, true),
                row("Operatsion harajat", plan.opex(), s.opex(), day, days, false),
                row("Sof foyda", plan.netProfit(), s.netProfit(), day, days, true),
                row("Egasi olgan pul", plan.ownerDraw(), s.ownerDraw(), day, days, false));
        return new PlanFact(plan, day, days, rows);
    }

    private PlanFactRow row(String metric, BigDecimal plan, BigDecimal fact, int day, int days, boolean higherBetter) {
        BigDecimal forecast;
        if (day == 0) {
            forecast = MoneyUtils.zero();
        } else if (day >= days) {
            forecast = fact;
        } else {
            forecast = MoneyUtils.round(fact.multiply(BigDecimal.valueOf(days))
                    .divide(BigDecimal.valueOf(day).subtract(new BigDecimal("0.5")), 2, RoundingMode.HALF_UP));
        }
        if (plan.signum() == 0) {
            return new PlanFactRow(metric, plan, fact, forecast, BigDecimal.ZERO, Tone.WARN);
        }
        BigDecimal ratio = forecast.divide(plan, 4, RoundingMode.HALF_UP);
        Tone tone;
        if (higherBetter) {
            tone = ratio.compareTo(new BigDecimal("0.98")) >= 0 ? Tone.GOOD : ratio.compareTo(new BigDecimal("0.85")) >= 0 ? Tone.WARN : Tone.BAD;
        } else {
            tone = ratio.compareTo(new BigDecimal("1.02")) <= 0 ? Tone.GOOD : ratio.compareTo(new BigDecimal("1.15")) <= 0 ? Tone.WARN : Tone.BAD;
        }
        return new PlanFactRow(metric, plan, fact, forecast, ratio, tone);
    }

    // ---------- kunlik tushum ----------

    @Transactional(readOnly = true)
    public List<DailyPoint> daily(LocalDate from, LocalDate to) {
        LocalDate t = to == null ? DateUtils.today() : to;
        LocalDate f = from == null ? t.minusDays(29) : from;
        LocalDateTime a = DateUtils.startOfDay(f);
        LocalDateTime b = DateUtils.startOfDay(t.plusDays(1));
        Map<LocalDate, BigDecimal> rev = new TreeMap<>();
        Map<LocalDate, BigDecimal> ret = new HashMap<>();
        Map<LocalDate, long[]> cnt = new HashMap<>();
        for (LocalDate d = f; !d.isAfter(t); d = d.plusDays(1)) {
            rev.put(d, BigDecimal.ZERO);
        }
        for (Object[] r : sales.rowsBetween(a, b)) {
            LocalDate d = ((LocalDateTime) r[0]).toLocalDate();
            rev.merge(d, MoneyUtils.bd(r[1]), BigDecimal::add);
            cnt.computeIfAbsent(d, k -> new long[1])[0]++;
        }
        for (Object[] r : returns.rowsBetween(a, b)) {
            ret.merge(((LocalDateTime) r[0]).toLocalDate(), MoneyUtils.bd(r[1]), BigDecimal::add);
        }
        List<DailyPoint> res = new ArrayList<>();
        rev.forEach((d, v) -> res.add(new DailyPoint(d, MoneyUtils.round(v), MoneyUtils.round(ret.getOrDefault(d, BigDecimal.ZERO)),
                cnt.getOrDefault(d, new long[1])[0])));
        return res;
    }

    // ---------- rejadagi to'lovlar ----------

    @Transactional(readOnly = true)
    public List<RecurringDto> listRecurring() {
        return recurring.findAllByOrderByDayOfMonthAscIdAsc().stream().map(FinanceService::toDto).toList();
    }

    @Transactional
    public RecurringDto saveRecurring(Long id, FinanceRequests.Recurring req) {
        RecurringPayment r = id == null ? new RecurringPayment() : recurring.findById(id)
                .orElseThrow(() -> new NotFoundException("Rejadagi to'lov topilmadi"));
        r.setLabel(req.label().trim());
        r.setCategory(req.category());
        r.setDayOfMonth(req.dayOfMonth());
        r.setAmount(req.amount() == null ? null : MoneyUtils.round(req.amount()));
        r.setPercentOfPrevRevenue(req.percentOfPrevRevenue());
        r.setOwnerDraw(req.ownerDraw());
        r.setActive(req.active() == null || req.active());
        recurring.save(r);
        audit.log(id == null ? "CREATE" : "UPDATE", "RecurringPayment", r.getId(), r.getLabel());
        return toDto(r);
    }

    static PlanDto toDto(FinancePlan p) {
        return new PlanDto(p.getMonth(), p.getRevenue(), p.getGrossProfit(), p.getOpex(), p.getNetProfit(), p.getOwnerDraw());
    }

    static RecurringDto toDto(RecurringPayment r) {
        return new RecurringDto(r.getId(), r.getLabel(), r.getCategory(), r.getDayOfMonth(), r.getAmount(),
                r.getPercentOfPrevRevenue(), r.isOwnerDraw(), r.isActive());
    }
}
