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
import univer.university.component.ExcelReader.TemplateColumn;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mahsulot importi: "Yangi tovar" formasidagi hamma maydon (rasmdan tashqari), qoidalari ham forma bilan bir xil.
 * To'g'ri qatorlar saqlanadi, xatolilari ro'yxat bilan qaytariladi.
 * Yangi tovarda miqdor &gt; 0 bo'lsa — boshlang'ich qoldiq (OPENING) sifatida yoziladi (egasi kapitali hisobiga), tannarx shart.
 * Mavjud tovarda faqat nomi/kategoriya/birlik/narx/minQty/ta'minotchi yangilanadi (qoldiq va tannarx o'zgarmaydi);
 * bo'sh qoldirilgan ixtiyoriy katak mavjud qiymatni o'chirmaydi, farqli miqdor ogohlantirish bilan qaytariladi.
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    public static final List<TemplateColumn> COLUMNS = List.of(
            new TemplateColumn("barcode *", true, true, List.of(), "8-14 ta raqam. Bazada bor bo'lsa tovar yangilanadi, yo'q bo'lsa yangi tovar yaratiladi", "4780000000011"),
            new TemplateColumn("nomi *", true, false, List.of(), "Tovar nomi, 255 belgigacha", "LED lampa 12W"),
            new TemplateColumn("kategoriya", false, false, List.of(), "Masalan: Lampalar, Kabellar", "Lampalar"),
            new TemplateColumn("birlik", false, false, List.of("dona", "metr"), "dona yoki metr. Bo'sh bo'lsa dona", "dona"),
            new TemplateColumn("tannarx", false, false, List.of(), "Bir birlik tannarxi, so'm. Miqdor 0 dan katta bo'lsa majburiy", "8000"),
            new TemplateColumn("narx *", true, false, List.of(), "Sotuv narxi, so'm", "12000"),
            new TemplateColumn("miqdor", false, false, List.of(), "Hozir do'kondagi qoldiq (boshlang'ich qoldiq). Dona uchun butun son. Bo'sh bo'lsa 0", "50"),
            new TemplateColumn("minQty", false, false, List.of(), "Minimal qoldiq: shundan kamaysa \"tugayapti\" deb ko'rsatiladi", "10"),
            new TemplateColumn("ta'minotchi", false, false, List.of(), "Ta'minotchi nomi. Bazada yo'q bo'lsa yaratiladi", "Elektro Optom"),
            new TemplateColumn("MXIK", false, true, List.of(), "17 ta raqam, fiskal chek uchun", "08544001001000000"),
            new TemplateColumn("qadoq kodi", false, true, List.of(), "O'lchov/qadoq kodi, 20 belgigacha, fiskal chek uchun", "1500123"),
            new TemplateColumn("QQS %", false, false, List.of(), "0 dan 100 gacha", "12"));

    private static final List<String> RULES = List.of(
            "Tovarlarni birinchi varaqqa, 2-qatordan boshlab kiriting. Sariq ustunlar majburiy.",
            "Bu varaq import qilinmaydi. Birinchi varaqdagi ustun nomlarini o'zgartirmang.",
            "Xato qatorlar saqlanmaydi, natijada qator raqami bilan ko'rsatiladi: ularni tuzatib, faylni qayta yuklash mumkin.",
            "Qayta yuklashda bor tovarning qoldig'i va tannarxi o'zgarmaydi, ular faqat kirim yoki tuzatish orqali o'zgaradi.",
            "Barcode, MXIK va qadoq kodi ustunlari matn formatida: boshqa fayldan nusxalaganda faqat qiymatni qo'ying (Paste Values).",
            "Tovar rasmi Excel orqali kiritilmaydi, u tovar kartasidan yuklanadi.");

    /** Baza ustunlari sig'imi: precision 18/scale 2 va 12/3. */
    private static final BigDecimal MAX_MONEY = new BigDecimal("1E16");
    private static final BigDecimal MAX_QTY = new BigDecimal("1E9");

    private final ExcelReader excel;
    private final BarcodeValidator barcodes;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final StockMovementRepository movements;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public byte[] template() {
        return excel.fullTemplate("Mahsulotlar", COLUMNS, RULES);
    }

    @Transactional
    public ImportResult importFile(MultipartFile file) {
        List<ExcelRow> rows = excel.read(file, "barcode", "nomi", "narx");
        List<RowError> errors = new ArrayList<>();
        List<RowError> warnings = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        int ok = 0;
        BigDecimal openingValue = BigDecimal.ZERO;
        for (ExcelRow r : rows) {
            try {
                Integer first = seen.putIfAbsent(r.get("barcode"), r.number());
                if (first != null) {
                    throw new IllegalArgumentException("barcode faylda takrorlangan (" + first + "-qatorda bor): " + r.get("barcode"));
                }
                openingValue = openingValue.add(importRow(r, warnings));
                ok++;
            } catch (RuntimeException e) {
                errors.add(new RowError(r.number(), e.getMessage()));
            }
        }
        if (openingValue.signum() > 0) {
            events.publishEvent(new OpeningBalanceEvent(BigDecimal.ZERO, MoneyUtils.round(openingValue), DateUtils.today()));
        }
        audit.log("IMPORT", "Product", null, "jami=" + rows.size() + ", saqlandi=" + ok + ", xato=" + errors.size());
        return new ImportResult(rows.size(), ok, errors, warnings);
    }

    /**
     * Baza xatosi butun tranzaksiyani (boshqa qatorlarni ham) bekor qiladi,
     * shuning uchun qator saqlashdan oldin to'liq tekshiriladi.
     * @return yangi boshlang'ich qoldiq qiymati
     */
    private BigDecimal importRow(ExcelRow r, List<RowError> warnings) {
        String barcode = r.get("barcode");
        barcodes.requireValid(barcode);
        String name = req(r.get("nomi"), "nomi");
        if (name.length() > 255) {
            throw new IllegalArgumentException("nomi 255 belgidan oshmasligi kerak");
        }
        Unit unit = parseUnit(r.get("birlik"));
        BigDecimal price = limit(number(r.get("narx"), "narx"), MAX_MONEY, "narx");
        BigDecimal cost = r.get("tannarx").isBlank() ? BigDecimal.ZERO : limit(number(r.get("tannarx"), "tannarx"), MAX_MONEY, "tannarx");
        BigDecimal qty = r.get("miqdor").isBlank() ? BigDecimal.ZERO : QtyUtils.scale(limit(number(r.get("miqdor"), "miqdor"), MAX_QTY, "miqdor"));
        BigDecimal minQty = r.get("minqty").isBlank() ? BigDecimal.ZERO : QtyUtils.scale(limit(number(r.get("minqty"), "minQty"), MAX_QTY, "minQty"));
        String category = r.get("kategoriya").isBlank() ? null : r.get("kategoriya");
        if (category != null && category.length() > 255) {
            throw new IllegalArgumentException("kategoriya 255 belgidan oshmasligi kerak");
        }
        String mxik = r.get("mxik");
        if (r.isNumeric("mxik")) {
            // Excel 15 xonadan keyingi raqamlarni nolga aylantiradi, bosh nol ham yo'qoladi
            throw new IllegalArgumentException("MXIK kodi son katakda buzilgan bo'lishi mumkin: katakni matn formatiga o'tkazib qayta yozing");
        }
        if (!mxik.isBlank() && !mxik.matches("\\d{17}")) {
            throw new IllegalArgumentException("MXIK kodi 17 ta raqamdan iborat bo'lishi kerak: " + mxik);
        }
        String packageCode = r.get("qadoqkodi");
        if (packageCode.length() > 20) {
            throw new IllegalArgumentException("qadoq kodi 20 belgidan oshmasligi kerak");
        }
        BigDecimal vat = r.get("qqs").isBlank() ? null : number(r.get("qqs"), "QQS");
        if (vat != null && vat.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("QQS 100% dan oshmasligi kerak");
        }
        String sup = r.get("taminotchi");
        if (sup.length() > 255) {
            throw new IllegalArgumentException("ta'minotchi nomi 255 belgidan oshmasligi kerak");
        }
        Product p = products.findByBarcode(barcode).orElse(null);
        if (p == null) {
            if (unit == Unit.DONA && qty.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("dona uchun miqdor butun bo'lishi kerak");
            }
            if (qty.signum() > 0 && MoneyUtils.round(cost).signum() <= 0) {
                throw new IllegalArgumentException("Tovar bor bo'lsa, tannarxini ham kiriting");
            }
        }
        // ta'minotchi faqat qator tekshiruvdan o'tgach yaratiladi
        Long supplierId = null;
        if (!sup.isBlank()) {
            supplierId = suppliers.findByName(sup).orElseGet(() -> suppliers.save(Supplier.builder().name(sup).build())).getId();
        }
        if (p != null) {
            p.setName(name);
            p.setPrice(MoneyUtils.round(price));
            if (category != null) {
                p.setCategory(category);
            }
            if (!r.get("birlik").isBlank()) {
                p.setUnit(unit);
            }
            if (!r.get("minqty").isBlank()) {
                p.setMinQty(minQty);
            }
            if (supplierId != null) {
                p.setSupplierId(supplierId);
            }
            applyFiscal(p, mxik, packageCode, vat);
            if (!r.get("miqdor").isBlank() && qty.compareTo(p.getQty()) != 0) {
                warnings.add(new RowError(r.number(), "Mavjud tovar miqdori o'zgartirilmadi (" + name + "): kirim yoki tuzatish orqali kiriting"));
            }
            return BigDecimal.ZERO;
        }
        p = products.save(Product.builder().name(name).barcode(barcode).category(category).unit(unit)
                .avgCost(MoneyUtils.round(cost)).lastCost(MoneyUtils.round(cost)).price(MoneyUtils.round(price))
                .qty(qty).minQty(minQty).supplierId(supplierId).active(true).build());
        applyFiscal(p, mxik, packageCode, vat);
        if (qty.signum() > 0) {
            movements.save(StockMovement.builder().productId(p.getId()).type(MovementType.OPENING).qty(qty)
                    .cost(p.getAvgCost()).createdAt(DateUtils.now()).build());
            return MoneyUtils.round(qty.multiply(p.getAvgCost()));
        }
        return BigDecimal.ZERO;
    }

    /** Bo'sh katak mavjud qiymatni o'zgartirmaydi. */
    private static void applyFiscal(Product p, String mxik, String packageCode, BigDecimal vat) {
        if (!mxik.isBlank()) {
            p.setMxikCode(mxik);
        }
        if (!packageCode.isBlank()) {
            p.setPackageCode(packageCode);
        }
        if (vat != null) {
            p.setVatPercent(MoneyUtils.round(vat));
        }
    }

    private static BigDecimal limit(BigDecimal v, BigDecimal max, String col) {
        if (v.compareTo(max) >= 0) {
            throw new IllegalArgumentException(col + " juda katta: " + v.toPlainString());
        }
        return v;
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
            String s = req(v, col).replaceAll("[\\s\\u00A0]", "");
            // 12,000 yoki 1,250.50 — minglik ajratgich; 12,5 — kasr vergul
            s = s.matches("\\d{1,3}(,\\d{3})+(\\.\\d+)?") ? s.replace(",", "") : s.replace(",", ".");
            BigDecimal n = new BigDecimal(s);
            if (n.signum() < 0) {
                throw new IllegalArgumentException(col + " manfiy bo'lmasligi kerak");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(col + " son emas: " + v);
        }
    }
}
