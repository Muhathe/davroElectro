package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import univer.university.component.ExcelReader;
import univer.university.component.ExcelReader.ExcelRow;
import univer.university.dto.request.StockRequests;
import univer.university.dto.response.ProductResponses.ImportResult;
import univer.university.dto.response.ProductResponses.IncomingResult;
import univer.university.dto.response.ProductResponses.RowError;
import univer.university.entity.Product;
import univer.university.entity.enums.PayMethod;
import univer.university.exception.BusinessException;
import univer.university.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Sklad kirimi importi. Shablon: barcode, miqdor, tannarx. Tovar avval mavjud bo'lishi kerak.
 * Xatosiz qatorlar bitta kirim (Purchase) bo'lib saqlanadi, xatolilari ro'yxat bilan qaytariladi.
 * To'lov: paid (default — to'liq), method (default NAQD), supplierId (qarz bo'lsa majburiy).
 */
@Service
@RequiredArgsConstructor
public class StockImportService {

    public static final List<String> HEADERS = List.of("barcode", "miqdor", "tannarx");

    private final ExcelReader excel;
    private final ProductRepository products;
    private final StockService stock;

    public byte[] template() {
        return excel.template("Kirim", HEADERS, List.of("4780000000011", "20", "8000"));
    }

    @Transactional
    public ImportResult importFile(MultipartFile file, Long supplierId, BigDecimal paid, PayMethod method) {
        List<ExcelRow> rows = excel.read(file);
        List<RowError> errors = new ArrayList<>();
        List<StockRequests.IncomingItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ExcelRow r : rows) {
            try {
                Product p = products.findByBarcode(r.get("barcode"))
                        .orElseThrow(() -> new IllegalArgumentException("barcode bazada yo'q: " + r.get("barcode")));
                BigDecimal qty = ProductImportService.number(r.get("miqdor"), "miqdor");
                BigDecimal cost = ProductImportService.number(r.get("tannarx"), "tannarx");
                if (qty.signum() <= 0) {
                    throw new IllegalArgumentException("miqdor 0 dan katta bo'lishi kerak");
                }
                items.add(new StockRequests.IncomingItem(p.getId(), qty, cost));
                total = total.add(qty.multiply(cost));
            } catch (RuntimeException e) {
                errors.add(new RowError(r.number(), e.getMessage()));
            }
        }
        if (!items.isEmpty()) {
            BigDecimal pay = paid == null ? total.setScale(2, java.math.RoundingMode.HALF_UP) : paid;
            IncomingResult res = stock.incoming(new StockRequests.Incoming(supplierId, pay,
                    method == null ? PayMethod.NAQD : method, items));
            if (res == null) {
                throw new BusinessException("Kirim saqlanmadi");
            }
        }
        return new ImportResult(rows.size(), items.size(), errors);
    }
}
