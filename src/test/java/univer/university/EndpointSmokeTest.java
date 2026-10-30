package univer.university;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

/** Har bir GET endpoint EGASI uchun ishlaydi (200) — Swagger'dagi ro'yxat bilan bir xil. */
class EndpointSmokeTest extends ApiTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/auth/me", "/api/settings/store", "/api/pos/sales?fiscalStatus=PENDING", "/api/print/terminals",
            "/api/print/status", "/api/print/jobs",
            "/api/users", "/api/users/1", "/api/roles/matrix", "/api/audit-logs",
            "/api/expense-categories", "/api/expenses",
            "/api/customers", "/api/customers/1", "/api/customers/1/debt", "/api/customers/1/payments",
            "/api/debts/customers", "/api/debts/suppliers",
            "/api/finance/summary", "/api/finance/plan", "/api/finance/plan-fact", "/api/finance/forecast",
            "/api/finance/daily", "/api/finance/recurring",
            "/api/accounting/journal", "/api/accounting/trial-balance", "/api/accounting/profit-loss",
            "/api/accounting/cash-book", "/api/accounting/accounts", "/api/accounting/balance-sheet",
            "/api/staff/stats",
            "/api/products", "/api/products/1", "/api/products/categories", "/api/products/barcode/4780000000011",
            "/api/products/import/template",
            "/api/suppliers", "/api/suppliers/1",
            "/api/pos/cash/balance", "/api/pos/sales", "/api/pos/sales/history",
            "/api/returns", "/api/returns/search?checkNo=CHK&product=lampa", "/api/returns/sellers",
            "/api/returns/sales/search?checkNo=CHK&sellerId=1&product=lampa&from=2020-01-01",
            "/api/roles/capabilities", "/api/roles/users/2/permissions",
            "/api/stock", "/api/stock/movements", "/api/stock/order-suggestions", "/api/stock/purchases",
            "/api/stock/import/template"
    })
    void getEndpointsRespond(String url) throws Exception {
        MvcResult r = call("GET", url, "davron", null);
        assertThat(r.getResponse().getStatus()).as(url + " -> " + r.getResponse().getContentAsString()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products/999999", "/api/users/999999", "/api/pos/sales/999999",
            "/api/stock/purchases/999999", "/api/returns/999999", "/api/expenses/999999", "/api/suppliers/999999"})
    void missingEntityGives404(String url) throws Exception {
        MvcResult r = call("GET", url, "davron", null);
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
        assertThat(json.readTree(r.getResponse().getContentAsString()).get("path").asText()).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/quick-insight"})
    void aiWithoutKeyGives503(String url) throws Exception {
        assertThat(call("GET", url, "davron", null).getResponse().getStatus()).isEqualTo(503);
    }
}
