package univer.university;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Chek printeri navbati: avtomatik navbat, agent olishi, ESC/POS matni, done/failed. */
class PrintTest extends ApiTestSupport {

    private static final String AGENT = "test-agent-token";

    private MvcResult poll(String token) throws Exception {
        return poll(token, "Xprinter XP-58");
    }

    private MvcResult poll(String token, String printer) throws Exception {
        var req = get("/api/print-agent/jobs/next?wait=0").header("X-Agent-Printer", printer)
                .header("X-Agent-Host", "KASSA-PC").header("X-Agent-Version", "v1");
        if (token != null) {
            req.header("X-Agent-Token", token);
        }
        return mvc.perform(req).andReturn();
    }

    /** Navbatdagi boshqa testlar cheklarini o'tkazib, kerakli vazifani topadi. */
    private JsonNode takeUntil(String type, long refId) throws Exception {
        return takeUntil(AGENT, type, refId);
    }

    private JsonNode takeUntil(String agent, String type, long refId) throws Exception {
        for (int i = 0; i < 500; i++) {
            MvcResult r = poll(agent);
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            JsonNode job = json.readTree(r.getResponse().getContentAsString());
            if (job.get("type").asText().equals(type) && (refId == 0 || jobRef(job.get("id").asLong()) == refId)) {
                return job;
            }
            mvc.perform(post("/api/print-agent/jobs/" + job.get("id").asLong() + "/done").header("X-Agent-Token", agent));
        }
        throw new AssertionError("vazifa topilmadi");
    }

    /** Agentga berilgan barcha vazifalarni bajarib, ularning id larini qaytaradi. */
    private List<Long> drain(String agent) throws Exception {
        List<Long> taken = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            MvcResult r = poll(agent);
            if (r.getResponse().getStatus() == 204) {
                return taken;
            }
            long id = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
            taken.add(id);
            mvc.perform(post("/api/print-agent/jobs/" + id + "/done").header("X-Agent-Token", agent));
        }
        throw new AssertionError("navbat tugamadi");
    }

    private static String tokenOf(String script) {
        int at = script.indexOf("DAVRON_TOKEN=") + "DAVRON_TOKEN=".length();
        return script.substring(at, script.indexOf('"', at));
    }

    /** Kassa yaratadi: {id, agent tokeni}. */
    private String[] newTerminal(String name) throws Exception {
        JsonNode inst = ok("POST", "/api/print/terminals", "davron", Map.of("name", name));
        return new String[]{inst.get("terminal").get("id").asText(), tokenOf(inst.get("script").asText())};
    }

    private JsonNode agentGet(String url, String token) throws Exception {
        return json.readTree(mvc.perform(get(url).header("X-Agent-Token", token)).andReturn().getResponse()
                .getContentAsString());
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

    @Test
    void terminalInstallerAndAgentFiles() throws Exception {
        assertThat(call("POST", "/api/print/terminals", "sardor", Map.of()).getResponse().getStatus()).isEqualTo(403);
        JsonNode inst = ok("POST", "/api/print/terminals", "admin", null);
        String script = inst.get("script").asText();
        long id = inst.get("terminal").get("id").asLong();
        assertThat(inst.get("terminal").get("name").asText()).startsWith("Kassa ");
        assertThat(inst.get("fileName").asText()).isEqualTo("DavronPrinter-kassa" + id + ".cmd");
        assertThat(script).startsWith("@echo off\r\n").contains("DAVRON_SERVER=http://localhost")
                .doesNotContain("{{").doesNotContain("\r\r");
        String token = tokenOf(script);
        assertThat(token).matches("[0-9a-f]{64}");

        // o'rnatuvchi va agent fayllari faqat token bilan
        assertThat(mvc.perform(get("/api/print-agent/installer")).andReturn().getResponse().getStatus()).isEqualTo(401);
        String installer = mvc.perform(get("/api/print-agent/installer").header("X-Agent-Token", token)).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(installer).contains("DavronPrintAgent", "/api/print-agent/script");
        byte[] agent = mvc.perform(get("/api/print-agent/script").header("X-Agent-Token", token)).andReturn()
                .getResponse().getContentAsByteArray();
        String version = mvc.perform(get("/api/print-agent/version").header("X-Agent-Token", token)).andReturn()
                .getResponse().getContentAsString();
        assertThat(version).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(agent)));

        // printer topilmagan agent: vazifa olmaydi, lekin onlayn
        assertThat(poll(token, "").getResponse().getStatus()).isEqualTo(204);
        JsonNode me = agentGet("/api/print-agent/me", token);
        assertThat(me.get("online").asBoolean()).isTrue();
        assertThat(me.get("printerConnected").asBoolean()).isFalse();
        assertThat(me.get("hostName").asText()).isEqualTo("KASSA-PC");

        // o'rnatuvchining test cheki shu kassaga
        mvc.perform(post("/api/print-agent/test").header("X-Agent-Token", token));
        assertThat(text(takeUntil(token, "TEST", 0))).contains("TEST CHEK");
        assertThat(agentGet("/api/print-agent/me", token).get("printerConnected").asBoolean()).isTrue();

        // qayta o'rnatish fayli eski tokenni bekor qiladi
        JsonNode again = ok("POST", "/api/print/terminals/" + id + "/installer", "davron", null);
        assertThat(again.get("script").asText()).doesNotContain(token);
        assertThat(poll(token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void receiptGoesToTheKassaWhereSaleWasMade() throws Exception {
        String[] a = newTerminal("Kassa A");
        String[] b = newTerminal("Kassa B");
        long productId = newProduct("Kassa lampa", "9000", "5", "6000");
        String sardor = "Bearer " + token("sardor");
        MvcResult r = mvc.perform(post("/api/pos/checkout").header("Authorization", sardor).header("X-Kassa-Id", a[0])
                .contentType("application/json").content(json.writeValueAsString(Map.of("paymentType", "NAQD",
                        "receivedAmount", 9000, "items", List.of(Map.of("productId", productId, "qty", 1)))))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        long saleId = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();

        long jobId = -1;
        for (JsonNode j : ok("GET", "/api/print/jobs", "davron", null)) {
            if (j.get("type").asText().equals("SALE") && j.get("refId").asLong() == saleId) {
                jobId = j.get("id").asLong();
                assertThat(j.get("terminalId").asText()).isEqualTo(a[0]);
            }
        }
        // boshqa kassa ham, eski umumiy agent ham bu chekni olmaydi
        assertThat(drain(b[1])).doesNotContain(jobId);
        assertThat(drain(AGENT)).doesNotContain(jobId);
        assertThat(takeUntil(a[1], "SALE", saleId).get("id").asLong()).isEqualTo(jobId);
        assertThat(mvc.perform(post("/api/print-agent/jobs/" + jobId + "/done").header("X-Agent-Token", b[1]))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        mvc.perform(post("/api/print-agent/jobs/" + jobId + "/done").header("X-Agent-Token", a[1]));

        // holat: X-Kassa-Id bo'yicha
        JsonNode st = json.readTree(mvc.perform(get("/api/print/status").header("Authorization", sardor)
                .header("X-Kassa-Id", a[0])).andReturn().getResponse().getContentAsString());
        assertThat(st.get("terminalName").asText()).isEqualTo("Kassa A");
        assertThat(st.get("agentOnline").asBoolean()).isTrue();
        assertThat(st.get("printerConnected").asBoolean()).isTrue();

        // noto'g'ri X-Kassa-Id sotuvni buzmaydi
        MvcResult bad = mvc.perform(post("/api/pos/checkout").header("Authorization", sardor).header("X-Kassa-Id", "abc")
                .contentType("application/json").content(json.writeValueAsString(Map.of("paymentType", "KARTA",
                        "items", List.of(Map.of("productId", productId, "qty", 1)))))).andReturn();
        assertThat(bad.getResponse().getStatus()).isEqualTo(201);

        // nomini o'zgartirish va o'chirish
        assertThat(ok("PUT", "/api/print/terminals/" + b[0], "admin", Map.of("name", "Ombor kassasi")).get("name").asText())
                .isEqualTo("Ombor kassasi");
        ok("DELETE", "/api/print/terminals/" + a[0], "davron", null);
        assertThat(poll(a[1]).getResponse().getStatus()).isEqualTo(401);
        assertThat(ok("GET", "/api/print/terminals", "sardor", null).findValuesAsText("name"))
                .contains("Ombor kassasi").doesNotContain("Kassa A");
    }
}
