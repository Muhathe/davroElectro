package univer.university.service;

import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
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
import univer.university.dto.request.ReturnRequests;
import univer.university.dto.response.ReturnResponses.*;
import univer.university.entity.*;
import univer.university.entity.enums.*;
import univer.university.event.ReturnCreatedEvent;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReturnService {

    private final SaleRepository sales;
    private final SaleReturnRepository returns;
    private final SaleReturnItemRepository returnItems;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final UserRepository users;
    private final StockMovementRepository movements;
    private final CashService cash;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private final ApplicationEventPublisher events;
    private final FiscalService fiscal;

    @Transactional(readOnly = true)
    public SaleForReturn findSale(String checkNo) {
        Sale s = sales.findByCheckNo(checkNo.trim()).orElseThrow(() -> new NotFoundException("Chek topilmadi: " + checkNo));
        String customerName = s.getCustomerId() == null ? null
                : customers.findById(s.getCustomerId()).map(Customer::getName).orElse(null);
        return new SaleForReturn(s.getId(), s.getCheckNo(), s.getCreatedAt(), s.getPaymentType(), customerName,
                s.getTotal(), lines(s), userName(s.getUserId()));
    }

    /** Vozvrat uchun chek qidirish: chek raqami (qismi), sotuvchi, tovar (id, nomi yoki barcode), sana. */
    @Transactional(readOnly = true)
    public SaleSearch searchSales(String checkNo, Long sellerId, Long productId, String product, LocalDate from, LocalDate to,
                                  int page, int size) {
        Specification<Sale> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (checkNo != null && !checkNo.isBlank()) {
                p.add(cb.like(cb.upper(root.get("checkNo")), "%" + checkNo.trim().toUpperCase(Locale.ROOT) + "%"));
            }
            if (sellerId != null) {
                p.add(cb.equal(root.get("userId"), sellerId));
            }
            addPeriod(p, root, cb, from, to);
            if (hasProductFilter(productId, product)) {
                p.add(productExists(q, cb, SaleItem.class, "sale", root, productId, product));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        int sz = Math.max(1, Math.min(size, 200));
        Page<Sale> res = sales.findAll(spec, PageRequest.of(Math.max(0, page), sz, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new SaleSearch(res.getContent().stream().map(s -> findSale(s.getCheckNo())).toList(), res.getTotalElements(),
                res.getNumber(), sz);
    }

    /** Vozvratlar tarixi: chek raqami, vozvratni qilgan xodim, tovar, sana. */
    @Transactional(readOnly = true)
    public ReturnSearch searchReturns(String checkNo, Long sellerId, Long productId, String product, LocalDate from, LocalDate to,
                                      int page, int size) {
        Specification<SaleReturn> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (checkNo != null && !checkNo.isBlank()) {
                Subquery<Long> sq = q.subquery(Long.class);
                Root<Sale> s = sq.from(Sale.class);
                sq.select(s.<Long>get("id")).where(cb.equal(s.get("id"), root.get("saleId")),
                        cb.like(cb.upper(s.get("checkNo")), "%" + checkNo.trim().toUpperCase(Locale.ROOT) + "%"));
                p.add(cb.exists(sq));
            }
            if (sellerId != null) {
                p.add(cb.equal(root.get("userId"), sellerId));
            }
            addPeriod(p, root, cb, from, to);
            if (hasProductFilter(productId, product)) {
                p.add(productExists(q, cb, SaleReturnItem.class, "saleReturn", root, productId, product));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        BigDecimal total = returns.findAll(spec).stream().map(SaleReturn::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int sz = Math.max(1, Math.min(size, 200));
        Page<SaleReturn> res = returns.findAll(spec, PageRequest.of(Math.max(0, page), sz, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new ReturnSearch(res.getContent().stream()
                .map(r -> toDto(r, sales.findById(r.getSaleId()).map(Sale::getCheckNo).orElse("?"))).toList(),
                res.getTotalElements(), MoneyUtils.round(total), res.getNumber(), sz);
    }

    /** Qidiruv filtri uchun xodimlar ro'yxati (XODIMLAR ruxsatisiz ham ko'rinadi). */
    @Transactional(readOnly = true)
    public List<SellerOption> sellers() {
        return users.findAll(Sort.by("fullName")).stream().filter(User::isActive)
                .map(u -> new SellerOption(u.getId(), u.getFullName())).toList();
    }

    private static void addPeriod(List<Predicate> p, Root<?> root, CriteriaBuilder cb, LocalDate from, LocalDate to) {
        if (from != null) {
            p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), DateUtils.startOfDay(from)));
        }
        if (to != null) {
            p.add(cb.lessThan(root.get("createdAt"), DateUtils.startOfDay(to.plusDays(1))));
        }
    }

    private static boolean hasProductFilter(Long productId, String product) {
        return productId != null || (product != null && !product.isBlank());
    }

    /** Hujjat qatorlari orasida filtrga mos tovar bormi. */
    private static <T> Predicate productExists(CommonAbstractCriteria q, CriteriaBuilder cb, Class<T> itemClass, String parent,
                                               Root<?> root, Long productId, String product) {
        Subquery<Long> sq = q.subquery(Long.class);
        Root<T> item = sq.from(itemClass);
        Root<Product> pr = sq.from(Product.class);
        List<Predicate> w = new ArrayList<>(List.of(cb.equal(item.get(parent), root), cb.equal(item.get("productId"), pr.get("id"))));
        if (productId != null) {
            w.add(cb.equal(pr.get("id"), productId));
        }
        if (product != null && !product.isBlank()) {
            String like = "%" + product.trim().toLowerCase(Locale.ROOT) + "%";
            w.add(cb.or(cb.like(cb.lower(pr.get("name")), like), cb.like(pr.get("barcode"), like)));
        }
        return cb.exists(sq.select(item.<Long>get("id")).where(w.toArray(new Predicate[0])));
    }

    private String userName(Long id) {
        return users.findById(id).map(User::getFullName).orElse(null);
    }

    @Transactional
    public ReturnDto create(ReturnRequests.Create req) {
        Sale sale = sales.findByCheckNo(req.checkNo().trim()).orElseThrow(() -> new NotFoundException("Chek topilmadi"));
        Map<Long, BigDecimal> wanted = new TreeMap<>();
        for (ReturnRequests.Item i : req.items()) {
            wanted.merge(i.productId(), i.qty(), BigDecimal::add);
        }
        Map<Long, SaleItem> soldItems = sale.getItems().stream().collect(Collectors.toMap(SaleItem::getProductId, i -> i));
        Map<Long, Product> locked = products.lockAllByIdIn(wanted.keySet()).stream().collect(Collectors.toMap(Product::getId, p -> p));
        // lock dan keyin o'qiladi: parallel vozvratlar sotilgandan ko'p qaytara olmasligi uchun
        Map<Long, BigDecimal> already = alreadyReturned(sale.getId());

        boolean toDebt = sale.getPaymentType() == PaymentType.NASIYA;
        SaleReturn ret = SaleReturn.builder().saleId(sale.getId()).customerId(sale.getCustomerId()).reason(req.reason())
                .total(BigDecimal.ZERO).toDebt(toDebt).createdAt(DateUtils.now()).userId(currentUser.id())
                .fiscal(fiscal.initialData()).build();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        List<Object[]> moves = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : wanted.entrySet()) {
            SaleItem si = soldItems.get(e.getKey());
            Product p = locked.get(e.getKey());
            if (si == null || p == null) {
                throw new BusinessException("Bu tovar chekda yo'q: #" + e.getKey());
            }
            BigDecimal qty = QtyUtils.normalize(p, e.getValue());
            BigDecimal available = si.getQty().subtract(already.getOrDefault(e.getKey(), BigDecimal.ZERO));
            if (qty.compareTo(available) > 0) {
                throw new BusinessException("Qaytariladigan miqdor oshib ketdi: " + p.getName() + " (mumkin: "
                        + available.stripTrailingZeros().toPlainString() + ")");
            }
            total = total.add(MoneyUtils.round(si.getPrice().multiply(qty)));
            cogs = cogs.add(MoneyUtils.round(si.getCost().multiply(qty)));
            // tovar sotilgan tannarxida omborga qaytadi (o'rtacha tannarx qayta hisoblanadi)
            BigDecimal newQty = p.getQty().add(qty);
            BigDecimal newAvg = p.getQty().multiply(p.getAvgCost()).add(qty.multiply(si.getCost()))
                    .divide(newQty, 2, RoundingMode.HALF_UP);
            p.setQty(newQty);
            p.setAvgCost(newAvg);
            ret.addItem(SaleReturnItem.builder().productId(p.getId()).qty(qty).price(si.getPrice()).cost(si.getCost()).build());
            moves.add(new Object[]{p.getId(), qty, si.getCost()});
        }
        total = MoneyUtils.round(total);
        ret.setTotal(total);
        SaleReturn saved = returns.save(ret);
        for (Object[] m : moves) {
            movements.save(StockMovement.builder().productId((Long) m[0]).type(MovementType.RETURN).qty((BigDecimal) m[1])
                    .cost((BigDecimal) m[2]).refId(saved.getId()).createdAt(saved.getCreatedAt()).build());
        }
        PayMethod method = null;
        if (!toDebt) {
            // aralash chekda vozvrat faqat naqddan
            method = sale.getPaymentType() == PaymentType.ARALASH ? PayMethod.NAQD : PayMethod.fromPayment(sale.getPaymentType());
            cash.record(CashDirection.OUT, total, method, CashSource.RETURN, saved.getId(), "Vozvrat " + sale.getCheckNo());
        }
        audit.log("CREATE", "SaleReturn", saved.getId(), "chek=" + sale.getCheckNo() + ", summa=" + total + ", sabab=" + req.reason());
        events.publishEvent(new ReturnCreatedEvent(saved.getId(), total, MoneyUtils.round(cogs), method, DateUtils.today()));
        return toDto(saved, sale.getCheckNo());
    }

    @Transactional(readOnly = true)
    public List<ReturnDto> recent() {
        Map<Long, String> checks = new HashMap<>();
        List<ReturnDto> res = new ArrayList<>();
        for (SaleReturn r : returns.findTop20ByOrderByCreatedAtDesc()) {
            String no = checks.computeIfAbsent(r.getSaleId(), id -> sales.findById(id).map(Sale::getCheckNo).orElse("?"));
            res.add(toDto(r, no));
        }
        return res;
    }

    @Transactional(readOnly = true)
    public ReturnDto get(Long id) {
        SaleReturn r = returns.findById(id).orElseThrow(() -> new NotFoundException("Vozvrat topilmadi"));
        return toDto(r, sales.findById(r.getSaleId()).map(Sale::getCheckNo).orElse("?"));
    }

    private Map<Long, BigDecimal> alreadyReturned(Long saleId) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] r : returnItems.returnedQtyBySale(saleId)) {
            m.put((Long) r[0], MoneyUtils.bd(r[1]));
        }
        return m;
    }

    private List<ReturnableLine> lines(Sale s) {
        Map<Long, BigDecimal> already = alreadyReturned(s.getId());
        Map<Long, String> names = products.findAllById(s.getItems().stream().map(SaleItem::getProductId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
        return s.getItems().stream().map(i -> {
            BigDecimal ret = already.getOrDefault(i.getProductId(), BigDecimal.ZERO);
            return new ReturnableLine(i.getProductId(), names.get(i.getProductId()), i.getQty(), ret,
                    i.getQty().subtract(ret), i.getPrice());
        }).toList();
    }

    private ReturnDto toDto(SaleReturn r, String checkNo) {
        Map<Long, String> names = products.findAllById(r.getItems().stream().map(SaleReturnItem::getProductId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
        List<ReturnableLine> items = r.getItems().stream().map(i -> new ReturnableLine(i.getProductId(),
                names.get(i.getProductId()), i.getQty(), i.getQty(), BigDecimal.ZERO, i.getPrice())).toList();
        return new ReturnDto(r.getId(), r.getSaleId(), checkNo, r.getReason(), r.getTotal(), r.isToDebt(), r.getCreatedAt(), items,
                FiscalService.toInfo(r.getFiscal()), r.getUserId(), userName(r.getUserId()));
    }
}
