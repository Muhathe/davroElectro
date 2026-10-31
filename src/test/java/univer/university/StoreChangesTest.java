package univer.university;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Aralash to'lov, boshlang'ich miqdor, vozvrat qidiruvi, xodim ruxsatlari va Excel importi. */
class StoreChangesTest extends ApiTestSupport {

    private static final AtomicLong CODE = new AtomicLong(88_000_000_000L);

    private static String barcode() {
        return String.valueOf(CODE.incrementAndGet());
    }

    private void assertBooksBalanced() throws Exception {
        assertThat(ok("GET", "/api/accounting/trial-balance", "davron", null).get("balanced").asBoolean()).isTrue();
        assertThat(ok("GET", "/api/accounting/balance-sheet", "davron", null).get("balanced").asBoolean()).isTrue();
    }

    private long customer(String name) throws Exception {
        return ok("POST", "/api/customers", "davron", Map.of("name", name, "type", "USTA", "discountPercent", 0)).get("id").asLong();
    }

    private static Map<String, Object> mixed(long productId, String qty, Long customerId, List<Map<String, Object>> payments) {
        Map<String, Object> m = new HashMap<>();
        m.put("items", List.of(Map.of("productId", productId, "qty", new BigDecimal(qty))));
        m.put("payments", payments);
        if (customerId != null) {
            m.put("customerId", customerId);
        }
        return m;
    }

    private BigDecimal debt(long customerId) throws Exception {
        return ok("GET", "/api/customers/" + customerId + "/debt", "davron", null).get("debt").decimalValue();
    }

    private BigDecimal cash(String field) throws Exception {
        return ok("GET", "/api/pos/cash/balance", "davron", null).get(field).decimalValue();
    }

    // ---------- aralash to'lov ----------

    @Test
    void mixedPaymentSplitsCashAndLeavesRestAsDebt() throws Exception {
        long id = newProduct("Aralash tovar", "100000", "10", "60000");
        long customerId = customer("Aralash mijoz");
        BigDecimal naqd = cash("naqd");
        BigDecimal karta = cash("karta");

        JsonNode sale = ok("POST", "/api/pos/checkout", "sardor", mixed(id, "1", customerId, List.of(
                Map.of("method", "NAQD", "amount", 30000), Map.of("method", "KARTA", "amount", 20000),
                Map.of("method", "CLICK", "amount", 10000))));

        assertThat(sale.get("paymentType").asText()).isEqualTo("ARALASH");
        assertThat(sale.get("creditAmount").decimalValue()).isEqualByComparingTo("40000");
        assertThat(sale.get("payments").size()).isEqualTo(4);
        assertThat(cash("naqd").subtract(naqd)).isEqualByComparingTo("30000");
        assertThat(cash("karta").subtract(karta)).isEqualByComparingTo("30000");
        assertThat(debt(customerId)).isEqualByComparingTo("40000");
        assertThat(ok("GET", "/api/pos/sales/history?paymentType=CLICK", "davron", null).get("count").asLong()).isGreaterThan(0);
        assertBooksBalanced();

        // to'lov qarzni yopadi
        ok("POST", "/api/customers/" + customerId + "/payments", "davron", Map.of("amount", 40000, "method", "NAQD"));
        assertThat(debt(customerId)).isEqualByComparingTo("0");
        assertBooksBalanced();
    }

    @Test
    void mixedPaymentRules() throws Exception {
        long id = newProduct("Qoida tovar", "100000", "10", "60000");
        // nasiya qismi bor, mijoz yo'q
        assertThat(call("POST", "/api/pos/checkout", "sardor", mixed(id, "1", null,
                List.of(Map.of("method", "NAQD", "amount", 50000)))).getResponse().getStatus()).isEqualTo(422);
        // chekdan ko'p to'lov
        assertThat(call("POST", "/api/pos/checkout", "sardor", mixed(id, "1", null, List.of(
                Map.of("method", "NAQD", "amount", 90000), Map.of("method", "KARTA", "amount", 20000))))
                .getResponse().getStatus()).isEqualTo(422);
        assertThat(productQty(id)).isEqualByComparingTo("10");

        // to'liq to'langan aralash chek: mijozsiz, qaytim naqd qismidan
        Map<String, Object> req = mixed(id, "1", null, List.of(
                Map.of("method", "NAQD", "amount", 40000), Map.of("method", "KARTA", "amount", 60000)));
        req.put("receivedAmount", 50000);
        JsonNode sale = ok("POST", "/api/pos/checkout", "sardor", req);
        assertThat(sale.get("change").decimalValue()).isEqualByComparingTo("10000");
        assertThat(sale.get("creditAmount").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void mixedReturnIsPaidFromCashOnly() throws Exception {
        long id = newProduct("Vozvrat tovar", "100000", "10", "60000");
        long customerId = customer("Vozvrat mijoz");
        JsonNode sale = ok("POST", "/api/pos/checkout", "sardor", mixed(id, "2", customerId, List.of(
                Map.of("method", "KARTA", "amount", 150000), Map.of("method", "CLICK", "amount", 20000))));
        BigDecimal naqd = cash("naqd");
        BigDecimal karta = cash("karta");

        JsonNode ret = ok("POST", "/api/returns", "sardor", Map.of("checkNo", sale.get("checkNo").asText(), "reason", "NOSOZ",
                "items", List.of(Map.of("productId", id, "qty", 1))));

        assertThat(ret.get("toDebt").asBoolean()).isFalse();
        assertThat(naqd.subtract(cash("naqd"))).isEqualByComparingTo("100000");
        assertThat(cash("karta")).isEqualByComparingTo(karta);
        assertThat(debt(customerId)).isEqualByComparingTo("30000");
        assertBooksBalanced();
    }

    // ---------- boshlang'ich miqdor ----------

    @Test
    void newProductRequiresQtyAndPostsOpeningStock() throws Exception {
        Map<String, Object> base = new HashMap<>(Map.of("name", "Yangi avtomat", "barcode", barcode(), "unit", "DONA",
                "price", 40000, "minQty", 0));
        assertThat(call("POST", "/api/products", "davron", base).getResponse().getStatus()).isEqualTo(400);

        base.put("initialQty", 15);
        assertThat(call("POST", "/api/products", "davron", base).getResponse().getStatus()).isEqualTo(422);
        base.put("initialQty", 1.5);
        base.put("initialCost", 28000);
        assertThat(call("POST", "/api/products", "davron", base).getResponse().getStatus()).isEqualTo(422);

        base.put("initialQty", 15);
        JsonNode p = ok("POST", "/api/products", "davron", base);
        assertThat(p.get("qty").decimalValue()).isEqualByComparingTo("15");
        assertThat(p.get("avgCost").decimalValue()).isEqualByComparingTo("28000");
        JsonNode moves = ok("GET", "/api/stock/movements?productId=" + p.get("id").asLong(), "davron", null);
        assertThat(moves.get(0).get("type").asText()).isEqualTo("OPENING");
        assertBooksBalanced();
    }

    // ---------- vozvrat qidiruvi ----------

    @Test
    void returnSearchByCheckSellerAndProduct() throws Exception {
        long id = newProduct("Qidiruv unikal tovar", "5000", "10", "3000");
        JsonNode sale = ok("POST", "/api/pos/checkout", "jasur", Map.of("paymentType", "NAQD",
                "items", List.of(Map.of("productId", id, "qty", 2))));
        String checkNo = sale.get("checkNo").asText();
        long seller = sale.get("userId").asLong();

        JsonNode byProduct = ok("GET", "/api/returns/sales/search?product=unikal", "sardor", null);
        assertThat(byProduct.get("count").asLong()).isEqualTo(1);
        assertThat(byProduct.get("items").get(0).get("checkNo").asText()).isEqualTo(checkNo);
        assertThat(byProduct.get("items").get(0).get("cashierName").asText()).contains("Jasur");
        assertThat(ok("GET", "/api/returns/sales/search?checkNo=" + checkNo.substring(4), "sardor", null).get("count").asLong()).isGreaterThan(0);
        assertThat(ok("GET", "/api/returns/sales/search?product=unikal&sellerId=" + seller, "sardor", null).get("count").asLong()).isEqualTo(1);
        assertThat(ok("GET", "/api/returns/sales/search?product=unikal&sellerId=1", "sardor", null).get("count").asLong()).isZero();
        assertThat(ok("GET", "/api/returns/sellers", "sardor", null).size()).isGreaterThan(0);

        ok("POST", "/api/returns", "sardor", Map.of("checkNo", checkNo, "reason", "NOSOZ",
                "items", List.of(Map.of("productId", id, "qty", 1))));
        JsonNode history = ok("GET", "/api/returns/search?product=unikal", "sardor", null);
        assertThat(history.get("count").asLong()).isEqualTo(1);
        assertThat(history.get("total").decimalValue()).isEqualByComparingTo("5000");
        assertThat(history.get("items").get(0).get("userName").asText()).contains("Sardor");
    }

    // ---------- xodim ruxsatlari ----------

    @Test
    void permissionsCanDifferPerEmployee() throws Exception {
        long id = ok("POST", "/api/users", "admin", Map.of("username", "kassir_test", "password", PASSWORD,
                "fullName", "Kassir Test", "role", "SOTUVCHI")).get("id").asLong();
        long productId = newProduct("Ruxsat tovar", "1000", "5", "500");
        Map<String, Object> sale = Map.of("paymentType", "NAQD", "items", List.of(Map.of("productId", productId, "qty", 1)));
        Map<String, Object> expense = Map.of("categoryId", 5, "amount", 1000, "paymentMethod", "NAQD", "note", "test");
        assertThat(call("POST", "/api/pos/expenses", "kassir_test", expense).getResponse().getStatus()).isEqualTo(403);

        // shu xodimga harajat ochiladi, sotuv yopiladi; boshqa sotuvchi o'zgarmaydi
        ok("PUT", "/api/roles/users/" + id + "/permissions", "davron", List.of(
                Map.of("module", "KASSA_HARAJAT", "canView", true, "canCreate", true, "canEdit", false, "canDelete", false),
                Map.of("module", "KASSA", "canView", true, "canCreate", false, "canEdit", false, "canDelete", false)));

        assertThat(call("POST", "/api/pos/expenses", "kassir_test", expense).getResponse().getStatus()).isEqualTo(201);
        assertThat(call("POST", "/api/pos/checkout", "kassir_test", sale).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("POST", "/api/pos/checkout", "sardor", sale).getResponse().getStatus()).isEqualTo(201);
        assertThat(call("POST", "/api/pos/expenses", "sardor", expense).getResponse().getStatus()).isEqualTo(403);

        JsonNode me = ok("GET", "/api/auth/me", "kassir_test", null);
        boolean canExpense = false;
        for (JsonNode p : me.get("permissions")) {
            canExpense |= "KASSA_HARAJAT".equals(p.get("module").asText()) && p.get("canCreate").asBoolean();
        }
        assertThat(canExpense).isTrue();

        // rol ruxsatiga qaytarish
        ok("DELETE", "/api/roles/users/" + id + "/permissions", "davron", null);
        assertThat(call("POST", "/api/pos/expenses", "kassir_test", expense).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("POST", "/api/pos/checkout", "kassir_test", sale).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void permissionEditGuards() throws Exception {
        long adminId = ok("GET", "/api/auth/me", "admin", null).get("user").get("id").asLong();
        long egasiId = ok("GET", "/api/auth/me", "davron", null).get("user").get("id").asLong();
        List<Map<String, Object>> row = List.of(Map.of("module", "BUXGALTERIYA", "canView", true, "canCreate", false, "canEdit", false, "canDelete", false));
        assertThat(call("PUT", "/api/roles/users/" + adminId + "/permissions", "admin", row).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("PUT", "/api/roles/users/" + egasiId + "/permissions", "admin", row).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("PUT", "/api/roles/users/" + adminId + "/permissions", "sardor", row).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void capabilitiesCoverEveryModuleAndOldRoleRulesStay() throws Exception {
        assertThat(ok("GET", "/api/roles/capabilities", "admin", null).size())
                .isEqualTo(univer.university.entity.enums.Module.values().length);
        assertThat(ok("GET", "/api/roles/matrix", "davron", null).size())
                .isEqualTo(univer.university.entity.enums.Module.values().length
                        * univer.university.entity.enums.Role.values().length);
        // avvalgi hasRole/hasAnyRole qoidalari matritsaga o'tgan, xulq o'zgarmagan
        assertThat(call("PUT", "/api/settings/store", "admin", Map.of("name", "X")).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("POST", "/api/print/test", "sardor", null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("POST", "/api/pos/sales/1/fiscalize", "sardor", null).getResponse().getStatus()).isEqualTo(403);
    }

    // ---------- Excel ----------

    private byte[] workbook(Consumer<Sheet> fill) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.accept(wb.createSheet("1"));
            wb.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode upload(String url, byte[] data) throws Exception {
        MvcResult r = mvc.perform(multipart(url).file(new MockMultipartFile("file", "f.xlsx", "application/octet-stream", data))
                .header("Authorization", "Bearer " + token("davron"))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString());
    }

    private static void header(Sheet s, String... names) {
        Row h = s.createRow(0);
        for (int i = 0; i < names.length; i++) {
            if (!names[i].isEmpty()) {
                h.createCell(i).setCellValue(names[i]);
            }
        }
    }

    @Test
    void excelReadsNumericCellsExactlyAndKeepsColumnsAligned() throws Exception {
        String code = "4" + (CODE.incrementAndGet() + 100_000_000_000L);
        JsonNode res = upload("/api/products/import", workbook(s -> {
            // sarlavhadagi bo'sh katak ustunlarni surmasligi kerak
            header(s, "barcode", "nomi", "", "narx", "tannarx", "miqdor");
            CellStyle thousands = s.getWorkbook().createCellStyle();
            thousands.setDataFormat(s.getWorkbook().createDataFormat().getFormat("#,##0"));
            Row r = s.createRow(1);
            r.createCell(0).setCellValue(Double.parseDouble(code)); // son formatidagi barcode
            r.createCell(1).setCellValue("Excel son tovar");
            r.createCell(3).setCellValue(12000);
            r.getCell(3).setCellStyle(thousands);
            r.createCell(4).setCellFormula("4000*2");
            r.createCell(5).setCellValue(1500);
            r.getCell(5).setCellStyle(thousands);
            s.getWorkbook().getCreationHelper().createFormulaEvaluator().evaluateAll();
        }));
        assertThat(res.get("errors").size()).as(res.toString()).isZero();
        JsonNode p = ok("GET", "/api/products/barcode/" + code, "davron", null);
        assertThat(p.get("price").decimalValue()).isEqualByComparingTo("12000");
        assertThat(p.get("qty").decimalValue()).isEqualByComparingTo("1500");
        assertThat(p.get("avgCost").decimalValue()).isEqualByComparingTo("8000");
        assertBooksBalanced();
    }

    @Test
    void excelBlankCellsDoNotWipeExistingProduct() throws Exception {
        String code = barcode();
        JsonNode created = ok("POST", "/api/products", "davron", Map.of("name", "Mavjud tovar", "barcode", code, "category", "Lampalar",
                "unit", "METR", "price", 1000, "minQty", 7, "initialQty", 3, "initialCost", 500));
        JsonNode res = upload("/api/products/import", workbook(s -> {
            header(s, "barcode", "nomi", "kategoriya", "birlik", "narx", "miqdor", "minQty");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue(code);
            r.createCell(1).setCellValue("Mavjud tovar");
            r.createCell(4).setCellValue("2,000");
            r.createCell(5).setCellValue(99);
        }));
        assertThat(res.get("errors").size()).as(res.toString()).isZero();
        assertThat(res.get("warnings").size()).isEqualTo(1);
        JsonNode p = ok("GET", "/api/products/" + created.get("id").asLong(), "davron", null);
        assertThat(p.get("price").decimalValue()).isEqualByComparingTo("2000");
        assertThat(p.get("category").asText()).isEqualTo("Lampalar");
        assertThat(p.get("unit").asText()).isEqualTo("METR");
        assertThat(p.get("minQty").decimalValue()).isEqualByComparingTo("7");
        assertThat(p.get("qty").decimalValue()).isEqualByComparingTo("3");
    }

    @Test
    void excelStockImportRejectsOnlyBadRowsAndRoundsLikeIncoming() throws Exception {
        long a = newProduct("Kirim A", "1000", "1", "500");
        long b = newProduct("Kirim B", "1000", "1", "500");
        String codeA = ok("GET", "/api/products/" + a, "davron", null).get("barcode").asText();
        String codeB = ok("GET", "/api/products/" + b, "davron", null).get("barcode").asText();
        JsonNode res = upload("/api/stock/import", workbook(s -> {
            header(s, "barcode", "miqdor", "tannarx");
            Object[][] rows = {{codeA, 3d, 0.335}, {codeB, 3d, 0.335}, {codeB, 1.5, 100d}};
            for (int i = 0; i < rows.length; i++) {
                Row r = s.createRow(i + 1);
                r.createCell(0).setCellValue((String) rows[i][0]);
                r.createCell(1).setCellValue((Double) rows[i][1]);
                r.createCell(2).setCellValue((Double) rows[i][2]);
            }
        }));
        assertThat(res.get("imported").asInt()).isEqualTo(2);
        assertThat(res.get("errors").size()).isEqualTo(1);
        assertThat(productQty(b)).isEqualByComparingTo("4");
        assertBooksBalanced();
    }

    private byte[] productTemplate() throws Exception {
        return mvc.perform(get("/api/products/import/template").header("Authorization", "Bearer " + token("davron")))
                .andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    void singleMethodPartialPaymentLeavesRestOnCredit() throws Exception {
        long id = newProduct("Qisman to'lov", "10000", "5", "6000");
        long customerId = customer("Qisman mijoz");
        JsonNode sale = ok("POST", "/api/pos/checkout", "sardor", mixed(id, "1", customerId, List.of(
                Map.of("method", "KARTA", "amount", 4000))));
        assertThat(sale.get("paymentType").asText()).isEqualTo("ARALASH");
        assertThat(sale.get("creditAmount").decimalValue()).isEqualByComparingTo("6000");
        assertBooksBalanced();
    }

    /** Haqiqiy shablon: foydalanuvchi uni yuklab olib to'ldirgandek. */
    private byte[] filledTemplate(Consumer<Sheet> fill) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(productTemplate())); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.accept(wb.getSheetAt(0));
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void fillRow(Sheet s, int index, String... cells) {
        Row r = s.createRow(index);
        for (int i = 0; i < cells.length; i++) {
            if (!cells[i].isEmpty()) {
                r.createCell(i).setCellValue(cells[i]);
            }
        }
    }

    @Test
    void excelTemplateHasNoSampleRowAndKeepsCodesAsText() throws Exception {
        byte[] template = productTemplate();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            Sheet s = wb.getSheetAt(0);
            assertThat(s.getLastRowNum()).isZero();
            assertThat(s.getRow(0).getLastCellNum()).isEqualTo((short) 12);
            assertThat(s.getColumnStyle(0).getDataFormatString()).isEqualTo("@");
            assertThat(s.getColumnStyle(9).getDataFormatString()).isEqualTo("@");
            assertThat(s.getColumnStyle(10).getDataFormatString()).isEqualTo("@");
            assertThat(s.getDataValidations()).hasSize(1);
            assertThat(wb.getSheetAt(1).getRow(1).getCell(3).getStringCellValue()).isEqualTo("4780000000011");
        }
        // shablonni to'ldirmasdan yuklash hech narsa saqlamaydi
        assertThat(upload("/api/products/import", template).get("total").asInt()).isZero();
    }

    @Test
    void excelTemplateCarriesEveryNewProductField() throws Exception {
        String code = barcode();
        String supplier = "Shablon ta'minotchi " + code;
        JsonNode res = upload("/api/products/import", filledTemplate(s -> fillRow(s, 1, code, "To'liq tovar", "Kabellar", "metr", "8000", "12000",
                "2.5", "10", supplier, "08544001001000000", "1500123", "12")));
        assertThat(res.get("imported").asInt()).as(res.toString()).isEqualTo(1);
        JsonNode p = ok("GET", "/api/products/barcode/" + code, "davron", null);
        assertThat(p.get("name").asText()).isEqualTo("To'liq tovar");
        assertThat(p.get("category").asText()).isEqualTo("Kabellar");
        assertThat(p.get("unit").asText()).isEqualTo("METR");
        assertThat(p.get("avgCost").decimalValue()).isEqualByComparingTo("8000");
        assertThat(p.get("lastCost").decimalValue()).isEqualByComparingTo("8000");
        assertThat(p.get("price").decimalValue()).isEqualByComparingTo("12000");
        assertThat(p.get("qty").decimalValue()).isEqualByComparingTo("2.5");
        assertThat(p.get("minQty").decimalValue()).isEqualByComparingTo("10");
        assertThat(p.get("supplierId").asLong()).isPositive();
        assertThat(p.get("mxikCode").asText()).isEqualTo("08544001001000000");
        assertThat(p.get("packageCode").asText()).isEqualTo("1500123");
        assertThat(p.get("vatPercent").decimalValue()).isEqualByComparingTo("12");
        assertThat(p.get("active").asBoolean()).isTrue();
        assertBooksBalanced();
    }

    @Test
    void excelBadRowsAreReportedAndGoodRowsStillSaved() throws Exception {
        String good = barcode();
        String dup = barcode();
        String noCost = barcode();
        String longPackage = barcode();
        String hugePrice = barcode();
        String numericMxik = barcode();
        JsonNode res = upload("/api/products/import", filledTemplate(s -> {
            fillRow(s, 1, good, "Yaxshi tovar", "", "", "1000", "2000", "5");
            fillRow(s, 2, dup, "Birinchi nom", "", "", "", "2000");
            fillRow(s, 3, dup, "Boshqa tovar", "", "", "", "3000");
            fillRow(s, 4, noCost, "Tannarxsiz", "", "", "", "2000", "5");
            fillRow(s, 5, longPackage, "Uzun kod", "", "", "", "2000", "", "", "", "", "123456789012345678901");
            fillRow(s, 6, hugePrice, "Katta narx", "", "", "", "99999999999999999999");
            fillRow(s, 7, numericMxik, "Son MXIK", "", "", "", "2000");
            s.getRow(7).createCell(9).setCellValue(12345678901234500000d);
        }));
        assertThat(res.get("imported").asInt()).as(res.toString()).isEqualTo(2);
        assertThat(res.get("errors").size()).as(res.toString()).isEqualTo(5);
        assertThat(ok("GET", "/api/products/barcode/" + dup, "davron", null).get("name").asText()).isEqualTo("Birinchi nom");
        assertThat(ok("GET", "/api/products/barcode/" + good, "davron", null).get("qty").decimalValue()).isEqualByComparingTo("5");
        assertBooksBalanced();
    }

    @Test
    void excelMissingColumnIsReported() throws Exception {
        byte[] data = workbook(s -> {
            header(s, "barcode", "nomi");
            s.createRow(1).createCell(0).setCellValue("x");
        });
        MvcResult r = mvc.perform(multipart("/api/products/import").file(new MockMultipartFile("file", "f.xlsx", "application/octet-stream", data))
                .header("Authorization", "Bearer " + token("davron"))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("narx");
    }
}
