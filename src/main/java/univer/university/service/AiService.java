package univer.university.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import univer.university.component.DateUtils;
import univer.university.dto.response.AiResponses.Answer;
import univer.university.dto.response.FinanceResponses.Forecast;
import univer.university.dto.response.FinanceResponses.Summary;
import univer.university.dto.response.ProductResponses.StockList;
import univer.university.exception.BusinessException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI yordamchi: faqat ko'rish va maslahat. Kalit faqat serverda. Do'kon ma'lumotlari kontekst sifatida yuboriladi.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    private static final String SYSTEM = "Sen Davron Elektro do'konining moliyaviy yordamchisisan. "
            + "Faqat o'zbek tilida (lotin yozuvida), qisqa va aniq raqamlar bilan javob ber. "
            + "Faqat berilgan ma'lumotlarga tayan, o'ylab topma. Sen hech qanday yozuvni o'zgartira olmaysan, faqat maslahat berasan.";

    private final FinanceService finance;
    private final ForecastService forecast;
    private final StockService stock;
    private final StaffService staff;
    private final ObjectMapper mapper;

    @Value("${app.ai.api-key:}")
    private String apiKey;
    @Value("${app.ai.model:}")
    private String model;
    @Value("${app.ai.base-url:https://api.anthropic.com}")
    private String baseUrl;

    public Answer chat(String question) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("AI sozlanmagan: app.ai.api-key (AI_API_KEY) kiritilmagan", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String prompt = "Do'kon ma'lumotlari (JSON):\n" + context() + "\n\nSavol: " + question;
        Map<String, Object> body = Map.of(
                "model", model == null || model.isBlank() ? "claude-sonnet-5-5" : model,
                "max_tokens", 1024,
                "system", SYSTEM,
                "messages", java.util.List.of(Map.of("role", "user", "content", prompt)));
        try {
            RestClient client = RestClient.builder().baseUrl(baseUrl)
                    .requestFactory(factory()).build();
            JsonNode res = client.post().uri("/v1/messages")
                    .header("x-api-key", apiKey).header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json").body(body).retrieve().body(JsonNode.class);
            StringBuilder sb = new StringBuilder();
            if (res != null && res.has("content")) {
                for (JsonNode part : res.get("content")) {
                    if (part.has("text")) {
                        sb.append(part.get("text").asText());
                    }
                }
            }
            return new Answer(sb.toString().trim());
        } catch (RestClientException e) {
            throw new BusinessException("AI xizmatiga ulanib bo'lmadi: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    public Answer quickInsight() {
        return chat("Bugungi holat bo'yicha 3 ta eng muhim xulosa va 1 ta tavsiya ber.");
    }

    private JdkClientHttpRequestFactory factory() {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        f.setReadTimeout(Duration.ofSeconds(60));
        return f;
    }

    /** Kassa, foyda, sklad, qarzlar, xodimlar, prognoz. */
    String context() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        Summary s = finance.summary(null);
        ctx.put("sana", DateUtils.today().toString());
        ctx.put("oy_xulosasi", s);
        StockList st = stock.list(null, null, null);
        Map<String, Object> sk = new LinkedHashMap<>();
        sk.put("jami_qiymat", st.totalValue());
        sk.put("muzlatilgan_qiymat", st.frozenValue());
        sk.put("tugagan", st.outOfStock());
        sk.put("tugayapti", st.lowStock());
        sk.put("olik", st.dead());
        sk.put("ortiqcha", st.excess());
        ctx.put("sklad", sk);
        ctx.put("xodimlar", staff.stats(null));
        Forecast f = forecast.forecast();
        Map<String, Object> pr = new LinkedHashMap<>();
        pr.put("boshlangich_qoldiq", f.startBalance());
        pr.put("minimal_qoldiq", f.minBalance());
        pr.put("minimal_kun", f.minDate());
        pr.put("ogohlantirish", f.warning());
        ctx.put("kassa_prognozi_30_kun", pr);
        try {
            return mapper.writeValueAsString(ctx);
        } catch (Exception e) {
            return "{}";
        }
    }
}
