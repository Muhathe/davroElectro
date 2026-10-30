package univer.university;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import univer.university.entity.enums.FiscalStatus;
import univer.university.service.FiscalService;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Tovar rasmi, chek maydonlari, do'kon rekvizitlari va fiskal oqimi. */
class ReceiptAndImageTest extends ApiTestSupport {

    @Autowired
    private FiscalService fiscalService;

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w / 2, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private MvcResult upload(long productId, String user, byte[] bytes, String name) throws Exception {
        return mvc.perform(multipart("/api/products/" + productId + "/image")
                .file(new MockMultipartFile("file", name, "image/png", bytes))
                .header("Authorization", "Bearer " + token(user))).andReturn();
    }

    private static int widthOf(byte[] jpeg) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(jpeg)).getWidth();
    }

    @Test
    void productImageUploadServeAndDelete() throws Exception {
        long id = newProduct("Rasmli lampa", "15000", "3", "9000");

        MvcResult r = upload(id, "davron", png(1600, 900), "lampa.png");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode dto = json.readTree(r.getResponse().getContentAsString());
        String imageUrl = dto.get("imageUrl").asText();
        String thumbUrl = dto.get("thumbUrl").asText();
        assertThat(imageUrl).matches("/files/products/" + id + "/[a-f0-9]{16}\\.jpg");
        assertThat(thumbUrl).isEqualTo(imageUrl.replace(".jpg", "_thumb.jpg"));
        assertThat(ok("GET", "/api/products/" + id, "sardor", null).get("thumbUrl").asText()).isEqualTo(thumbUrl);

        // tokensiz ochiq, abadiy kesh, o'lchamlar 800/200
        MvcResult main = mvc.perform(get(imageUrl)).andReturn();
        assertThat(main.getResponse().getStatus()).isEqualTo(200);
        assertThat(main.getResponse().getHeader("Cache-Control")).isEqualTo("public, max-age=31536000, immutable");
        assertThat(main.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(widthOf(main.getResponse().getContentAsByteArray())).isEqualTo(800);
        assertThat(widthOf(mvc.perform(get(thumbUrl)).andReturn().getResponse().getContentAsByteArray())).isEqualTo(200);

        // yangi rasm — yangi URL, eskisi o'chadi
        String second = json.readTree(upload(id, "davron", png(300, 300), "b.png").getResponse().getContentAsString())
                .get("imageUrl").asText();
        assertThat(second).isNotEqualTo(imageUrl);
        assertThat(mvc.perform(get(imageUrl)).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(widthOf(mvc.perform(get(second)).andReturn().getResponse().getContentAsByteArray())).isEqualTo(300);

        assertThat(call("DELETE", "/api/products/" + id + "/image", "davron", null).getResponse().getStatus()).isEqualTo(204);
        assertThat(ok("GET", "/api/products/" + id, "davron", null).has("imageUrl")).isFalse();
        assertThat(mvc.perform(get(second)).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void webpIsAccepted() throws Exception {
        long id = newProduct("WEBP rasm", "1000", "1", "500");
        byte[] webp = java.util.Base64.getDecoder().decode(
                "UklGRkwAAABXRUJQVlA4IEAAAAAwAwCdASooABQAPm02l0ikIyIhJWgAgA2JZwB2AABX8FvAAP7tTd//WoRhzOb//64D/5wH/zgP5bwQwkzYAAAA");
        MvcResult r = mvc.perform(multipart("/api/products/" + id + "/image")
                .file(new MockMultipartFile("file", "a.webp", "image/webp", webp))
                .header("Authorization", "Bearer " + token("davron"))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        String url = json.readTree(r.getResponse().getContentAsString()).get("imageUrl").asText();
        assertThat(widthOf(mvc.perform(get(url)).andReturn().getResponse().getContentAsByteArray())).isEqualTo(40);
    }

    @Test
    void productImageValidation() throws Exception {
        long id = newProduct("Rasm tekshiruv", "1000", "1", "500");
        assertThat(upload(id, "davron", "<svg></svg>".getBytes(), "x.png").getResponse().getStatus()).isEqualTo(400);
        // PNG signaturasi bor, lekin ichi buzilgan
        byte[] broken = new byte[64];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, broken, 0, 8);
        MvcResult b = upload(id, "davron", broken, "y.png");
        assertThat(b.getResponse().getStatus()).isEqualTo(400);
        assertThat(json.readTree(b.getResponse().getContentAsString()).get("message").asText()).isEqualTo("Rasm fayli buzilgan");

        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(png(10, 10), 0, big, 0, 8);
        assertThat(upload(id, "davron", big, "z.png").getResponse().getStatus()).isEqualTo(413);
        assertThat(upload(999999, "davron", png(10, 10), "a.png").getResponse().getStatus()).isEqualTo(404);
        assertThat(upload(id, "sardor", png(10, 10), "a.png").getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/files/products/" + id + "/..%2F..%2Fapplication.properties")).andReturn()
                .getResponse().getStatus()).isIn(400, 404);
        assertThat(mvc.perform(get("/files/products/" + id + "/notes.txt")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void unknownPathGives404Not500() throws Exception {
        MvcResult r = call("GET", "/api/print", "davron", null);
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
        assertThat(json.readTree(r.getResponse().getContentAsString()).get("path").asText()).isEqualTo("/api/print");
        assertThat(call("PATCH", "/api/products/1/image", "davron", null).getResponse().getStatus()).isEqualTo(405);
    }

    @Test
    void checkoutReturnsReceiptAmounts() throws Exception {
        long id = newProduct("Chek kabel", "10000", "10", "7000");
        long customerId = ok("POST", "/api/customers", "davron",
                Map.of("name", "Chegirmali usta", "type", "USTA", "discountPercent", 10)).get("id").asLong();
        JsonNode sale = ok("POST", "/api/pos/checkout", "sardor", Map.of("paymentType", "NAQD", "customerId", customerId,
                "receivedAmount", new BigDecimal("30000"), "items", List.of(Map.of("productId", id, "qty", 3))));
        assertThat(sale.get("subtotal").decimalValue()).isEqualByComparingTo("30000");
        assertThat(sale.get("discountAmount").decimalValue()).isEqualByComparingTo("3000");
        assertThat(sale.get("total").decimalValue()).isEqualByComparingTo("27000");
        assertThat(sale.get("receivedAmount").decimalValue()).isEqualByComparingTo("30000");
        assertThat(sale.get("change").decimalValue()).isEqualByComparingTo("3000");
        assertThat(sale.has("fiscal")).isFalse();

        JsonNode card = ok("POST", "/api/pos/checkout", "sardor", Map.of("paymentType", "KARTA",
                "items", List.of(Map.of("productId", id, "qty", 1))));
        assertThat(card.has("change")).isFalse();
        assertThat(card.get("discountAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(call("POST", "/api/pos/sales/" + card.get("id").asLong() + "/fiscalize", "davron", null)
                .getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void storeSettings() throws Exception {
        assertThat(ok("GET", "/api/settings/store", "sardor", null).get("name").asText()).isNotBlank();
        Map<String, Object> body = Map.of("name", "Davron Elektro", "address", "Toshkent, Chilonzor 5",
                "phone", "+998901234567", "inn", "123456789", "receiptFooter", "Xaridingiz uchun rahmat!");
        assertThat(call("PUT", "/api/settings/store", "admin", body).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("PUT", "/api/settings/store", "davron", Map.of("name", "X", "inn", "12"))
                .getResponse().getStatus()).isEqualTo(400);
        ok("PUT", "/api/settings/store", "davron", body);
        JsonNode s = ok("GET", "/api/settings/store", "buxgalter", null);
        assertThat(s.get("inn").asText()).isEqualTo("123456789");
        assertThat(s.get("receiptFooter").asText()).isEqualTo("Xaridingiz uchun rahmat!");
    }

    @Test
    void fiscalPendingThenRetrySucceeds() throws Exception {
        long id = newProduct("Fiskal avtomat", "50000", "5", "30000");
        ok("PUT", "/api/products/" + id, "davron", Map.of("name", "Fiskal avtomat", "barcode",
                ok("GET", "/api/products/" + id, "davron", null).get("barcode").asText(), "unit", "DONA",
                "price", 50000, "mxikCode", "08544001001000000", "packageCode", "1501", "vatPercent", 12));
        ReflectionTestUtils.setField(fiscalService, "enabled", true);
        try {
            TestFiscalProvider.online = false;
            JsonNode sale = ok("POST", "/api/pos/checkout", "sardor", Map.of("paymentType", "NAQD",
                    "items", List.of(Map.of("productId", id, "qty", 2))));
            assertThat(sale.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(sale.get("fiscal").get("status").asText()).isEqualTo(FiscalStatus.PENDING.name());
            assertThat(sale.get("fiscal").get("error").asText()).contains("javob bermadi");
            long saleId = sale.get("id").asLong();

            boolean listed = false;
            for (JsonNode s : ok("GET", "/api/pos/sales?fiscalStatus=PENDING", "davron", null).get("sales")) {
                listed |= s.get("id").asLong() == saleId;
            }
            assertThat(listed).isTrue();
            assertThat(call("POST", "/api/pos/sales/" + saleId + "/fiscalize", "sardor", null)
                    .getResponse().getStatus()).isEqualTo(403);

            TestFiscalProvider.online = true;
            JsonNode fixed = ok("POST", "/api/pos/sales/" + saleId + "/fiscalize", "admin", null);
            assertThat(fixed.get("fiscal").get("status").asText()).isEqualTo("SUCCESS");
            assertThat(fixed.get("fiscal").get("qrUrl").asText()).startsWith("https://");
            assertThat(TestFiscalProvider.last.lines().get(0).mxikCode()).isEqualTo("08544001001000000");
            assertThat(call("POST", "/api/pos/sales/" + saleId + "/fiscalize", "admin", null)
                    .getResponse().getStatus()).isEqualTo(409);

            JsonNode ret = ok("POST", "/api/returns", "sardor", Map.of("checkNo", sale.get("checkNo").asText(),
                    "reason", "NOSOZ", "items", List.of(Map.of("productId", id, "qty", 1))));
            assertThat(ret.get("fiscal").get("status").asText()).isEqualTo("SUCCESS");
            assertThat(TestFiscalProvider.last.type().name()).isEqualTo("RETURN");
            assertThat(TestFiscalProvider.last.originalFiscalSign()).isEqualTo(fixed.get("fiscal").get("fiscalSign").asText());
        } finally {
            ReflectionTestUtils.setField(fiscalService, "enabled", false);
            TestFiscalProvider.online = true;
        }
    }
}
