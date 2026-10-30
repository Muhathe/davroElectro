package univer.university;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** TZ 23-band (Definition of Done) bo'yicha asosiy biznes oqimlari. */
class BusinessFlowTest extends ApiTestSupport {

    private static Map<String, Object> checkout(long productId, String qty, String paymentType, Long customerId) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("paymentType", paymentType);
        m.put("items", List.of(Map.of("productId", productId, "qty", new BigDecimal(qty))));
        if (customerId != null) {
            m.put("customerId", customerId);
        }
        return m;
    }

    private void assertBooksBalanced() throws Exception {
        assertThat(ok("GET", "/api/accounting/trial-balance", "davron", null).get("balanced").asBoolean()).isTrue();
        assertThat(ok("GET", "/api/accounting/balance-sheet", "davron", null).get("balanced").asBoolean()).isTrue();
    }

    @Test
    void cashSaleMovesStockCashAndJournal() throws Exception {
        long id = newProduct("Test lampa", "12000", "10", "8000");
        BigDecimal cashBefore = cashTotal();

        JsonNode sale = ok("POST", "/api/pos/checkout", "sardor", checkout(id, "3", "NAQD", null));
        assertThat(sale.get("checkNo").asText()).startsWith("CHK-");
        assertThat(sale.get("total").decimalValue()).isEqualByComparingTo("36000");
        assertThat(productQty(id)).isEqualByComparingTo("7");
        assertThat(cashTotal().subtract(cashBefore)).isEqualByComparingTo("36000");

        JsonNode journal = ok("GET", "/api/accounting/journal", "davron", null);
        boolean found = false;
        for (JsonNode e : journal) {
            found |= "SALE".equals(e.get("sourceType").asText()) && e.get("sourceId").asLong() == sale.get("id").asLong();
        }
        assertThat(found).as("sotuv jurnalda").isTrue();
        assertThat(ok("GET", "/api/pos/sales/check/" + sale.get("checkNo").asText(), "sardor", null).get("id").asLong())
                .isEqualTo(sale.get("id").asLong());
        assertBooksBalanced();
    }

    @Test
    void expenseCancelRestoresCashAndWritesAudit() throws Exception {
        BigDecimal before = cashTotal();
        JsonNode e = ok("POST", "/api/pos/expenses", "buxgalter",
                Map.of("categoryId", 5, "amount", new BigDecimal("50000"), "paymentMethod", "NAQD", "note", "test"));
        assertThat(before.subtract(cashTotal())).isEqualByComparingTo("50000");

        assertThat(call("POST", "/api/expenses/" + e.get("id").asLong() + "/cancel", "buxgalter", Map.of("reason", ""))
                .getResponse().getStatus()).isEqualTo(400);
        ok("POST", "/api/expenses/" + e.get("id").asLong() + "/cancel", "buxgalter", Map.of("reason", "xato kiritildi"));
        assertThat(cashTotal()).isEqualByComparingTo(before);
        assertThat(call("POST", "/api/expenses/" + e.get("id").asLong() + "/cancel", "buxgalter", Map.of("reason", "yana"))
                .getResponse().getStatus()).isEqualTo(409);

        boolean audited = false;
        for (JsonNode a : ok("GET", "/api/audit-logs?entity=Expense", "davron", null)) {
            audited |= "CANCEL".equals(a.get("action").asText()) && a.get("entityId").asLong() == e.get("id").asLong();
        }
        assertThat(audited).isTrue();
        assertBooksBalanced();
    }

    @Test
    void partialReturnCannotExceedSoldQty() throws Exception {
        long id = newProduct("Test rozetka", "20000", "5", "15000");
        JsonNode sale = ok("POST", "/api/pos/checkout", "sardor", checkout(id, "2", "KARTA", null));
        String checkNo = sale.get("checkNo").asText();

        ok("POST", "/api/returns", "sardor", Map.of("checkNo", checkNo, "reason", "NOSOZ",
                "items", List.of(Map.of("productId", id, "qty", 1))));
        assertThat(productQty(id)).isEqualByComparingTo("4");
        assertThat(call("POST", "/api/returns", "sardor", Map.of("checkNo", checkNo, "reason", "NOSOZ",
                "items", List.of(Map.of("productId", id, "qty", 2)))).getResponse().getStatus()).isEqualTo(422);
        assertBooksBalanced();
    }

    @Test
    void creditSaleAndCustomerPayment() throws Exception {
        long id = newProduct("Test kabel", "10000", "20", "6000");
        long customerId = ok("POST", "/api/customers", "davron",
                Map.of("name", "Nasiya mijoz", "type", "USTA", "discountPercent", 0)).get("id").asLong();
        ok("POST", "/api/pos/checkout", "sardor", checkout(id, "5", "NASIYA", customerId));
        assertThat(ok("GET", "/api/customers/" + customerId + "/debt", "davron", null).get("debt").decimalValue())
                .isEqualByComparingTo("50000");

        assertThat(call("POST", "/api/customers/" + customerId + "/payments", "davron",
                Map.of("amount", 60000, "method", "NAQD")).getResponse().getStatus()).isEqualTo(422);
        ok("POST", "/api/customers/" + customerId + "/payments", "davron", Map.of("amount", 50000, "method", "CLICK"));
        assertThat(ok("GET", "/api/customers/" + customerId + "/debt", "davron", null).get("debt").decimalValue())
                .isEqualByComparingTo("0");
        assertThat(ok("GET", "/api/customers/" + customerId + "/payments", "davron", null).size()).isEqualTo(1);
        assertBooksBalanced();
    }

    @Test
    void parallelSalesNeverMakeStockNegative() throws Exception {
        long id = newProduct("Test avtomat", "40000", "5", "28000");
        token("sardor");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Integer>> results = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return call("POST", "/api/pos/checkout", "sardor", checkout(id, "1", "NAQD", null)).getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        int rejected = 0;
        for (Future<Integer> f : results) {
            int s = f.get(60, TimeUnit.SECONDS);
            if (s == 201) {
                created++;
            } else if (s == 422) {
                rejected++;
            }
        }
        pool.shutdown();
        assertThat(created).isEqualTo(5);
        assertThat(rejected).isEqualTo(5);
        assertThat(productQty(id)).isEqualByComparingTo("0");
    }
}
