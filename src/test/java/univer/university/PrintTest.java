package univer.university;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Chek printeri navbati: avtomatik navbat, agent olishi, ESC/POS matni, done/failed. */
class PrintTest extends ApiTestSupport {

    private static final String AGENT = "test-agent-token";

    private MvcResult poll(String token) throws Exception {
        var req = get("/api/print-agent/jobs/next?wait=0").header("X-Agent-Printer", "Xprinter XP-58");
        if (token != null) {
            req.header("X-Agent-Token", token);
        }
        return mvc.perform(req).andReturn();
    }

    /** Navbatdagi boshqa testlar cheklarini o'tkazib, kerakli vazifani topadi. */
    private JsonNode takeUntil(String type, long refId) throws Exception {
        for (int i = 0; i < 500; i++) {
            MvcResult r = poll(AGENT);
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            JsonNode job = json.readTree(r.getResponse().getContentAsString());
            if (job.get("type").asText().equals(type) && (refId == 0 || jobRef(job.get("id").asLong()) == refId)) {
                return job;
            }
            mvc.perform(post("/api/print-agent/jobs/" + job.get("id").asLong() + "/done").header("X-Agent-Token", AGENT));
        }
        throw new AssertionError("vazifa topilmadi");
    }

    private long jobRef(long jobId) throws Exception {
        for (JsonNode j : ok("GET", "/api/print/jobs", "davron", null)) {
            if (j.get("id").asLong() == jobId) {
                return j.has("refId") ? j.get("refId").asLong() : 0;
            }
        }
        return -1;
    }

    private static String text(JsonNode job) {
        return new String(Base64.getDecoder().decode(job.get("data").asText()), StandardCharsets.US_ASCII);
    }

    @Test
    void agentNeedsToken() throws Exception {
        assertThat(poll(null).getResponse().getStatus()).isEqualTo(401);
        assertThat(poll("notogri").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void saleIsPrintedAutomaticallyAndCanBeReprinted() throws Exception {
        long id = newProduct("Chop lampa", "12500", "5", "8000");
        JsonNode sale = ok("POST", "/api/pos/checkout", "sardor", Map.of("paymentType", "NAQD",
                "receivedAmount", 30000, "items", List.of(Map.of("productId", id, "qty", 2))));
        long saleId = sale.get("id").asLong();

        JsonNode job = takeUntil("SALE", saleId);
        String receipt = text(job);
        assertThat(receipt).startsWith("\u001B@");
        assertThat(receipt).contains(sale.get("checkNo").asText(), "Chop lampa", "25 000", "Qaytim:", "5 000",
                "Bu fiskal chek emas");
        assertThat(ok("GET", "/api/print/status", "sardor", null).get("agentOnline").asBoolean()).isTrue();

        mvc.perform(post("/api/print-agent/jobs/" + job.get("id").asLong() + "/done").header("X-Agent-Token", AGENT));
        JsonNode reprint = ok("POST", "/api/pos/sales/" + saleId + "/print", "sardor", null);
        assertThat(reprint.get("status").asText()).isEqualTo("NEW");
        assertThat(call("POST", "/api/pos/sales/999999/print", "sardor", null).getResponse().getStatus()).isEqualTo(404);

        // printer xatosi: 3 urinishgacha qayta navbat, keyin FAILED
        JsonNode again = takeUntil("SALE", saleId);
        long jobId = again.get("id").asLong();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/print-agent/jobs/" + jobId + "/failed").header("X-Agent-Token", AGENT)
                    .contentType("application/json").content("{\"error\":\"Qog'oz tugagan\"}"));
            if (i < 2) {
                assertThat(takeUntil("SALE", saleId).get("id").asLong()).isEqualTo(jobId);
            }
        }
        String status = null;
        for (JsonNode j : ok("GET", "/api/print/jobs", "davron", null)) {
            if (j.get("id").asLong() == jobId) {
                status = j.get("status").asText();
            }
        }
        assertThat(status).isEqualTo("FAILED");
    }

    @Test
    void testPageOnlyForOwnerOrAdmin() throws Exception {
        assertThat(call("POST", "/api/print/test", "sardor", null).getResponse().getStatus()).isEqualTo(403);
        ok("POST", "/api/print/test", "admin", null);
        assertThat(text(takeUntil("TEST", 0))).contains("TEST CHEK");
    }
}
