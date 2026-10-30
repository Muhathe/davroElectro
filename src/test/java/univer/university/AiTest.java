package univer.university;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import univer.university.service.AiService;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** AI vositalar sikli soxta OpenAI mos server bilan tekshiriladi (haqiqiy provayderga so'rov ketmaydi). */
class AiTest extends ApiTestSupport {

    private record Reply(int status, String body) {
    }

    @Autowired
    private AiService ai;

    private HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<JsonNode> requests = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", ex -> {
            requests.add(json.readTree(ex.getRequestBody().readAllBytes()));
            Reply r = replies.isEmpty() ? new Reply(500, "{}") : replies.poll();
            byte[] out = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("content-type", "application/json");
            ex.sendResponseHeaders(r.status(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        ReflectionTestUtils.setField(ai, "apiKey", "test-key");
        ReflectionTestUtils.setField(ai, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
        ReflectionTestUtils.setField(ai, "apiKey", "");
    }

    private static Reply text(String s) {
        return new Reply(200, "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"" + s + "\"}}]}");
    }

    private static Reply toolCall(String name, String args) {
        return new Reply(200, "{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"tool_calls\":"
                + "[{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\"" + name + "\",\"arguments\":\"" + args.replace("\"", "\\\"") + "\"}}]}}]}");
    }

    private List<String> toolNames(JsonNode req) {
        List<String> names = new ArrayList<>();
        req.path("tools").forEach(t -> names.add(t.path("function").path("name").asText()));
        return names;
    }

    @Test
    void answersFromToolResult() throws Exception {
        newProduct("AiTekshiruv lampa", "12000", "37", "8000");
        replies.add(toolCall("tovar_qidirish", "{\"qidiruv\":\"AiTekshiruv\"}"));
        replies.add(text("37 dona qoldi"));

        JsonNode res = ok("POST", "/api/ai/chat", "davron", Map.of("message", "AiTekshiruv lampa nechta qoldi?"));

        assertThat(res.get("answer").asText()).isEqualTo("37 dona qoldi");
        assertThat(requests).hasSize(2);
        JsonNode second = requests.get(1).get("messages");
        JsonNode toolMessage = second.get(second.size() - 1);
        assertThat(toolMessage.get("role").asText()).isEqualTo("tool");
        assertThat(toolMessage.get("tool_call_id").asText()).isEqualTo("c1");
        assertThat(toolMessage.get("content").asText()).contains("AiTekshiruv lampa").contains("37");
    }

    @Test
    void historyIsSentToModel() throws Exception {
        replies.add(text("ok"));
        ok("POST", "/api/ai/chat", "davron", Map.of("message", "Undan nechta?",
                "history", List.of(Map.of("role", "user", "content", "LED lampa"), Map.of("role", "assistant", "content", "Topildi"))));
        assertThat(requests.get(0).get("messages").size()).isEqualTo(4);
    }

    @Test
    void forgedToolCallCannotBypassPermissions() throws Exception {
        long id = ok("GET", "/api/auth/me", "sardor", null).get("user").get("id").asLong();
        ok("PUT", "/api/roles/users/" + id + "/permissions", "davron", List.of(
                Map.of("module", "AI", "canView", true, "canCreate", true, "canEdit", false, "canDelete", false)));
        try {
            replies.add(toolCall("moliya_xulosasi", "{}"));
            replies.add(text("ruxsat yo'q"));
            ok("POST", "/api/ai/chat", "sardor", Map.of("message", "Foyda qancha?"));

            assertThat(toolNames(requests.get(0))).contains("tovar_qidirish").doesNotContain("moliya_xulosasi", "xodimlar_statistikasi");
            JsonNode second = requests.get(1).get("messages");
            assertThat(second.get(second.size() - 1).get("content").asText()).contains("ruxsat yo'q");
        } finally {
            ok("DELETE", "/api/roles/users/" + id + "/permissions", "davron", null);
        }
    }

    @Test
    void providerErrorsAreClear() throws Exception {
        replies.add(new Reply(401, "{\"error\":\"bad key\"}"));
        var bad = call("POST", "/api/ai/chat", "davron", Map.of("message", "salom"));
        assertThat(bad.getResponse().getStatus()).isEqualTo(503);
        assertThat(bad.getResponse().getContentAsString()).doesNotContain("bad key");

        replies.add(new Reply(429, "{}"));
        assertThat(call("POST", "/api/ai/chat", "davron", Map.of("message", "salom")).getResponse().getStatus()).isEqualTo(429);

        replies.add(new Reply(200, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));
        assertThat(call("POST", "/api/ai/chat", "davron", Map.of("message", "salom")).getResponse().getStatus()).isEqualTo(502);
    }

    @Test
    void brokenToolCallIsRetriedOnce() throws Exception {
        replies.add(new Reply(400, "{\"error\":{\"code\":\"tool_use_failed\"}}"));
        replies.add(text("tayyor"));
        assertThat(ok("POST", "/api/ai/chat", "davron", Map.of("message", "salom")).get("answer").asText()).isEqualTo("tayyor");
    }
}
