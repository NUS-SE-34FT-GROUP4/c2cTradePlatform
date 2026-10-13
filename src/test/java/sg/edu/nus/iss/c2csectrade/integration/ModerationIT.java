package sg.edu.nus.iss.c2csectrade.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import sg.edu.nus.iss.c2csectrade.service.CaptchaService;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #61 and #63 against the real schema: report, uphold or dismiss, ban and
 * reinstate, and what a banned account sees (docs/api/moderation.md).
 * Seed users: admin (1, ROLE_ADMIN), seller_lvl1..3 (101..103), all with password admin123.
 */
class ModerationIT extends MySqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired CaptchaService captcha;
    @Autowired ObjectMapper json;

    private String token(String user) { return "Bearer " + tokens.generateTokenForUsername(user); }

    private ResultActions report(String reporter, String targetType, long targetId, String reason) throws Exception {
        return mvc.perform(post("/api/reports").header("Authorization", token(reporter))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("targetType", targetType, "targetId", targetId,
                        "reason", reason, "detail", "Seen on the listing page"))));
    }

    private long reportId(String reporter, String targetType, long targetId, String reason) throws Exception {
        String body = report(reporter, targetType, targetId, reason).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private ResultActions admin(String path, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", token("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions login(String username, String password) throws Exception {
        captcha.save("moderation-it", "abcd", 60);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password,
                        "captchaId", "moderation-it", "captchaCode", "abcd"))));
    }

    private String userStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, id);
    }

    @Test
    void reasonsArePublic() throws Exception {
        mvc.perform(get("/api/reports/reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].code").value("FRAUD"))
                .andExpect(jsonPath("$[0].label").value("Scam or fraud"));
    }

    @Test
    void reportIsStoredAndRejectsDuplicatesSelfReportsAndUnknownTargets() throws Exception {
        report("seller_lvl2", "USER", 101, "FRAUD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reporter.username").value("seller_lvl2"))
                .andExpect(jsonPath("$.target.username").value("seller_lvl1"))
                .andExpect(jsonPath("$.reasonLabel").value("Scam or fraud"));

        report("seller_lvl2", "USER", 101, "SPAM").andExpect(status().isConflict());
        report("seller_lvl1", "PRODUCT", 1, "SPAM").andExpect(status().isBadRequest());   // own listing
        report("seller_lvl1", "USER", 101, "SPAM").andExpect(status().isBadRequest());    // self
        report("seller_lvl2", "USER", 101, "NOT_A_REASON").andExpect(status().isBadRequest());
        report("seller_lvl2", "PRODUCT", 999999, "SPAM").andExpect(status().isNotFound());

        mvc.perform(get("/api/reports/mine").header("Authorization", token("seller_lvl2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].targetType").value("USER"));
    }

    @Test
    void onlyAdministratorsReachTheBackOffice() throws Exception {
        mvc.perform(get("/api/admin/reports").header("Authorization", token("seller_lvl1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports").header("Authorization", token("admin")))
                .andExpect(status().isOk());
    }

    @Test
    void upholdingAUserReportBansTheAccountUntilReinstated() throws Exception {
        String sellerToken = token("seller_lvl1");     // issued before the ban
        long first = reportId("seller_lvl2", "USER", 101, "FRAUD");
        long second = reportId("seller_lvl3", "USER", 101, "HARASSMENT");

        mvc.perform(get("/api/admin/reports").header("Authorization", token("admin")))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first));   // oldest first

        admin("/api/admin/reports/" + first + "/uphold", Map.of("note", "Three buyers complained"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPHELD"))
                .andExpect(jsonPath("$.decidedBy.username").value("admin"));

        assertEquals("BANNED", userStatus(101));
        assertEquals("UPHELD", jdbc.queryForObject("SELECT status FROM report WHERE id=?", String.class, second));
        String reason = jdbc.queryForObject("SELECT ban_reason FROM users WHERE id=101", String.class);
        assertEquals("Scam or fraud: Three buyers complained", reason);

        // An old token is refused with the reason on authenticated endpoints...
        mvc.perform(get("/api/cart").header("Authorization", sellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_BANNED"))
                .andExpect(jsonPath("$.message").value(containsString("Three buyers complained")));
        mvc.perform(post("/api/reports").header("Authorization", sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // ...public pages still work...
        mvc.perform(get("/api/products").header("Authorization", sellerToken)).andExpect(status().isOk());
        // ...login says why only with the right password.
        login("seller_lvl1", "admin123")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_BANNED"));
        login("seller_lvl1", "wrong-password").andExpect(status().isUnauthorized());

        admin("/api/admin/users/101/reinstate", Map.of("note", "Appeal accepted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.banReason").doesNotExist());
        assertEquals("ACTIVE", userStatus(101));
        mvc.perform(get("/api/cart").header("Authorization", sellerToken)).andExpect(status().isOk());
        login("seller_lvl1", "admin123").andExpect(status().isOk());

        mvc.perform(get("/api/admin/audit-log").header("Authorization", token("admin")))
                .andExpect(jsonPath("$.content[*].action", hasItem("UPHOLD_REPORT")))
                .andExpect(jsonPath("$.content[*].action", hasItem("BAN_USER")))
                .andExpect(jsonPath("$.content[0].action").value("REINSTATE_USER"));
    }

    @Test
    void upholdingAProductReportDelistsItAndADecidedReportCannotBeDecidedAgain() throws Exception {
        long id = reportId("seller_lvl2", "PRODUCT", 1, "PROHIBITED_ITEM");
        admin("/api/admin/reports/" + id + "/uphold", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.status").value(0));
        assertEquals(0, jdbc.queryForObject("SELECT status FROM pms_product WHERE id=1", Integer.class));
        assertEquals("ACTIVE", userStatus(101));      // the seller is not banned automatically
        admin("/api/admin/reports/" + id + "/dismiss", Map.of()).andExpect(status().isConflict());
        admin("/api/admin/reports/999999/uphold", Map.of()).andExpect(status().isNotFound());
    }

    @Test
    void upholdingAReportAgainstAnAdministratorIsRefusedAndLeavesItOpen() throws Exception {
        long id = reportId("seller_lvl2", "USER", 1, "HARASSMENT");     // admin is user 1
        admin("/api/admin/reports/" + id + "/uphold", Map.of("note", "x"))
                .andExpect(status().isBadRequest());
        assertEquals("ACTIVE", userStatus(1));
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM report WHERE id=?", String.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE report_id=?", Integer.class, id));
        admin("/api/admin/reports/" + id + "/dismiss", Map.of("note", "Not actionable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    void dismissingLeavesTheTargetAlone() throws Exception {
        long id = reportId("seller_lvl2", "USER", 103, "SPAM");
        admin("/api/admin/reports/" + id + "/dismiss", Map.of("note", "Not spam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.decisionNote").value("Not spam"));
        assertEquals("ACTIVE", userStatus(103));
    }

    @Test
    void banNeedsAReasonAndNeverHitsAdministrators() throws Exception {
        admin("/api/admin/users/102/ban", Map.of()).andExpect(status().isBadRequest());
        admin("/api/admin/users/1/ban", Map.of("reason", "x")).andExpect(status().isBadRequest());
        admin("/api/admin/users/999999/ban", Map.of("reason", "x")).andExpect(status().isNotFound());
        admin("/api/admin/users/102/reinstate", Map.of()).andExpect(status().isConflict());

        admin("/api/admin/users/102/ban", Map.of("reason", "Repeated scam listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BANNED"))
                .andExpect(jsonPath("$.banReason").value("Repeated scam listings"));
        admin("/api/admin/users/102/ban", Map.of("reason", "again")).andExpect(status().isConflict());

        mvc.perform(get("/api/admin/users").param("status", "BANNED").header("Authorization", token("admin")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("seller_lvl2"));
    }
}
