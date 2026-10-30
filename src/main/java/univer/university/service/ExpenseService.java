package univer.university.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.CurrentUserProvider;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.dto.request.ExpenseRequests;
import univer.university.dto.response.ExpenseResponses.*;
import univer.university.entity.Expense;
import univer.university.entity.ExpenseCategory;
import univer.university.entity.User;
import univer.university.entity.enums.CashDirection;
import univer.university.entity.enums.CashSource;
import univer.university.entity.enums.ExpenseStatus;
import univer.university.entity.enums.ExpenseType;
import univer.university.entity.enums.PayMethod;
import univer.university.event.ExpenseCancelledEvent;
import univer.university.event.ExpenseCreatedEvent;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.ExpenseCategoryRepository;
import univer.university.repository.ExpenseRepository;
import univer.university.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final ExpenseCategoryRepository categories;
    private final UserRepository users;
    private final CashService cash;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private final ApplicationEventPublisher events;

    // ---------- kategoriyalar ----------

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories() {
        return categories.findAllByOrderByIdAsc().stream().map(ExpenseService::toDto).toList();
    }

    @Transactional
    public CategoryDto createCategory(ExpenseRequests.Category req) {
        String name = req.name().trim();
        if (categories.findByName(name).isPresent()) {
            throw BusinessException.conflict("Bunday kategoriya mavjud");
        }
        ExpenseCategory c = categories.save(ExpenseCategory.builder().name(name).type(req.type()).build());
        audit.log("CREATE", "ExpenseCategory", c.getId(), name + " / " + c.getType());
        return toDto(c);
    }

    /** Nomi doim o'zgaradi; turi esa faqat harajat yozilmagan kategoriyada (storno jurnali eski turga bog'liq). */
    @Transactional
    public CategoryDto updateCategory(Long id, ExpenseRequests.Category req) {
        ExpenseCategory c = categories.findById(id).orElseThrow(() -> new NotFoundException("Kategoriya topilmadi"));
        String name = req.name().trim();
        categories.findByName(name).filter(x -> !x.getId().equals(id)).ifPresent(x -> {
            throw BusinessException.conflict("Bunday kategoriya mavjud");
        });
        if (req.type() != c.getType() && expenses.existsByCategory_Id(id)) {
            throw new BusinessException("Harajat yozilgan kategoriyaning turini o'zgartirib bo'lmaydi");
        }
        c.setName(name);
        c.setType(req.type());
        audit.log("UPDATE", "ExpenseCategory", c.getId(), name + " / " + c.getType());
        return toDto(c);
    }

    // ---------- harajat ----------

    /** Kassadan harajat kiritish: Expense + CashOperation(OUT) + AuditLog bitta tranzaksiyada. */
    @Transactional
    public ExpenseDto create(ExpenseRequests.Create req) {
        ExpenseCategory cat = categories.findById(req.categoryId())
                .orElseThrow(() -> new NotFoundException("Kategoriya topilmadi"));
        BigDecimal amount = MoneyUtils.round(req.amount());
        Long uid = currentUser.id();
        Expense e = expenses.save(Expense.builder().category(cat).amount(amount).paymentMethod(req.paymentMethod())
                .note(req.note()).status(ExpenseStatus.ACTIVE).userId(uid).createdAt(DateUtils.now()).build());
        cash.record(CashDirection.OUT, amount, req.paymentMethod(), CashSource.EXPENSE, e.getId(),
                "Harajat X-" + e.getId() + " (" + cat.getName() + ")");
        audit.log("CREATE", "Expense", e.getId(), cat.getName() + " " + amount + " " + req.paymentMethod());
        events.publishEvent(new ExpenseCreatedEvent(e.getId(), amount, req.paymentMethod(),
                cat.getType() == ExpenseType.OWNER_DRAW, DateUtils.today()));
        return toDto(e, nameOf(uid));
    }

    /** Storno: o'chirilmaydi, teskari kassa yozuvi yoziladi. */
    @Transactional
    public ExpenseDto cancel(Long id, ExpenseRequests.Cancel req) {
        Expense e = expenses.findById(id).orElseThrow(() -> new NotFoundException("Harajat topilmadi"));
        if (e.getStatus() == ExpenseStatus.CANCELLED) {
            throw BusinessException.conflict("Harajat allaqachon bekor qilingan");
        }
        Long uid = currentUser.id();
        e.setStatus(ExpenseStatus.CANCELLED);
        e.setCancelReason(req.reason().trim());
        e.setCancelledAt(DateUtils.now());
        e.setCancelledBy(uid);
        cash.record(CashDirection.IN, e.getAmount(), e.getPaymentMethod(), CashSource.EXPENSE_STORNO, e.getId(),
                "Storno: X-" + e.getId());
        audit.log("CANCEL", "Expense", e.getId(), "sabab: " + e.getCancelReason());
        events.publishEvent(new ExpenseCancelledEvent(e.getId(), e.getAmount(), e.getPaymentMethod(),
                e.getCategory().getType() == ExpenseType.OWNER_DRAW, DateUtils.today()));
        return toDto(e, nameOf(e.getUserId()));
    }

    @Transactional(readOnly = true)
    public ExpenseDto get(Long id) {
        Expense e = expenses.findById(id).orElseThrow(() -> new NotFoundException("Harajat topilmadi"));
        return toDto(e, nameOf(e.getUserId()));
    }

    @Transactional(readOnly = true)
    public ExpenseList list(String month, Long categoryId, PayMethod method, Long userId) {
        YearMonth ym = DateUtils.parseMonth(month);
        LocalDateTime from = DateUtils.monthStart(ym);
        LocalDateTime to = DateUtils.monthEnd(ym);
        Specification<Expense> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            p.add(cb.lessThan(root.get("createdAt"), to));
            if (categoryId != null) {
                p.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (method != null) {
                p.add(cb.equal(root.get("paymentMethod"), method));
            }
            if (userId != null) {
                p.add(cb.equal(root.get("userId"), userId));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        List<Expense> all = expenses.findAll(spec, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id"));
        Map<Long, String> names = users.findAllById(all.stream().map(Expense::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        List<ExpenseDto> items = all.stream().map(e -> toDto(e, names.get(e.getUserId()))).toList();
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byCat = new LinkedHashMap<>();
        for (Expense e : all) {
            if (e.getStatus() == ExpenseStatus.ACTIVE) {
                total = total.add(e.getAmount());
                byCat.merge(e.getCategory().getName(), e.getAmount(), BigDecimal::add);
            }
        }
        List<CategoryTotal> cats = byCat.entrySet().stream()
                .map(en -> new CategoryTotal(en.getKey(), MoneyUtils.round(en.getValue())))
                .sorted((a, b) -> b.amount().compareTo(a.amount())).toList();
        return new ExpenseList(items, MoneyUtils.round(total), cats);
    }

    private String nameOf(Long userId) {
        return users.findById(userId).map(User::getFullName).orElse(null);
    }

    static CategoryDto toDto(ExpenseCategory c) {
        return new CategoryDto(c.getId(), c.getName(), c.getType());
    }

    static ExpenseDto toDto(Expense e, String userName) {
        return new ExpenseDto(e.getId(), e.getCategory().getId(), e.getCategory().getName(), e.getCategory().getType(),
                e.getAmount(), e.getPaymentMethod(), e.getNote(), e.getStatus(), e.getCancelReason(), e.getUserId(),
                userName, e.getCreatedAt(), e.getCancelledAt());
    }
}
