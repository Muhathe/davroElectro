package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.dto.response.FinanceResponses.Forecast;
import univer.university.dto.response.FinanceResponses.ForecastDay;
import univer.university.entity.RecurringPayment;
import univer.university.entity.SupplierDebt;
import univer.university.entity.enums.ExpenseType;
import univer.university.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** 30 kunlik kassa prognozi. */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final SaleReturnRepository returns;
    private final SaleReturnItemRepository returnItems;
    private final CustomerPaymentRepository payments;
    private final ExpenseRepository expenses;
    private final RecurringPaymentRepository recurring;
    private final SupplierDebtRepository supplierDebts;
    private final CashService cash;

    @Transactional(readOnly = true)
    public Forecast forecast() {
        LocalDate today = DateUtils.today();
        LocalDateTime todayStart = DateUtils.startOfDay(today);
        LocalDateTime d14 = todayStart.minusDays(14);
        LocalDateTime d30 = todayStart.minusDays(30);

        BigDecimal avgRev = MoneyUtils.nz(sales.sumNonCredit(d14, todayStart)).add(MoneyUtils.nz(payments.sumBetween(d14, todayStart)))
                .divide(BigDecimal.valueOf(14), 2, RoundingMode.HALF_UP);
        BigDecimal avgCogs = MoneyUtils.nz(saleItems.sumCost(d30, todayStart)).subtract(MoneyUtils.nz(returnItems.sumCost(d30, todayStart)))
                .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
        BigDecimal smallExp = MoneyUtils.nz(expenses.sumActiveByType(ExpenseType.VARIABLE, d30, todayStart))
                .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        BigDecimal avgRet = MoneyUtils.nz(returns.sumCashReturns(d30, todayStart)).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

        YearMonth prev = YearMonth.from(today).minusMonths(1);
        BigDecimal prevRevenue = MoneyUtils.nz(sales.sumTotal(DateUtils.monthStart(prev), DateUtils.monthEnd(prev)))
                .subtract(MoneyUtils.nz(returns.sumTotal(DateUtils.monthStart(prev), DateUtils.monthEnd(prev))));

        List<RecurringPayment> recs = recurring.findByActiveTrueOrderByDayOfMonthAscIdAsc();
        List<SupplierDebt> debts = supplierDebts.findByClosedFalseOrderByDueDateAsc();

        BigDecimal start = cash.balance();
        BigDecimal balance = start;
        BigDecimal min = null;
        LocalDate minDate = null;
        LocalDate firstNeg = null;
        List<ForecastDay> days = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            LocalDate d = today.plusDays(i);
            BigDecimal buyRate = i <= 14 ? new BigDecimal("0.5") : BigDecimal.ONE;
            BigDecimal out = avgCogs.multiply(buyRate).add(smallExp).add(avgRet);
            List<String> ev = new ArrayList<>();
            for (RecurringPayment r : recs) {
                int dom = Math.min(r.getDayOfMonth(), d.lengthOfMonth());
                if (d.getDayOfMonth() == dom) {
                    BigDecimal amt = r.getAmount() != null ? r.getAmount()
                            : prevRevenue.multiply(MoneyUtils.nz(r.getPercentOfPrevRevenue())).divide(MoneyUtils.HUNDRED, 2, RoundingMode.HALF_UP);
                    if (amt.signum() > 0) {
                        out = out.add(amt);
                        ev.add(r.getLabel() + ": " + MoneyUtils.round(amt).toPlainString());
                    }
                }
            }
            for (SupplierDebt sd : debts) {
                boolean due = sd.getDueDate().equals(d) || (i == 1 && sd.getDueDate().isBefore(d));
                if (due) {
                    out = out.add(sd.getRemaining());
                    ev.add("Ta'minotchi qarzi: " + sd.getRemaining().toPlainString());
                }
            }
            balance = balance.add(avgRev).subtract(out);
            BigDecimal b = MoneyUtils.round(balance);
            if (min == null || b.compareTo(min) < 0) {
                min = b;
                minDate = d;
            }
            if (firstNeg == null && b.signum() < 0) {
                firstNeg = d;
            }
            days.add(new ForecastDay(d, MoneyUtils.round(avgRev), MoneyUtils.round(out), b, ev));
        }
        boolean risk = firstNeg != null;
        String warning = risk ? "Kassa uzilishi xavfi: " + firstNeg + " kuni qoldiq manfiy bo'lishi mumkin" : null;
        return new Forecast(start, days, min, minDate, firstNeg, risk, warning);
    }
}
