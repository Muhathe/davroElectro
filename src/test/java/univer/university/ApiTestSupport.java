package univer.university;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Umumiy kontekst (H2 + seed ma'lumotlari) va HTTP yordamchilari. Seed paroli: test12345. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFiscalProvider.class)
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "test12345";
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();
    private static final AtomicLong BARCODE = new AtomicLong(99_000_000_000L);

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;

    protected String token(String username) throws Exception {
        String cached = TOKENS.get(username);
        if (cached != null) {
            return cached;
        }
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD)))).andReturn();
        if (r.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login muvaffaqiyatsiz: " + username + " -> " + r.getResponse().getContentAsString());
        }
        String t = json.readTree(r.getResponse().getContentAsString()).get("token").asText();
        TOKENS.put(username, t);
        return t;
    }

    protected MvcResult call(String method, String url, String user, Object body) throws Exception {
        var req = request(org.springframework.http.HttpMethod.valueOf(method), url);
        if (user != null) {
            req.header("Authorization", "Bearer " + token(user));
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(req).andReturn();
    }

    protected JsonNode ok(String method, String url, String user, Object body) throws Exception {
        MvcResult r = call(method, url, user, body);
        int s = r.getResponse().getStatus();
        if (s < 200 || s >= 300) {
            throw new AssertionError(method + " " + url + " -> " + s + ": " + r.getResponse().getContentAsString());
        }
        String c = r.getResponse().getContentAsString();
        return c.isEmpty() ? null : json.readTree(c);
    }

    /** Yangi tovar yaratadi va unga to'liq to'langan kirim qiladi. */
    protected long newProduct(String name, String price, String qty, String cost) throws Exception {
        String barcode = String.valueOf(BARCODE.incrementAndGet());
        long id = ok("POST", "/api/products", "davron", Map.of("name", name, "barcode", barcode, "unit", "DONA",
                "price", new BigDecimal(price), "minQty", BigDecimal.ZERO)).get("id").asLong();
        BigDecimal total = new BigDecimal(qty).multiply(new BigDecimal(cost));
        ok("POST", "/api/stock/incoming", "davron", Map.of("paid", total, "method", "NAQD",
                "items", java.util.List.of(Map.of("productId", id, "qty", new BigDecimal(qty), "cost", new BigDecimal(cost)))));
        return id;
    }

    protected BigDecimal productQty(long id) throws Exception {
        return ok("GET", "/api/products/" + id, "davron", null).get("qty").decimalValue();
    }

    protected BigDecimal cashTotal() throws Exception {
        return ok("GET", "/api/pos/cash/balance", "davron", null).get("total").decimalValue();
    }
}
