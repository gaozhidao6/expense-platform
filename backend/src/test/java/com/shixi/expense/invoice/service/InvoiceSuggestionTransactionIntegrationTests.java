package com.shixi.expense.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shixi.expense.attachment.entity.Attachment;
import com.shixi.expense.attachment.mapper.AttachmentMapper;
import com.shixi.expense.common.security.CurrentUserService;
import com.shixi.expense.invoice.dto.ConfirmInvoiceSuggestionRequest;
import com.shixi.expense.invoice.entity.Invoice;
import com.shixi.expense.invoice.entity.InvoiceRecognitionSuggestion;
import com.shixi.expense.invoice.mapper.InvoiceMapper;
import com.shixi.expense.invoice.mapper.InvoiceRecognitionSuggestionMapper;
import com.shixi.expense.report.entity.ExpenseItem;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.mapper.ExpenseItemMapper;
import com.shixi.expense.report.mapper.ExpenseReportMapper;
import com.shixi.expense.user.entity.SysUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 使用真实 MySQL 验证确认流程中途失败时所有写操作都会回滚。 */
@SpringBootTest(properties = {
        "security.jwt.secret=ZXhwZW5zZS1wbGF0Zm9ybS1qd3QtdGVzdC1zZWNyZXQtMzItYnl0ZXM=",
        "security.jwt.expiration-seconds=7200"
})
class InvoiceSuggestionTransactionIntegrationTests {

    @Autowired private InvoiceSuggestionService suggestionService;
    @Autowired private InvoiceRecognitionSuggestionMapper suggestionMapper;
    @Autowired private ExpenseReportMapper reportMapper;
    @Autowired private ExpenseItemMapper itemMapper;
    @Autowired private AttachmentMapper attachmentMapper;
    @Autowired private InvoiceMapper invoiceMapper;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long reportId;
    private Long itemId;
    private Long attachmentId;
    private Long suggestionId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "employee", null, List.of()));
        SysUser user = currentUserService.requireCurrentUser();

        ExpenseReport report = new ExpenseReport();
        report.setReportNo("TX-" + UUID.randomUUID());
        report.setApplicantId(user.getId());
        report.setDepartmentId(user.getDepartmentId());
        report.setTitle("发票确认事务回滚测试");
        report.setTotalAmount(new BigDecimal("10.00"));
        report.setCurrency("CNY");
        report.setStatus("DRAFT");
        report.setApprovalRound(1);
        report.setVersion(0);
        reportMapper.insert(report);
        reportId = report.getId();

        ExpenseItem item = new ExpenseItem();
        item.setReportId(reportId);
        item.setExpenseDate(LocalDate.now());
        item.setExpenseType("OTHER");
        item.setMerchant("原商户");
        item.setDescription("事务回滚测试明细");
        item.setAmount(new BigDecimal("10.00"));
        item.setTaxAmount(BigDecimal.ZERO);
        item.setSortOrder(0);
        itemMapper.insert(item);
        itemId = item.getId();

        Attachment attachment = new Attachment();
        attachment.setReportId(reportId);
        attachment.setOriginalFilename("invoice.pdf");
        attachment.setStoragePath("tests/transaction/invoice.pdf");
        attachment.setFileSize(10L);
        attachment.setContentType("application/pdf");
        attachment.setSha256(
                UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""));
        attachmentMapper.insert(attachment);
        attachmentId = attachment.getId();

        InvoiceRecognitionSuggestion suggestion =
                new InvoiceRecognitionSuggestion();
        suggestion.setReportId(reportId);
        suggestion.setAttachmentId(attachmentId);
        suggestion.setInvoiceNumber("OLD-001");
        suggestion.setInvoiceDate(LocalDate.now());
        suggestion.setAmount(new BigDecimal("10.00"));
        suggestion.setMerchantName("原商户");
        suggestion.setConfidence(new BigDecimal("0.9000"));
        suggestion.setStatus("PENDING_CONFIRMATION");
        suggestion.setCreatedBy(user.getId());
        suggestion.setVersion(0);
        suggestionMapper.insert(suggestion);
        suggestionId = suggestion.getId();
    }

    @AfterEach
    void tearDown() {
        if (itemId != null) {
            jdbcTemplate.update(
                    "DELETE FROM invoice WHERE expense_item_id = ?",
                    itemId);
        }
        if (suggestionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM invoice_recognition_suggestion WHERE id = ?",
                    suggestionId);
        }
        if (attachmentId != null) {
            jdbcTemplate.update(
                    "DELETE FROM attachment WHERE id = ?",
                    attachmentId);
        }
        if (itemId != null) {
            jdbcTemplate.update("DELETE FROM expense_item WHERE id = ?", itemId);
        }
        if (reportId != null) {
            jdbcTemplate.update(
                    "DELETE FROM expense_report WHERE id = ?",
                    reportId);
        }
        SecurityContextHolder.clearContext();
    }

    @Test
    void rollsBackItemTotalAndSuggestionWhenInvoiceInsertFails() {
        // invoice_number 只有 80 个字符，超长值会在正式发票 insert 时触发数据库异常。
        String tooLongInvoiceNumber = "X".repeat(81);
        ConfirmInvoiceSuggestionRequest request =
                new ConfirmInvoiceSuggestionRequest(
                        itemId,
                        tooLongInvoiceNumber,
                        LocalDate.now(),
                        new BigDecimal("99.00"),
                        "新商户");

        assertThrows(
                RuntimeException.class,
                () -> suggestionService.confirmSuggestion(
                        reportId,
                        suggestionId,
                        request));

        ExpenseItem itemAfter = itemMapper.selectById(itemId);
        ExpenseReport reportAfter = reportMapper.selectById(reportId);
        InvoiceRecognitionSuggestion suggestionAfter =
                suggestionMapper.selectById(suggestionId);
        Long invoiceCount = invoiceMapper.selectCount(
                new LambdaQueryWrapper<Invoice>()
                        .eq(Invoice::getExpenseItemId, itemId));

        assertEquals(new BigDecimal("10.00"), itemAfter.getAmount());
        assertEquals(new BigDecimal("10.00"), reportAfter.getTotalAmount());
        assertEquals("PENDING_CONFIRMATION", suggestionAfter.getStatus());
        assertEquals(0L, invoiceCount);
    }
}
