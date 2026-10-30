package univer.university;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityTest extends ApiTestSupport {

    @Test
    void noTokenGives401Json() throws Exception {
        MvcResult r = call("GET", "/api/pos/cash/balance", null, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(json.readTree(r.getResponse().getContentAsString()).get("status").asInt()).isEqualTo(401);
    }

    @Test
    void wrongPasswordGives401() throws Exception {
        MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType("application/json").content("{\"username\":\"davron\",\"password\":\"notogri\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void roleMatrixIsEnforced() throws Exception {
        assertThat(call("GET", "/api/accounting/journal", "sardor", null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("GET", "/api/expenses", "sardor", null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("GET", "/api/pos/cash/balance", "sardor", null).getResponse().getStatus()).isEqualTo(200);
        assertThat(call("GET", "/api/users", "buxgalter", null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("GET", "/api/accounting/journal", "admin", null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("GET", "/api/accounting/journal", "buxgalter", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void adminCannotChangeOwnRolePermissions() throws Exception {
        Map<String, Object> adminRow = Map.of("role", "ADMIN", "module", "BUXGALTERIYA",
                "canView", true, "canCreate", true, "canEdit", true, "canDelete", true);
        assertThat(call("PUT", "/api/roles/matrix", "admin", List.of(adminRow)).getResponse().getStatus()).isEqualTo(403);

        // boshqa rol qatorini o'zgartira oladi (qiymatlar seed bilan bir xil — holat o'zgarmaydi)
        Map<String, Object> sellerRow = Map.of("role", "SOTUVCHI", "module", "KASSA",
                "canView", true, "canCreate", true, "canEdit", false, "canDelete", false);
        assertThat(call("PUT", "/api/roles/matrix", "admin", List.of(sellerRow)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void meAndChangePassword() throws Exception {
        JsonNode me = ok("GET", "/api/auth/me", "sardor", null);
        assertThat(me.get("user").get("role").asText()).isEqualTo("SOTUVCHI");
        assertThat(me.get("permissions").size()).isGreaterThan(0);

        ok("POST", "/api/users", "admin", Map.of("username", "parol_test", "password", PASSWORD,
                "fullName", "Parol Test", "role", "SOTUVCHI"));
        assertThat(call("PUT", "/api/auth/password", "parol_test",
                Map.of("oldPassword", "xato-parol", "newPassword", "yangi-parol-1")).getResponse().getStatus()).isEqualTo(422);
        assertThat(call("PUT", "/api/auth/password", "parol_test",
                Map.of("oldPassword", PASSWORD, "newPassword", "yangi-parol-1")).getResponse().getStatus()).isEqualTo(204);

        MvcResult relogin = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType("application/json").content("{\"username\":\"parol_test\",\"password\":\"yangi-parol-1\"}")).andReturn();
        assertThat(relogin.getResponse().getStatus()).isEqualTo(200);
    }
}
