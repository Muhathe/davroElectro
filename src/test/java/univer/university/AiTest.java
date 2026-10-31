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

    private record Reply(int status, String body, String retryAfter) {
        Reply(int status, String body) {
            this(status, body, null);
        }
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
            if (r.retryAfter() != null) {
                ex.getResponseHeaders().add("retry-after", r.retryAfter());
            }
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

        // kutish muddatga sig'maydi (kunlik limit): darhol aniq xato
        replies.add(new Reply(429, "{}", "3600"));
        assertThat(call("POST", "/api/ai/chat", "davron", Map.of("message", "salom")).getResponse().getStatus()).isEqualTo(429);
        assertThat(requests).hasSize(2);

        String empty = "{\"choices\":[{\"message\":{\"content\":\"\"}}]}";
        replies.addAll(List.of(new Reply(200, empty), new Reply(200, empty), new Reply(200, empty)));
        assertThat(call("POST", "/api/ai/chat", "davron", Map.of("message", "salom")).getResponse().getStatus()).isEqualTo(502);
    }

    @Test
    void rateLimitIsWaitedOutAndRetried() throws Exception {
        replies.add(new Reply(429, "{}", "1"));
        replies.add(text("tayyor"));
        long start = System.nanoTime();
        assertThat(ok("POST", "/api/ai/chat", "davron", Map.of("message", "salom")).get("answer").asText()).isEqualTo("tayyor");
        assertThat(requests).hasSize(2);
        assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(900_000_000L);
    }

    @Test
    void serverErrorIsRetried() throws Exception {
        replies.add(new Reply(503, "{}"));
        replies.add(text("tayyor"));
        assertThat(ok("POST", "/api/ai/chat", "davron", Map.of("message", "salom")).get("answer").asText()).isEqualTo("tayyor");
    }

    @Test
    void brokenToolCallAfterDataAnswersFromCollectedData() throws Exception {
        replies.add(toolCall("kassa_qoldigi", "{}"));
        replies.add(new Reply(400, "{\"error\":{\"code\":\"tool_use_failed\"}}"));
        replies.add(text("kassada pul bor"));
        assertThat(ok("POST", "/api/ai/chat", "davron", Map.of("message", "Kassada qancha?")).get("answer").asText()).isEqualTo("kassada pul bor");
        assertThat(requests.get(1).has("tool_choice")).isFalse();
        assertThat(requests.get(2).get("tool_choice").asText()).isEqualTo("none");
    }

    @Test
    void lastStepForcesAnAnswerInsteadOfAnotherToolCall() throws Exception {
        for (int i = 0; i < 5; i++) {
            replies.add(toolCall("kassa_qoldigi", "{}"));
        }
        replies.add(text("yakuniy javob"));
        assertThat(ok("POST", "/api/ai/chat", "davron", Map.of("message", "Kassada qancha?")).get("answer").asText()).isEqualTo("yakuniy javob");
        assertThat(requests.get(5).get("tool_choice").asText()).isEqualTo("none");
    }

    @Test
    void emptyReplyIsRetried() throws Exception {
        replies.add(new Reply(200, "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"\"}}]}"));
        replies.add(text("tayyor"));
        assertThat(ok("POST", "/api/ai/chat", "davron", Map.of("message", "salom")).get("answer").asText()).isEqualTo("tayyor");
    }

    @Test
    void reasoningIsLimitedForGptOss() throws Exception {
        replies.add(text("ok"));
        ok("POST", "/api/ai/chat", "davron", Map.of("message", "salom"));
        assertThat(requests.get(0).get("reasoning_effort").asText()).isEqualTo("low");
        assertThat(requests.get(0).get("include_reasoning").asBoolean()).isFalse();
    }

    @Test
    void brokenToolCallIsRetriedOnce() throws Exception {
        replies.add(new Reply(400, "{\"error\":{\"code\":\"tool_use_failed\"}}"));
        replies.add(text("tayyor"));
        assertThat(ok("POST", "/api/ai/chat", "davron", Map.of("message", "salom")).get("answer").asText()).isEqualTo("tayyor");
    }
}
