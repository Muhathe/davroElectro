package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.dto.response.StaffResponses.StaffStat;
import univer.university.entity.User;
import univer.university.entity.enums.Role;
import univer.university.repository.SaleItemRepository;
import univer.university.repository.SaleRepository;
import univer.university.repository.SaleReturnRepository;
import univer.university.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final UserRepository users;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final SaleReturnRepository returns;

    @Transactional(readOnly = true)
    public List<StaffStat> stats(String month) {
        YearMonth ym = DateUtils.parseMonth(month);
        LocalDateTime from = DateUtils.monthStart(ym);
        LocalDateTime to = DateUtils.monthEnd(ym);
        LocalDate today = DateUtils.today();
        Map<Long, BigDecimal[]> mon = pairs(sales.groupByUser(from, to));
        Map<Long, BigDecimal[]> day = pairs(sales.groupByUser(DateUtils.startOfDay(today), DateUtils.startOfDay(today.plusDays(1))));
        Map<Long, BigDecimal[]> ret = pairs(returns.groupByUser(from, to));
        Map<Long, BigDecimal> gross = new HashMap<>();
        for (Object[] r : saleItems.grossByUser(from, to)) {
            gross.put((Long) r[0], MoneyUtils.bd(r[1]));
        }
        BigDecimal totalRev = mon.values().stream().map(v -> v[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<StaffStat> res = new ArrayList<>();
        for (User u : users.findAll()) {
            BigDecimal[] m = mon.get(u.getId());
            if (m == null && u.getRole() != Role.SOTUVCHI) {
                continue;
            }
            BigDecimal revenue = m == null ? BigDecimal.ZERO : m[1];
            long checks = m == null ? 0 : m[0].longValue();
            BigDecimal[] r = ret.getOrDefault(u.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] d = day.getOrDefault(u.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            res.add(new StaffStat(u.getId(), u.getFullName(), u.getRole(), MoneyUtils.round(d[1]), MoneyUtils.round(revenue), checks,
                    checks == 0 ? MoneyUtils.zero() : revenue.divide(BigDecimal.valueOf(checks), 2, RoundingMode.HALF_UP),
                    MoneyUtils.round(gross.getOrDefault(u.getId(), BigDecimal.ZERO)), r[0].longValue(), MoneyUtils.round(r[1]),
                    totalRev.signum() == 0 ? BigDecimal.ZERO : revenue.divide(totalRev, 4, RoundingMode.HALF_UP)));
        }
        res.sort(Comparator.comparing(StaffStat::monthRevenue).reversed());
        return res;
    }

    private static Map<Long, BigDecimal[]> pairs(List<Object[]> rows) {
        Map<Long, BigDecimal[]> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put((Long) r[0], new BigDecimal[]{BigDecimal.valueOf((Long) r[1]), MoneyUtils.bd(r[2])});
        }
        return m;
    }
}
