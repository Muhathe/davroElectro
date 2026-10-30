package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import univer.university.component.ExcelReader;
import univer.university.component.ExcelReader.ExcelRow;
import univer.university.component.MoneyUtils;
import univer.university.component.QtyUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        List<ExcelRow> rows = excel.read(file, HEADERS.toArray(new String[0]));
        List<RowError> errors = new ArrayList<>();
        List<StockRequests.IncomingItem> items = new ArrayList<>();
        // kirim summasi StockService bilan bir xil: tovar kesimida yaxlitlanadi
        Map<Long, BigDecimal> values = new HashMap<>();
        for (ExcelRow r : rows) {
            try {
                Product p = products.findByBarcode(r.get("barcode"))
                        .orElseThrow(() -> new IllegalArgumentException("barcode bazada yo'q: " + r.get("barcode")));
                BigDecimal qty = ProductImportService.number(r.get("miqdor"), "miqdor");
                BigDecimal cost = ProductImportService.number(r.get("tannarx"), "tannarx");
                QtyUtils.normalize(p, qty);
                items.add(new StockRequests.IncomingItem(p.getId(), qty, cost));
                values.merge(p.getId(), qty.multiply(cost), BigDecimal::add);
            } catch (RuntimeException e) {
                errors.add(new RowError(r.number(), e.getMessage()));
            }
        }
        if (!items.isEmpty()) {
            BigDecimal total = values.values().stream().map(MoneyUtils::round).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pay = paid == null ? total : paid;
            IncomingResult res = stock.incoming(new StockRequests.Incoming(supplierId, pay,
                    method == null ? PayMethod.NAQD : method, items));
            if (res == null) {
                throw new BusinessException("Kirim saqlanmadi");
            }
        }
        return new ImportResult(rows.size(), items.size(), errors, List.of());
    }
}
