package com.shixi.expense.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shixi.expense.auth.security.JwtAuthenticationFilter;
import com.shixi.expense.auth.security.JwtTokenProvider;
import com.shixi.expense.config.SecurityConfig;
import com.shixi.expense.report.dto.ExpenseReportCreateDTO;
import com.shixi.expense.report.dto.ExpenseItemDTO;
import com.shixi.expense.report.service.ExpenseReportService;
import com.shixi.expense.report.vo.ExpenseItemVO;
import com.shixi.expense.report.vo.ExpenseReportVO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExpenseReportController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtTokenProvider.class
})
@TestPropertySource(properties = {
        "security.jwt.secret=ZXhwZW5zZS1wbGF0Zm9ybS1qd3QtdGVzdC1zZWNyZXQtMzItYnl0ZXM=",
        "security.jwt.expiration-seconds=7200"
})
class ExpenseReportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ExpenseReportService expenseReportService;

    @Test
    void rejectsCreateWithoutToken() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());

        verify(expenseReportService, never()).createReport(any());
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(expenseReportService, never()).createReport(any());
    }

    @Test
    void createsDraftWithValidToken() throws Exception {
        when(expenseReportService.createReport(any(ExpenseReportCreateDTO.class)))
                .thenReturn(reportVO());

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalAmount").value(350.00))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void returnsReportDetailWithValidToken() throws Exception {
        when(expenseReportService.getDetail(10L)).thenReturn(reportVO());

        mockMvc.perform(get("/api/reports/10")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.reportNo").value("ER-TEST"));
    }

    @Test
    void returnsForbiddenWhenViewingAnotherUsersReport() throws Exception {
        when(expenseReportService.getDetail(10L))
                .thenThrow(new AccessDeniedException("无权查看该报销单"));

        mockMvc.perform(get("/api/reports/10")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void addsExpenseItem() throws Exception {
        when(expenseReportService.addItem(
                org.mockito.ArgumentMatchers.eq(10L),
                any(ExpenseItemDTO.class)))
                .thenReturn(reportVO());

        mockMvc.perform(post("/api/reports/10/items")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validItemRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(350.00));
    }

    @Test
    void updatesExpenseItem() throws Exception {
        when(expenseReportService.updateItem(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(2L),
                any(ExpenseItemDTO.class)))
                .thenReturn(reportVO());

        mockMvc.perform(put("/api/reports/10/items/2")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validItemRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void deletesExpenseItem() throws Exception {
        when(expenseReportService.deleteItem(10L, 2L)).thenReturn(reportVO());

        mockMvc.perform(delete("/api/reports/10/items/2")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void rejectsInvalidExpenseItem() throws Exception {
        mockMvc.perform(post("/api/reports/10/items")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expenseDate": "2026-08-28",
                                  "expenseType": "MEAL",
                                  "description": "金额无效",
                                  "amount": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(expenseReportService, never()).addItem(
                org.mockito.ArgumentMatchers.eq(10L), any());
    }

    @Test
    void submitsReport() throws Exception {
        ExpenseReportVO approving = reportVO();
        approving.setStatus("APPROVING");
        when(expenseReportService.submit(10L, "submit-key")).thenReturn(approving);

        mockMvc.perform(post("/api/reports/10/submit")
                        .header("Authorization", bearerToken())
                        .header("Idempotency-Key", "submit-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVING"));
    }

    @Test
    void withdrawsReport() throws Exception {
        ExpenseReportVO withdrawn = reportVO();
        withdrawn.setStatus("WITHDRAWN");
        when(expenseReportService.withdraw(10L)).thenReturn(withdrawn);

        mockMvc.perform(post("/api/reports/10/withdraw")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void rejectsSubmitWithoutToken() throws Exception {
        mockMvc.perform(post("/api/reports/10/submit"))
                .andExpect(status().isUnauthorized());
        verify(expenseReportService, never()).submit(eq(10L), any());
    }

    private String bearerToken() {
        String token = jwtTokenProvider.generateToken(
                2L, "employee", List.of("EMPLOYEE"));
        return "Bearer " + token;
    }

    private String validRequest() {
        return """
                {
                  "title": "8 月出差报销",
                  "description": "上海出差费用",
                  "currency": "CNY",
                  "items": [
                    {
                      "expenseDate": "2026-08-28",
                      "expenseType": "TRANSPORT",
                      "description": "高铁票",
                      "amount": 300.00,
                      "taxAmount": 0,
                      "sortOrder": 1
                    },
                    {
                      "expenseDate": "2026-08-29",
                      "expenseType": "MEAL",
                      "description": "工作餐",
                      "amount": 50.00,
                      "taxAmount": 0,
                      "sortOrder": 2
                    }
                  ]
                }
                """;
    }

    private String validItemRequest() {
        return """
                {
                  "expenseDate": "2026-08-28",
                  "expenseType": "MEAL",
                  "merchant": "测试餐厅",
                  "description": "工作餐",
                  "amount": 100.00,
                  "taxAmount": 0,
                  "sortOrder": 3
                }
                """;
    }

    private ExpenseReportVO reportVO() {
        ExpenseItemVO first = new ExpenseItemVO();
        first.setId(1L);
        first.setDescription("高铁票");
        first.setAmount(new BigDecimal("300.00"));

        ExpenseItemVO second = new ExpenseItemVO();
        second.setId(2L);
        second.setDescription("工作餐");
        second.setAmount(new BigDecimal("50.00"));

        ExpenseReportVO report = new ExpenseReportVO();
        report.setId(10L);
        report.setReportNo("ER-TEST");
        report.setApplicantId(2L);
        report.setTitle("8 月出差报销");
        report.setStatus("DRAFT");
        report.setCurrency("CNY");
        report.setTotalAmount(new BigDecimal("350.00"));
        report.setItems(List.of(first, second));
        return report;
    }
}
