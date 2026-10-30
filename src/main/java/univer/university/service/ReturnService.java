package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
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
    private final StockMovementRepository movements;
    private final CashService cash;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public SaleForReturn findSale(String checkNo) {
        Sale s = sales.findByCheckNo(checkNo.trim()).orElseThrow(() -> new NotFoundException("Chek topilmadi: " + checkNo));
        String customerName = s.getCustomerId() == null ? null
                : customers.findById(s.getCustomerId()).map(Customer::getName).orElse(null);
        return new SaleForReturn(s.getId(), s.getCheckNo(), s.getCreatedAt(), s.getPaymentType(), customerName,
                s.getTotal(), lines(s));
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
                .total(BigDecimal.ZERO).toDebt(toDebt).createdAt(DateUtils.now()).userId(currentUser.id()).build();
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
            method = PayMethod.fromPayment(sale.getPaymentType());
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
        return new ReturnDto(r.getId(), r.getSaleId(), checkNo, r.getReason(), r.getTotal(), r.isToDebt(), r.getCreatedAt(), items);
    }
}
