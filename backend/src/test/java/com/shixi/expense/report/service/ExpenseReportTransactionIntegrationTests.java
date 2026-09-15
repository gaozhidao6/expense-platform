package com.shixi.expense.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shixi.expense.report.dto.ExpenseItemDTO;
import com.shixi.expense.report.dto.ExpenseReportCreateDTO;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.mapper.ExpenseReportMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 使用本地 MySQL 验证报销单和明细保存失败时会整体回滚。 */
@SpringBootTest(properties = {
        "security.jwt.secret=ZXhwZW5zZS1wbGF0Zm9ybS1qd3QtdGVzdC1zZWNyZXQtMzItYnl0ZXM=",
        "security.jwt.expiration-seconds=7200"
})
class ExpenseReportTransactionIntegrationTests {

    @Autowired
    private ExpenseReportService expenseReportService;

    @Autowired
    private ExpenseReportMapper expenseReportMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "employee", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rollsBackReportAndPreviousItemsWhenSecondItemFails() {
        String title = "rollback-test-" + UUID.randomUUID();
        ExpenseReportCreateDTO request = new ExpenseReportCreateDTO();
        request.setTitle(title);
        request.setCurrency("CNY");
        request.setItems(List.of(
                item("第一条有效明细", new BigDecimal("10.00")),
                item("第二条故意失败的明细", null)));

        long before = countReportsByTitle(title);

        // Service 直接调用绕过 Controller 参数校验，让第二条明细触发数据库 NOT NULL 约束。
        assertThrows(
                RuntimeException.class,
                () -> expenseReportService.createReport(request));

        long after = countReportsByTitle(title);
        assertEquals(before, after, "事务回滚后不应留下报销单主记录");
    }

    private ExpenseItemDTO item(String description, BigDecimal amount) {
        ExpenseItemDTO item = new ExpenseItemDTO();
        item.setExpenseDate(LocalDate.of(2026, 8, 28));
        item.setExpenseType("OTHER");
        item.setDescription(description);
        item.setAmount(amount);
        item.setTaxAmount(BigDecimal.ZERO);
        return item;
    }

    private long countReportsByTitle(String title) {
        return expenseReportMapper.selectCount(
                new LambdaQueryWrapper<ExpenseReport>()
                        .eq(ExpenseReport::getTitle, title));
    }
}
