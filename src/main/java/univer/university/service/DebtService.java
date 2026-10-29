package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.CurrentUserProvider;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.dto.request.DebtRequests;
import univer.university.dto.response.DebtResponses.*;
import univer.university.entity.Customer;
import univer.university.entity.CustomerPayment;
import univer.university.entity.Supplier;
import univer.university.entity.SupplierDebt;
import univer.university.entity.enums.CashDirection;
import univer.university.entity.enums.CashSource;
import univer.university.event.CustomerPaymentEvent;
import univer.university.event.SupplierPaymentEvent;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DebtService {

    private final CustomerRepository customers;
    private final SaleRepository sales;
    private final SaleReturnRepository returns;
    private final CustomerPaymentRepository payments;
    private final SupplierDebtRepository supplierDebts;
    private final SupplierRepository suppliers;
    private final CashService cash;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private final ApplicationEventPublisher events;

    /** Mijoz qarzi = nasiya sotuvlar − nasiyaga vozvrat − to'lovlar (FIFO: eng eski qarz avval yopiladi, jamlanma hisobda). */
    @Transactional(readOnly = true)
    public BigDecimal debtOf(Long customerId) {
        BigDecimal credit = sales.sumCreditByCustomer(customerId);
        BigDecimal ret = returns.sumToDebtByCustomer(customerId);
        BigDecimal paid = payments.sumByCustomer(customerId);
        return MoneyUtils.round(credit.subtract(ret).subtract(paid));
    }

    @Transactional(readOnly = true)
    public CustomerDebt customerDebt(Long customerId) {
        Customer c = customers.findById(customerId).orElseThrow(() -> new NotFoundException("Mijoz topilmadi"));
        BigDecimal credit = MoneyUtils.round(sales.sumCreditByCustomer(customerId));
        BigDecimal ret = MoneyUtils.round(returns.sumToDebtByCustomer(customerId));
        BigDecimal paid = MoneyUtils.round(payments.sumByCustomer(customerId));
        return new CustomerDebt(c.getId(), c.getName(), credit, ret, paid, MoneyUtils.round(credit.subtract(ret).subtract(paid)));
    }

    @Transactional(readOnly = true)
    public List<CustomerDebt> debtors() {
        return customers.findAll().stream().map(c -> customerDebt(c.getId()))
                .filter(d -> d.debt().signum() > 0)
                .sorted((a, b) -> b.debt().compareTo(a.debt())).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal totalCustomerDebt() {
        return debtors().stream().map(CustomerDebt::debt).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public CustomerPaymentDto acceptPayment(Long customerId, DebtRequests.Payment req) {
        customers.findById(customerId).orElseThrow(() -> new NotFoundException("Mijoz topilmadi"));
        BigDecimal amount = MoneyUtils.round(req.amount());
        BigDecimal debt = debtOf(customerId);
        if (debt.signum() <= 0) {
            throw new BusinessException("Mijozning qarzi yo'q");
        }
        if (amount.compareTo(debt) > 0) {
            throw new BusinessException("To'lov qarzdan oshib ketdi (qarz: " + debt.toPlainString() + ")");
        }
        CustomerPayment p = payments.save(CustomerPayment.builder().customerId(customerId).amount(amount)
                .method(req.method()).createdAt(DateUtils.now()).userId(currentUser.id()).build());
        cash.record(CashDirection.IN, amount, req.method(), CashSource.CUSTOMER_PAYMENT, p.getId(),
                "Mijoz nasiya to'lovi #" + customerId);
        audit.log("CREATE", "CustomerPayment", p.getId(), "mijoz=" + customerId + ", summa=" + amount);
        events.publishEvent(new CustomerPaymentEvent(p.getId(), amount, req.method(), DateUtils.today()));
        return new CustomerPaymentDto(p.getId(), customerId, amount, req.method().name(), p.getCreatedAt(),
                MoneyUtils.round(debt.subtract(amount)));
    }

    // ---------- ta'minotchi qarzi ----------

    @Transactional(readOnly = true)
    public List<SupplierDebtDto> supplierDebts(boolean onlyOpen) {
        List<SupplierDebt> list = onlyOpen ? supplierDebts.findByClosedFalseOrderByDueDateAsc()
                : supplierDebts.findAll(org.springframework.data.domain.Sort.by("dueDate"));
        Map<Long, String> names = suppliers.findAll().stream().collect(Collectors.toMap(Supplier::getId, Supplier::getName));
        return list.stream().map(d -> toDto(d, names.get(d.getSupplierId()))).toList();
    }

    @Transactional
    public SupplierDebtDto paySupplier(Long debtId, DebtRequests.Payment req) {
        SupplierDebt d = supplierDebts.lockById(debtId).orElseThrow(() -> new NotFoundException("Qarz topilmadi"));
        if (d.isClosed()) {
            throw BusinessException.conflict("Qarz allaqachon yopilgan");
        }
        BigDecimal amount = MoneyUtils.round(req.amount());
        if (amount.compareTo(d.getRemaining()) > 0) {
            throw new BusinessException("To'lov qoldiqdan oshib ketdi (qoldiq: " + d.getRemaining().toPlainString() + ")");
        }
        cash.record(CashDirection.OUT, amount, req.method(), CashSource.SUPPLIER_PAYMENT, d.getId(),
                "Ta'minotchiga to'lov, qarz #" + d.getId());
        d.setRemaining(MoneyUtils.round(d.getRemaining().subtract(amount)));
        if (d.getRemaining().signum() == 0) {
            d.setClosed(true);
        }
        audit.log("PAY", "SupplierDebt", d.getId(), "summa=" + amount + ", qoldiq=" + d.getRemaining());
        events.publishEvent(new SupplierPaymentEvent(d.getId(), amount, req.method(), DateUtils.today()));
        String name = suppliers.findById(d.getSupplierId()).map(Supplier::getName).orElse(null);
        return toDto(d, name);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalSupplierDebt() {
        return MoneyUtils.round(supplierDebts.sumOpen());
    }

    private SupplierDebtDto toDto(SupplierDebt d, String supplierName) {
        boolean overdue = !d.isClosed() && d.getDueDate() != null && d.getDueDate().isBefore(DateUtils.today());
        return new SupplierDebtDto(d.getId(), d.getPurchaseId(), d.getSupplierId(), supplierName, d.getAmount(),
                d.getRemaining(), d.getDueDate(), d.isClosed(), overdue);
    }
}
