package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.CurrentUserProvider;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.component.QtyUtils;
import univer.university.dto.request.StockRequests;
import univer.university.dto.response.ProductResponses.*;
import univer.university.entity.*;
import univer.university.entity.enums.*;
import univer.university.event.PurchaseCreatedEvent;
import univer.university.event.StockAdjustedEvent;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {

    private static final BigDecimal DAYS30 = new BigDecimal("30");

    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final PurchaseRepository purchases;
    private final SupplierDebtRepository supplierDebts;
    private final StockMovementRepository movements;
    private final SaleItemRepository saleItems;
    private final CashService cash;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private final ApplicationEventPublisher events;

    // ---------- holat ----------

    @Transactional(readOnly = true)
    public StockList list(String q, String category, StockStatus status) {
        Map<Long, BigDecimal> sold = soldLast30();
        List<StockRow> rows = new ArrayList<>();
        for (Product p : products.findAll(Sort.by("name"))) {
            if (!p.isActive()) {
                continue;
            }
            if (q != null && !q.isBlank() && !(p.getName().toLowerCase().contains(q.trim().toLowerCase())
                    || p.getBarcode().contains(q.trim()))) {
                continue;
            }
            if (category != null && !category.isBlank() && !category.equals(p.getCategory())) {
                continue;
            }
            rows.add(row(p, sold.getOrDefault(p.getId(), BigDecimal.ZERO)));
        }
        BigDecimal value = rows.stream().map(StockRow::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal frozen = rows.stream().map(StockRow::frozenValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        long out = rows.stream().filter(r -> r.status() == StockStatus.TUGAGAN).count();
        long low = rows.stream().filter(r -> r.status() == StockStatus.TUGAYAPTI).count();
        long dead = rows.stream().filter(r -> r.status() == StockStatus.OLIK).count();
        long excess = rows.stream().filter(r -> r.status() == StockStatus.ORTIQCHA).count();
        if (status != null) {
            rows = rows.stream().filter(r -> r.status() == status).toList();
        }
        return new StockList(rows, MoneyUtils.round(value), MoneyUtils.round(frozen), out, low, dead, excess);
    }

    StockRow row(Product p, BigDecimal sold30) {
        BigDecimal daily = sold30.divide(DAYS30, 4, RoundingMode.HALF_UP);
        BigDecimal cover = daily.signum() > 0 ? p.getQty().divide(daily, 1, RoundingMode.HALF_UP) : null;
        StockStatus status;
        if (p.getQty().signum() <= 0) {
            status = StockStatus.TUGAGAN;
        } else if ((cover != null && cover.compareTo(new BigDecimal("7")) < 0)
                || (p.getMinQty().signum() > 0 && p.getQty().compareTo(p.getMinQty()) <= 0)) {
            status = StockStatus.TUGAYAPTI;
        } else if (sold30.signum() == 0) {
            status = StockStatus.OLIK;
        } else if (cover != null && cover.compareTo(new BigDecimal("90")) > 0) {
            status = StockStatus.ORTIQCHA;
        } else {
            status = StockStatus.NORMAL;
        }
        BigDecimal value = MoneyUtils.round(p.getQty().multiply(p.getAvgCost()));
        BigDecimal frozen = BigDecimal.ZERO;
        if (status == StockStatus.OLIK) {
            frozen = value;
        } else if (cover != null && cover.compareTo(new BigDecimal("60")) > 0) {
            frozen = MoneyUtils.round(p.getQty().subtract(daily.multiply(new BigDecimal("60"))).multiply(p.getAvgCost()));
        }
        return new StockRow(p.getId(), p.getName(), p.getBarcode(), p.getCategory(), p.getUnit(), p.getQty(), p.getMinQty(),
                p.getAvgCost(), p.getPrice(), value, sold30, daily, cover, status, frozen);
    }

    private Map<Long, BigDecimal> soldLast30() {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] r : saleItems.soldQtyByProduct(DateUtils.now().minusDays(30))) {
            m.put((Long) r[0], MoneyUtils.bd(r[1]));
        }
        return m;
    }

    @Transactional(readOnly = true)
    public List<OrderSuggestion> orderSuggestions() {
        Map<Long, BigDecimal> sold = soldLast30();
        Map<Long, String> sup = suppliers.findAll().stream().collect(Collectors.toMap(Supplier::getId, Supplier::getName));
        List<OrderSuggestion> res = new ArrayList<>();
        for (Product p : products.findAll(Sort.by("name"))) {
            BigDecimal s30 = sold.getOrDefault(p.getId(), BigDecimal.ZERO);
            if (!p.isActive() || s30.signum() == 0) {
                continue;
            }
            BigDecimal daily = s30.divide(DAYS30, 4, RoundingMode.HALF_UP);
            BigDecimal need = daily.multiply(new BigDecimal("21")).subtract(p.getQty()).setScale(0, RoundingMode.CEILING);
            if (need.signum() <= 0) {
                continue;
            }
            BigDecimal unitCost = p.getLastCost().signum() > 0 ? p.getLastCost() : p.getAvgCost();
            res.add(new OrderSuggestion(p.getId(), p.getName(), p.getSupplierId(), sup.get(p.getSupplierId()), p.getQty(),
                    p.getMinQty(), daily, need, MoneyUtils.round(need.multiply(unitCost))));
        }
        res.sort(Comparator.comparing(OrderSuggestion::estimatedCost).reversed());
        return res;
    }

    @Transactional(readOnly = true)
    public List<MovementDto> movements(Long productId, int limit) {
        PageRequest pr = PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        Page<StockMovement> page = productId == null ? movements.findAllByOrderByCreatedAtDescIdDesc(pr)
                : movements.findByProductIdOrderByCreatedAtDescIdDesc(productId, pr);
        Map<Long, String> names = products.findAllById(page.stream().map(StockMovement::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Product::getName));
        return page.stream().map(m -> new MovementDto(m.getId(), m.getProductId(), names.get(m.getProductId()),
                m.getType().name(), m.getQty(), m.getCost(), m.getRefId(), m.getCreatedAt())).toList();
    }

    // ---------- kirim ----------

    /** Prixod: o'rtacha tannarx qayta hisoblanadi, to'lanmagan qism ta'minotchi qarziga (14 kun) yoziladi. */
    @Transactional
    public IncomingResult incoming(StockRequests.Incoming req) {
        if (req.supplierId() != null && !suppliers.existsById(req.supplierId())) {
            throw new NotFoundException("Ta'minotchi topilmadi");
        }
        Map<Long, BigDecimal> qtys = new TreeMap<>();
        Map<Long, BigDecimal> costValue = new HashMap<>(); // jami qiymat (qty*cost)
        for (StockRequests.IncomingItem i : req.items()) {
            qtys.merge(i.productId(), i.qty(), BigDecimal::add);
            costValue.merge(i.productId(), i.qty().multiply(i.cost()), BigDecimal::add);
        }
        Map<Long, Product> locked = products.lockAllByIdIn(qtys.keySet()).stream().collect(Collectors.toMap(Product::getId, p -> p));
        Purchase purchase = Purchase.builder().supplierId(req.supplierId()).total(BigDecimal.ZERO).paid(BigDecimal.ZERO)
                .payMethod(req.method()).createdAt(DateUtils.now()).userId(currentUser.id()).build();
        BigDecimal total = BigDecimal.ZERO;
        List<Object[]> moves = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : qtys.entrySet()) {
            Product p = locked.get(e.getKey());
            if (p == null) {
                throw new NotFoundException("Tovar topilmadi: #" + e.getKey());
            }
            BigDecimal qty = QtyUtils.normalize(p, e.getValue());
            BigDecimal value = MoneyUtils.round(costValue.get(e.getKey()));
            BigDecimal unitCost = value.divide(qty, 2, RoundingMode.HALF_UP);
            BigDecimal newQty = p.getQty().add(qty);
            p.setAvgCost(p.getQty().multiply(p.getAvgCost()).add(value).divide(newQty, 2, RoundingMode.HALF_UP));
            p.setLastCost(unitCost);
            p.setQty(newQty);
            total = total.add(value);
            purchase.addItem(PurchaseItem.builder().productId(p.getId()).qty(qty).cost(unitCost).build());
            moves.add(new Object[]{p.getId(), qty, unitCost});
        }
        total = MoneyUtils.round(total);
        BigDecimal paid = MoneyUtils.round(req.paid());
        if (paid.compareTo(total) > 0 && paid.subtract(total).compareTo(new BigDecimal("0.05")) <= 0) {
            paid = total; // yaxlitlash farqi
        }
        if (paid.compareTo(total) > 0) {
            throw new BusinessException("To'langan summa kirim summasidan oshib ketdi (" + total.toPlainString() + ")");
        }
        BigDecimal debt = total.subtract(paid);
        if (debt.signum() > 0 && req.supplierId() == null) {
            throw new BusinessException("Qarzga olingan kirim uchun ta'minotchi tanlang");
        }
        if (paid.signum() > 0 && req.method() == null) {
            throw new BusinessException("To'lov usulini tanlang");
        }
        purchase.setTotal(total);
        purchase.setPaid(paid);
        Purchase saved = purchases.save(purchase);
        for (Object[] m : moves) {
            movements.save(StockMovement.builder().productId((Long) m[0]).type(MovementType.INCOMING).qty((BigDecimal) m[1])
                    .cost((BigDecimal) m[2]).refId(saved.getId()).createdAt(saved.getCreatedAt()).build());
        }
        if (paid.signum() > 0) {
            cash.record(CashDirection.OUT, paid, req.method(), CashSource.PURCHASE, saved.getId(), "Tovar kirimi #" + saved.getId());
        }
        if (debt.signum() > 0) {
            supplierDebts.save(SupplierDebt.builder().purchaseId(saved.getId()).supplierId(req.supplierId()).amount(debt)
                    .remaining(debt).dueDate(DateUtils.today().plusDays(14)).closed(false).build());
        }
        audit.log("CREATE", "Purchase", saved.getId(), "jami=" + total + ", to'langan=" + paid + ", qarz=" + debt);
        events.publishEvent(new PurchaseCreatedEvent(saved.getId(), total, paid, req.method(), DateUtils.today()));
        return new IncomingResult(saved.getId(), total, paid, debt);
    }

    // ---------- tuzatish ----------

    @Transactional
    public ProductDto adjust(StockRequests.Adjust req) {
        Product p = products.lockAllByIdIn(List.of(req.productId())).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Tovar topilmadi"));
        BigDecimal newQty = QtyUtils.scale(req.newQty());
        BigDecimal diff = newQty.subtract(p.getQty());
        if (diff.signum() == 0) {
            return ProductService.toDto(p);
        }
        p.setQty(newQty);
        LocalDateTime now = DateUtils.now();
        StockMovement m = movements.save(StockMovement.builder().productId(p.getId()).type(MovementType.ADJUSTMENT)
                .qty(diff).cost(p.getAvgCost()).refId(null).createdAt(now).build());
        audit.log("ADJUST", "Product", p.getId(), p.getName() + ": " + diff.toPlainString() + " (" + req.note() + ")");
        events.publishEvent(new StockAdjustedEvent(p.getId(), MoneyUtils.round(diff.multiply(p.getAvgCost())), DateUtils.today()));
        return ProductService.toDto(p);
    }
}
