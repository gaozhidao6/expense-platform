package com.shixi.expense.policy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shixi.expense.auth.security.JwtTokenProvider;
import com.shixi.expense.approval.entity.ApprovalTask;
import com.shixi.expense.approval.mapper.ApprovalTaskMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Real MySQL + HTTP authorization and submission transactions, with Redis unavailable. */
@SpringBootTest(properties = {
    "security.jwt.secret=ZXhwZW5zZS1wbGF0Zm9ybS1qd3QtdGVzdC1zZWNyZXQtMzItYnl0ZXM=",
    "app.jobs.enabled=false"
})
@AutoConfigureMockMvc
class ExpenseLimitIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    @MockBean StringRedisTemplate redis;
    @SpyBean ApprovalTaskMapper tasks;
    private String prefix;
    private Map<String, Object> originalPolicy;

    @BeforeEach void prepare() {
        prefix = "limit-test-" + UUID.randomUUID();
        originalPolicy = jdbc.queryForMap("SELECT * FROM expense_policy WHERE expense_type='TRANSPORT'");
        jdbc.update("UPDATE expense_policy SET limit_amount=200, enabled=1 WHERE expense_type='TRANSPORT'");
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("test offline"));
    }

    @AfterEach void cleanUp() {
        reset(tasks);
        List<Long> ids = jdbc.queryForList("SELECT id FROM expense_report WHERE title LIKE ?", Long.class, prefix + "%");
        for (Long id : ids) {
            jdbc.update("DELETE FROM notification_outbox WHERE message_key LIKE ?", "SUBMIT_REPORT:" + id + ":%");
            jdbc.update("DELETE FROM notification WHERE business_type='EXPENSE_REPORT' AND business_id=?", id);
            jdbc.update("DELETE FROM operation_log WHERE business_type='EXPENSE_REPORT' AND business_id=?", id);
            jdbc.update("DELETE FROM idempotency_record WHERE business_type='SUBMIT_REPORT' AND business_id=?", id);
            jdbc.update("DELETE FROM approval_record WHERE report_id=?", id);
            jdbc.update("DELETE FROM approval_task WHERE report_id=?", id);
            jdbc.update("DELETE FROM expense_item WHERE report_id=?", id);
            jdbc.update("DELETE FROM expense_report WHERE id=?", id);
        }
        jdbc.update("UPDATE expense_policy SET limit_amount=?, enabled=?, updated_by=?, updated_at=? WHERE expense_type='TRANSPORT'",
                originalPolicy.get("limit_amount"), originalPolicy.get("enabled"), originalPolicy.get("updated_by"), originalPolicy.get("updated_at"));
    }

    private String token(String username, String role) {
        Long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        return "Bearer " + jwt.generateToken(id, username, List.of(role));
    }
    private String item(String amount, String reason) throws Exception {
        return json.writeValueAsString(Map.of("expenseDate", "2026-08-28", "expenseType", "TRANSPORT",
                "description", "交通费用", "amount", new BigDecimal(amount), "overLimitReason", reason));
    }
    private long draft(String amount, String reason) throws Exception {
        String body = "{\"title\":\"" + prefix + "\",\"currency\":\"CNY\",\"items\":[" + item(amount, reason) + "]}";
        String response = mvc.perform(post("/api/reports").header("Authorization", token("employee", "EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("id").asLong();
    }
    private void submit(long id, String key, int expectedStatus) throws Exception {
        mvc.perform(post("/api/reports/{id}/submit", id).header("Authorization", token("employee", "EMPLOYEE"))
                .header("Idempotency-Key", key)).andExpect(status().is(expectedStatus));
    }
    private void changePolicy(String amount, boolean enabled) throws Exception {
        mvc.perform(put("/api/expense-policies/TRANSPORT").header("Authorization", token("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":" + amount + ",\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk());
    }

    @Test void overLimitWithoutReasonIsRejectedWithoutTasksAndCanRetryAfterCorrection() throws Exception {
        long id = draft("200.01", "   ");
        String key = prefix + "-retry";
        submit(id, key, 400);
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM expense_report WHERE id=?", String.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM approval_task WHERE report_id=?", Integer.class, id));
        assertNull(jdbc.queryForObject("SELECT applied_limit_amount FROM expense_item WHERE report_id=?", BigDecimal.class, id));
        long itemId = jdbc.queryForObject("SELECT id FROM expense_item WHERE report_id=?", Long.class, id);
        mvc.perform(put("/api/reports/{id}/items/{itemId}", id, itemId).header("Authorization", token("employee", "EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON).content(item("200.01", "临时客户拜访需改签")))
                .andExpect(status().isOk());
        submit(id, key, 200);
        submit(id, key, 200);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM approval_task WHERE report_id=?", Integer.class, id));
    }

    @Test void exactLimitDoesNotRequireReason() throws Exception {
        long id = draft("200.00", "");
        submit(id, prefix, 200);
        mvc.perform(get("/api/reports/{id}", id).header("Authorization", token("employee", "EMPLOYEE")))
                .andExpect(jsonPath("$.items[0].overLimit").value(false));
    }

    @Test void ruleChangeDoesNotAlterSubmittedSnapshotOrIdempotentReplay() throws Exception {
        long id = draft("250", "远程客户拜访");
        submit(id, prefix, 200);
        changePolicy("500", true);
        mvc.perform(get("/api/reports/{id}", id).header("Authorization", token("manager", "MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].limitAmount").value(200))
                .andExpect(jsonPath("$.items[0].overLimit").value(true))
                .andExpect(jsonPath("$.items[0].overLimitReason").value("远程客户拜访"));
        mvc.perform(post("/api/reports/{id}/submit", id).header("Authorization", token("employee", "EMPLOYEE"))
                .header("Idempotency-Key", prefix)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].limitAmount").value(200));
    }

    @Test void disablingRuleRemovesDraftRequirementAndSnapshotsNoLimit() throws Exception {
        long id = draft("250", "");
        changePolicy("200", false);
        submit(id, prefix, 200);
        assertNull(jdbc.queryForObject("SELECT applied_limit_amount FROM expense_item WHERE report_id=?", BigDecimal.class, id));
    }

    @Test void lateSubmissionFailureRollsBackLimitSnapshotAndApproval() throws Exception {
        long id = draft("250", "客户急需");
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test insert failure"))
                .when(tasks).insert(any(ApprovalTask.class));
        submit(id, prefix, 500);
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM expense_report WHERE id=?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT applied_limit_amount FROM expense_item WHERE report_id=?", BigDecimal.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM approval_task WHERE report_id=?", Integer.class, id));
    }

    @Test void ruleWriteRequiresAdminAndValidAmount() throws Exception {
        mvc.perform(get("/api/expense-policies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/expense-policies").header("Authorization", token("employee", "EMPLOYEE")))
                .andExpect(status().isOk());
        for (String username : List.of("employee", "manager", "finance")) {
            mvc.perform(put("/api/expense-policies/TRANSPORT").header("Authorization", token(username, username.toUpperCase()))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":200,\"enabled\":true}"))
                    .andExpect(status().isForbidden());
        }
        for (String amount : List.of("0", "-1", "1.001")) {
            mvc.perform(put("/api/expense-policies/TRANSPORT").header("Authorization", token("admin", "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":" + amount + ",\"enabled\":true}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void arbitraryExpenseTypeCannotBypassRulesAndReasonLengthIsValidated() throws Exception {
        String unknown = item("250", "").replace("TRANSPORT", "TRANSPORT_2");
        mvc.perform(post("/api/reports").header("Authorization", token("employee", "EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + prefix + "\",\"items\":[" + unknown + "]}"))
                .andExpect(status().isBadRequest());
        String tooLong = item("250", "x".repeat(501));
        mvc.perform(post("/api/reports").header("Authorization", token("employee", "EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + prefix + "\",\"items\":[" + tooLong + "]}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM expense_report WHERE title=?", Integer.class, prefix));
    }

    @Test void loweredRuleIsRecheckedFromDatabaseAtSubmission() throws Exception {
        long id = draft("150", "");
        changePolicy("100", true);
        submit(id, prefix, 400);
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM expense_report WHERE id=?", String.class, id));
    }

    @Test void unsupportedCurrencyCannotUseCnyThreshold() throws Exception {
        long id = draft("150", "");
        jdbc.update("UPDATE expense_report SET currency='USD' WHERE id=?", id);
        submit(id, prefix, 400);
    }
}
