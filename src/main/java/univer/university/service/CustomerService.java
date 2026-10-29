package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.dto.request.CustomerRequests;
import univer.university.dto.response.CustomerResponses.CustomerDto;
import univer.university.entity.Customer;
import univer.university.exception.NotFoundException;
import univer.university.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final LocalDateTime EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final CustomerRepository customers;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final SaleReturnRepository returns;
    private final SaleReturnItemRepository returnItems;
    private final DebtService debts;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<CustomerDto> list() {
        Stats st = stats();
        return customers.findAll(Sort.by("name")).stream().map(c -> toDto(c, st)).toList();
    }

    @Transactional(readOnly = true)
    public CustomerDto get(Long id) {
        return toDto(find(id), stats());
    }

    @Transactional
    public CustomerDto create(CustomerRequests.Save req) {
        Customer c = customers.save(Customer.builder().name(req.name().trim()).type(req.type())
                .phone(blank(req.phone())).discountPercent(MoneyUtils.round(req.discountPercent()))
                .active(true).createdAt(DateUtils.now()).build());
        audit.log("CREATE", "Customer", c.getId(), c.getName());
        return toDto(c, stats());
    }

    @Transactional
    public CustomerDto update(Long id, CustomerRequests.Save req) {
        Customer c = find(id);
        c.setName(req.name().trim());
        c.setType(req.type());
        c.setPhone(blank(req.phone()));
        c.setDiscountPercent(MoneyUtils.round(req.discountPercent()));
        if (req.active() != null) {
            c.setActive(req.active());
        }
        audit.log("UPDATE", "Customer", c.getId(), c.getName() + ", chegirma=" + c.getDiscountPercent());
        return toDto(c, stats());
    }

    private Customer find(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Mijoz topilmadi"));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private record Stats(Map<Long, long[]> count, Map<Long, BigDecimal> total, Map<Long, BigDecimal> gross,
                         Map<Long, BigDecimal> returned, Map<Long, BigDecimal> returnedGross,
                         Map<Long, LocalDateTime> last) {
    }

    private Stats stats() {
        Map<Long, long[]> count = new HashMap<>();
        Map<Long, BigDecimal> total = new HashMap<>();
        for (Object[] r : sales.groupByCustomer(EPOCH)) {
            count.put((Long) r[0], new long[]{(Long) r[1]});
            total.put((Long) r[0], MoneyUtils.bd(r[2]));
        }
        Map<Long, BigDecimal> gross = new HashMap<>();
        for (Object[] r : saleItems.grossByCustomer(EPOCH)) {
            gross.put((Long) r[0], MoneyUtils.bd(r[1]));
        }
        Map<Long, BigDecimal> returned = new HashMap<>();
        for (Object[] r : returns.sumByCustomer(EPOCH)) {
            returned.put((Long) r[0], MoneyUtils.bd(r[1]));
        }
        Map<Long, BigDecimal> returnedGross = new HashMap<>();
        for (Object[] r : returnItems.grossByCustomer(EPOCH)) {
            returnedGross.put((Long) r[0], MoneyUtils.bd(r[1]));
        }
        Map<Long, LocalDateTime> last = new HashMap<>();
        for (Object[] r : sales.lastPurchaseByCustomer()) {
            last.put((Long) r[0], (LocalDateTime) r[1]);
        }
        return new Stats(count, total, gross, returned, returnedGross, last);
    }

    private CustomerDto toDto(Customer c, Stats st) {
        long purchases = st.count().getOrDefault(c.getId(), new long[]{0})[0];
        BigDecimal spent = MoneyUtils.nz(st.total().get(c.getId())).subtract(MoneyUtils.nz(st.returned().get(c.getId())));
        BigDecimal gross = MoneyUtils.nz(st.gross().get(c.getId())).subtract(MoneyUtils.nz(st.returnedGross().get(c.getId())));
        LocalDateTime last = st.last().get(c.getId());
        return new CustomerDto(c.getId(), c.getName(), c.getType(), c.getPhone(), c.getDiscountPercent(), c.isActive(),
                c.getCreatedAt(), purchases, MoneyUtils.round(spent), MoneyUtils.round(gross), debts.debtOf(c.getId()),
                last, state(last));
    }

    /** Yangi (xarid yo'q), Faol (<=10 kun), Kamaygan (<=21), Sust (>21). */
    static String state(LocalDateTime last) {
        if (last == null) {
            return "Yangi";
        }
        long idle = ChronoUnit.DAYS.between(last.toLocalDate(), DateUtils.today());
        if (idle <= 10) {
            return "Faol";
        }
        return idle <= 21 ? "Kamaygan" : "Sust";
    }
}
