package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import univer.university.component.BarcodeValidator;
import univer.university.component.DateUtils;
import univer.university.component.ExcelReader;
import univer.university.component.ExcelReader.ExcelRow;
import univer.university.component.MoneyUtils;
import univer.university.component.QtyUtils;
import univer.university.dto.response.ProductResponses.ImportResult;
import univer.university.dto.response.ProductResponses.RowError;
import univer.university.entity.Product;
import univer.university.entity.StockMovement;
import univer.university.entity.Supplier;
import univer.university.entity.enums.MovementType;
import univer.university.entity.enums.Unit;
import univer.university.event.OpeningBalanceEvent;
import univer.university.repository.ProductRepository;
import univer.university.repository.StockMovementRepository;
import univer.university.repository.SupplierRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mahsulot importi. Shablon: barcode, nomi, kategoriya, birlik, tannarx, narx, miqdor, minQty, ta'minotchi.
 * To'g'ri qatorlar saqlanadi, xatolilari ro'yxat bilan qaytariladi.
 * Yangi tovarda miqdor &gt; 0 bo'lsa — boshlang'ich qoldiq (OPENING) sifatida yoziladi (egasi kapitali hisobiga).
 * Mavjud tovarda faqat nomi/kategoriya/birlik/narx/minQty/ta'minotchi yangilanadi (qoldiq va tannarx o'zgarmaydi).
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    public static final List<String> HEADERS = List.of("barcode", "nomi", "kategoriya", "birlik", "tannarx", "narx", "miqdor", "minQty", "ta'minotchi");

    private final ExcelReader excel;
    private final BarcodeValidator barcodes;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final StockMovementRepository movements;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public byte[] template() {
        return excel.template("Mahsulotlar", HEADERS,
                List.of("4780000000011", "LED lampa 12W", "Lampalar", "dona", "8000", "12000", "50", "10", "Elektro Optom"));
    }

    @Transactional
    public ImportResult importFile(MultipartFile file) {
        List<ExcelRow> rows = excel.read(file);
        List<RowError> errors = new ArrayList<>();
        int ok = 0;
        BigDecimal openingValue = BigDecimal.ZERO;
        for (ExcelRow r : rows) {
            try {
                openingValue = openingValue.add(importRow(r));
                ok++;
            } catch (RuntimeException e) {
                errors.add(new RowError(r.number(), e.getMessage()));
            }
        }
        if (openingValue.signum() > 0) {
            events.publishEvent(new OpeningBalanceEvent(BigDecimal.ZERO, MoneyUtils.round(openingValue), DateUtils.today()));
        }
        audit.log("IMPORT", "Product", null, "jami=" + rows.size() + ", saqlandi=" + ok + ", xato=" + errors.size());
        return new ImportResult(rows.size(), ok, errors);
    }

    /** @return yangi boshlang'ich qoldiq qiymati */
    private BigDecimal importRow(ExcelRow r) {
        String barcode = r.get("barcode");
        barcodes.requireValid(barcode);
        String name = req(r.get("nomi"), "nomi");
        Unit unit = parseUnit(r.get("birlik"));
        BigDecimal price = number(r.get("narx"), "narx");
        BigDecimal cost = r.get("tannarx").isBlank() ? BigDecimal.ZERO : number(r.get("tannarx"), "tannarx");
        BigDecimal qty = r.get("miqdor").isBlank() ? BigDecimal.ZERO : QtyUtils.scale(number(r.get("miqdor"), "miqdor"));
        BigDecimal minQty = r.get("minqty").isBlank() ? BigDecimal.ZERO : QtyUtils.scale(number(r.get("minqty"), "minQty"));
        String category = r.get("kategoriya").isBlank() ? null : r.get("kategoriya");
        Long supplierId = null;
        String sup = r.get("taminotchi");
        if (!sup.isBlank()) {
            supplierId = suppliers.findByName(sup).orElseGet(() -> suppliers.save(Supplier.builder().name(sup).build())).getId();
        }
        Product p = products.findByBarcode(barcode).orElse(null);
        if (p != null) {
            p.setName(name);
            p.setCategory(category);
            p.setUnit(unit);
            p.setPrice(MoneyUtils.round(price));
            p.setMinQty(minQty);
            p.setSupplierId(supplierId);
            return BigDecimal.ZERO;
        }
        if (qty.signum() < 0) {
            throw new IllegalArgumentException("miqdor manfiy bo'lmasligi kerak");
        }
        if (unit == Unit.DONA && qty.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("dona uchun miqdor butun bo'lishi kerak");
        }
        p = products.save(Product.builder().name(name).barcode(barcode).category(category).unit(unit)
                .avgCost(MoneyUtils.round(cost)).lastCost(MoneyUtils.round(cost)).price(MoneyUtils.round(price))
                .qty(qty).minQty(minQty).supplierId(supplierId).active(true).build());
        if (qty.signum() > 0) {
            movements.save(StockMovement.builder().productId(p.getId()).type(MovementType.OPENING).qty(qty)
                    .cost(p.getAvgCost()).createdAt(DateUtils.now()).build());
            return MoneyUtils.round(qty.multiply(p.getAvgCost()));
        }
        return BigDecimal.ZERO;
    }

    static Unit parseUnit(String s) {
        String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "", "dona", "d", "шт" -> Unit.DONA;
            case "metr", "m", "metr.", "м" -> Unit.METR;
            default -> throw new IllegalArgumentException("birlik noto'g'ri (dona yoki metr): " + s);
        };
    }

    static String req(String v, String col) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(col + " bo'sh");
        }
        return v.trim();
    }

    static BigDecimal number(String v, String col) {
        try {
            BigDecimal n = new BigDecimal(req(v, col).replace(" ", "").replace(",", "."));
            if (n.signum() < 0) {
                throw new IllegalArgumentException(col + " manfiy bo'lmasligi kerak");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(col + " son emas: " + v);
        }
    }
}
