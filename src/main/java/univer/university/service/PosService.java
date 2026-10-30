package univer.university.service;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.CurrentUserProvider;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.component.QtyUtils;
import univer.university.dto.request.PosRequests;
import univer.university.dto.response.PosResponses.*;
import univer.university.entity.*;
import univer.university.entity.enums.*;
import univer.university.event.SaleCompletedEvent;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PosService {

    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SaleRepository sales;
    private final StockMovementRepository movements;
    private final UserRepository users;
    private final CashService cash;
    private final DocNumberService numbers;
    private final CurrentUserProvider currentUser;
    private final ApplicationEventPublisher events;
    private final FiscalService fiscal;

    /** Bitta tranzaksiya: lock -> qoldiq tekshiruvi -> chegirma -> sklad -> sotuv -> kassa -> hodisa. */
    @Transactional
    public SaleDto checkout(PosRequests.Checkout req) {
        Customer customer = null;
        if (req.customerId() != null) {
            customer = customers.findById(req.customerId()).orElseThrow(() -> new NotFoundException("Mijoz topilmadi"));
            if (!customer.isActive()) {
                throw new BusinessException("Mijoz faol emas");
            }
        }

        // takroriy tovarlarni birlashtirish
        Map<Long, BigDecimal> wanted = new TreeMap<>();
        for (PosRequests.Item i : req.items()) {
            wanted.merge(i.productId(), i.qty(), BigDecimal::add);
        }
        // 1) PESSIMISTIC_WRITE lock (id tartibida — deadlock bo'lmasligi uchun)
        Map<Long, Product> locked = products.lockAllByIdIn(wanted.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        BigDecimal discount = customer == null ? BigDecimal.ZERO : MoneyUtils.nz(customer.getDiscountPercent());

        Sale sale = Sale.builder().checkNo(numbers.nextCheckNo()).createdAt(DateUtils.now()).userId(currentUser.id())
                .customerId(customer == null ? null : customer.getId())
                .discountPercent(discount).total(BigDecimal.ZERO).status(SaleStatus.COMPLETED).build();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        List<Object[]> moves = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : wanted.entrySet()) {
            Product p = locked.get(e.getKey());
            if (p == null) {
                throw new NotFoundException("Tovar topilmadi: #" + e.getKey());
            }
            if (!p.isActive()) {
                throw new BusinessException("Tovar sotuvda emas: " + p.getName());
            }
            BigDecimal qty = QtyUtils.normalize(p, e.getValue());
            // 2) qoldiq yetarliligi
            if (p.getQty().compareTo(qty) < 0) {
                throw new BusinessException("Qoldiq yetarli emas: " + p.getName() + " (mavjud: " + p.getQty().stripTrailingZeros().toPlainString() + ")");
            }
            // 3) chegirma bilan narx
            BigDecimal price = MoneyUtils.applyDiscount(p.getPrice(), discount);
            BigDecimal line = MoneyUtils.round(price.multiply(qty));
            subtotal = subtotal.add(MoneyUtils.round(p.getPrice().multiply(qty)));
            total = total.add(line);
            cogs = cogs.add(MoneyUtils.round(p.getAvgCost().multiply(qty)));
            // 4) qoldiqni kamaytirish
            p.setQty(p.getQty().subtract(qty));
            sale.addItem(SaleItem.builder().productId(p.getId()).qty(qty).price(price).cost(p.getAvgCost()).build());
            moves.add(new Object[]{p.getId(), qty.negate(), p.getAvgCost()});
        }
        total = MoneyUtils.round(total);
        subtotal = MoneyUtils.round(subtotal);
        sale.setTotal(total);
        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(subtotal.subtract(total).max(BigDecimal.ZERO));

        Map<PaymentType, BigDecimal> parts = paymentParts(req, total);
        BigDecimal credit = parts.getOrDefault(PaymentType.NASIYA, BigDecimal.ZERO);
        if (credit.signum() > 0 && customer == null) {
            throw new BusinessException("Nasiya savdo uchun mijoz tanlanishi shart");
        }
        sale.setPaymentType(parts.size() == 1 ? parts.keySet().iterator().next() : PaymentType.ARALASH);
        sale.setCreditAmount(MoneyUtils.round(credit));
        for (Map.Entry<PaymentType, BigDecimal> e : parts.entrySet()) {
            sale.addPayment(SalePayment.builder().method(e.getKey()).amount(e.getValue()).build());
        }

        BigDecimal naqd = parts.get(PaymentType.NAQD);
        if (naqd != null && req.receivedAmount() != null) {
            if (req.receivedAmount().compareTo(naqd) < 0) {
                throw new BusinessException("Qabul qilingan summa naqd to'lovdan kam (" + naqd.toPlainString() + ")");
            }
            sale.setReceivedAmount(MoneyUtils.round(req.receivedAmount()));
            sale.setChangeAmount(MoneyUtils.round(req.receivedAmount().subtract(naqd)));
        }
        // fiskal chek commit dan keyin yuboriladi (PosController)
        sale.setFiscal(fiscal.initialData());

        // 5) sotuv
        Sale saved = sales.save(sale);
        for (Object[] m : moves) {
            movements.save(StockMovement.builder().productId((Long) m[0]).type(MovementType.SALE).qty((BigDecimal) m[1])
                    .cost((BigDecimal) m[2]).refId(saved.getId()).createdAt(saved.getCreatedAt()).build());
        }
        // 6) kassa: har bir pul usuli alohida (nasiya qismida o'zgarmaydi)
        for (Map.Entry<PaymentType, BigDecimal> e : parts.entrySet()) {
            if (e.getKey() != PaymentType.NASIYA) {
                cash.record(CashDirection.IN, e.getValue(), PayMethod.fromPayment(e.getKey()), CashSource.SALE,
                        saved.getId(), "Sotuv " + saved.getCheckNo());
            }
        }
        // 7) hodisa (jurnal shu tranzaksiya BEFORE_COMMIT da yoziladi)
        events.publishEvent(new SaleCompletedEvent(saved.getId(), saved.getCheckNo(), total, MoneyUtils.round(cogs),
                parts, DateUtils.today()));
        return toDtos(List.of(saved)).get(0);
    }

    /** Chekni to'lov qismlariga ajratadi; payments dagi pul summasidan ortgani NASIYA bo'ladi. */
    private static Map<PaymentType, BigDecimal> paymentParts(PosRequests.Checkout req, BigDecimal total) {
        Map<PaymentType, BigDecimal> parts = new EnumMap<>(PaymentType.class);
        if (req.payments() == null || req.payments().isEmpty()) {
            if (req.paymentType() == null || req.paymentType() == PaymentType.ARALASH) {
                throw BusinessException.badRequest("To'lov turini tanlang");
            }
            parts.put(req.paymentType(), total);
            return parts;
        }
        BigDecimal paid = BigDecimal.ZERO;
        for (PosRequests.Payment p : req.payments()) {
            if (p.method() == PaymentType.ARALASH) {
                throw BusinessException.badRequest("To'lov usuli noto'g'ri");
            }
            if (p.method() != PaymentType.NASIYA) {
                BigDecimal amount = MoneyUtils.round(p.amount());
                parts.merge(p.method(), amount, BigDecimal::add);
                paid = paid.add(amount);
            }
        }
        if (paid.compareTo(total) > 0) {
            throw new BusinessException("To'lovlar chek summasidan oshib ketdi (" + total.toPlainString() + ")");
        }
        if (paid.compareTo(total) < 0) {
            parts.put(PaymentType.NASIYA, total.subtract(paid));
        }
        if (parts.isEmpty()) {
            throw BusinessException.badRequest("To'lov turini tanlang");
        }
        return parts;
    }

    @Transactional(readOnly = true)
    public DayCashSummary salesOfDay(LocalDate date) {
        LocalDate d = date == null ? DateUtils.today() : date;
        List<Sale> list = sales.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                DateUtils.startOfDay(d), DateUtils.startOfDay(d.plusDays(1)));
        List<SaleDto> dtos = toDtos(list);
        BigDecimal total = list.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DayCashSummary(d, list.size(), MoneyUtils.round(total), dtos);
    }

    /** Fiskal holati bo'yicha nazorat ro'yxati (masalan PENDING), eng eskisi birinchi. */
    @Transactional(readOnly = true)
    public DayCashSummary byFiscalStatus(FiscalStatus status) {
        List<Sale> list = sales.findAll((Specification<Sale>) (root, q, cb) -> cb.equal(root.get("fiscal").get("status"), status),
                PageRequest.of(0, 500, Sort.by("createdAt", "id"))).getContent();
        BigDecimal total = list.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DayCashSummary(null, list.size(), MoneyUtils.round(total), toDtos(list));
    }

    @Transactional(readOnly = true)
    public SaleDto get(Long id) {
        Sale s = sales.findById(id).orElseThrow(() -> new NotFoundException("Sotuv topilmadi"));
        return toDtos(List.of(s)).get(0);
    }

    @Transactional(readOnly = true)
    public SaleDto getByCheckNo(String checkNo) {
        Sale s = sales.findByCheckNo(checkNo.trim()).orElseThrow(() -> new NotFoundException("Chek topilmadi: " + checkNo));
        return toDtos(List.of(s)).get(0);
    }

    /** Davr bo'yicha sotuvlar tarixi (sotuvchi, mijoz, to'lov turi filtrlari bilan). */
    @Transactional(readOnly = true)
    public SaleHistory history(LocalDate from, LocalDate to, Long userId, Long customerId, PaymentType paymentType,
                               FiscalStatus fiscalStatus, int page, int size) {
        LocalDate f = from == null ? DateUtils.today().withDayOfMonth(1) : from;
        LocalDate t = to == null ? DateUtils.today() : to;
        Specification<Sale> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), DateUtils.startOfDay(f)));
            p.add(cb.lessThan(root.get("createdAt"), DateUtils.startOfDay(t.plusDays(1))));
            if (userId != null) {
                p.add(cb.equal(root.get("userId"), userId));
            }
            if (customerId != null) {
                p.add(cb.equal(root.get("customerId"), customerId));
            }
            if (paymentType != null) {
                // aralash cheklar ham shu usul bilan to'langan bo'lsa ko'rinadi
                Subquery<Long> part = q.subquery(Long.class);
                Root<SalePayment> sp = part.from(SalePayment.class);
                part.select(sp.<Long>get("id")).where(cb.equal(sp.get("sale"), root), cb.equal(sp.get("method"), paymentType));
                p.add(cb.or(cb.equal(root.get("paymentType"), paymentType), cb.exists(part)));
            }
            if (fiscalStatus != null) {
                p.add(cb.equal(root.get("fiscal").get("status"), fiscalStatus));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        BigDecimal total = sales.findAll(spec).stream().map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int sz = Math.max(1, Math.min(size, 200));
        Page<Sale> res = sales.findAll(spec, PageRequest.of(Math.max(0, page), sz,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new SaleHistory(toDtos(res.getContent()), res.getTotalElements(), MoneyUtils.round(total), res.getNumber(), sz);
    }

    List<SaleDto> toDtos(List<Sale> list) {
        Set<Long> userIds = list.stream().map(Sale::getUserId).collect(Collectors.toSet());
        Set<Long> custIds = list.stream().map(Sale::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> prodIds = list.stream().flatMap(s -> s.getItems().stream()).map(SaleItem::getProductId).collect(Collectors.toSet());
        Map<Long, String> userNames = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getFullName));
        Map<Long, String> custNames = customers.findAllById(custIds).stream().collect(Collectors.toMap(Customer::getId, Customer::getName));
        Map<Long, String> prodNames = products.findAllById(prodIds).stream().collect(Collectors.toMap(Product::getId, Product::getName));
        List<SaleDto> res = new ArrayList<>();
        for (Sale s : list) {
            List<SaleLine> lines = s.getItems().stream().map(i -> new SaleLine(i.getProductId(), prodNames.get(i.getProductId()),
                    i.getQty(), i.getPrice(), MoneyUtils.round(i.getPrice().multiply(i.getQty())))).toList();
            // eski sotuvlarda subtotal saqlanmagan
            BigDecimal subtotal = s.getSubtotal() == null ? s.getTotal() : s.getSubtotal();
            BigDecimal discountAmount = s.getDiscountAmount() == null ? BigDecimal.ZERO : s.getDiscountAmount();
            // eski sotuvlarda to'lov qismlari saqlanmagan
            BigDecimal credit = s.getCreditAmount() != null ? s.getCreditAmount()
                    : s.getPaymentType() == PaymentType.NASIYA ? s.getTotal() : BigDecimal.ZERO;
            List<PaymentLine> payments = s.getPayments().isEmpty() ? List.of(new PaymentLine(s.getPaymentType(), s.getTotal()))
                    : s.getPayments().stream().map(p -> new PaymentLine(p.getMethod(), p.getAmount())).toList();
            res.add(new SaleDto(s.getId(), s.getCheckNo(), s.getCreatedAt(), s.getUserId(), userNames.get(s.getUserId()),
                    s.getCustomerId(), custNames.get(s.getCustomerId()), s.getPaymentType(), s.getDiscountPercent(),
                    subtotal, discountAmount, s.getTotal(), s.getReceivedAmount(), s.getChangeAmount(), s.getStatus(),
                    FiscalService.toInfo(s.getFiscal()), lines, credit, payments));
        }
        return res;
    }
}
