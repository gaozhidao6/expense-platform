package com.shixi.expense.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shixi.expense.attachment.entity.Attachment;
import com.shixi.expense.attachment.mapper.AttachmentMapper;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.exception.ErrorCode;
import com.shixi.expense.common.security.CurrentUserService;
import com.shixi.expense.invoice.dto.ConfirmInvoiceSuggestionRequest;
import com.shixi.expense.invoice.dto.InvoiceRecognitionResult;
import com.shixi.expense.invoice.entity.Invoice;
import com.shixi.expense.invoice.entity.InvoiceRecognitionSuggestion;
import com.shixi.expense.invoice.enums.InvoiceSuggestionStatus;
import com.shixi.expense.invoice.mapper.InvoiceMapper;
import com.shixi.expense.invoice.mapper.InvoiceRecognitionSuggestionMapper;
import com.shixi.expense.report.entity.ExpenseItem;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.mapper.ExpenseItemMapper;
import com.shixi.expense.report.mapper.ExpenseReportMapper;
import com.shixi.expense.report.service.ReportTotalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceSuggestionServiceTests {

    @Mock private InvoiceRecognitionSuggestionMapper suggestionMapper;
    @Mock private ExpenseReportMapper reportMapper;
    @Mock private ExpenseItemMapper itemMapper;
    @Mock private AttachmentMapper attachmentMapper;
    @Mock private InvoiceMapper invoiceMapper;
    @Mock private ReportTotalService reportTotalService;
    @Mock private CurrentUserService currentUserService;

    private InvoiceSuggestionService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(reportMapper.selectByIdForUpdate(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> reportMapper.selectById(invocation.<Long>getArgument(0)));

        service = new InvoiceSuggestionService(
                suggestionMapper,
                reportMapper,
                itemMapper,
                attachmentMapper,
                invoiceMapper,
                reportTotalService,
                currentUserService);
    }

    @Test
    void createsPendingSuggestionAndReloadsDatabaseValues() {
        ExpenseReport report = report(1L, 7L, "DRAFT");
        Attachment attachment = attachment(2L, 1L);
        InvoiceRecognitionSuggestion persisted = suggestion(10L, "PENDING_CONFIRMATION");
        when(currentUserService.requireCurrentUserId()).thenReturn(7L);
        when(reportMapper.selectById(1L)).thenReturn(report);
        when(attachmentMapper.selectById(2L)).thenReturn(attachment);
        when(suggestionMapper.insert(any(InvoiceRecognitionSuggestion.class)))
                .thenAnswer(invocation -> {
            InvoiceRecognitionSuggestion value = invocation.getArgument(0);
            value.setId(10L);
            return 1;
        });
        when(suggestionMapper.selectById(10L)).thenReturn(persisted);

        service.createSuggestion(1L, 2L, recognitionResult());

        ArgumentCaptor<InvoiceRecognitionSuggestion> captor =
                ArgumentCaptor.forClass(InvoiceRecognitionSuggestion.class);
        verify(suggestionMapper).insert(captor.capture());
        assertEquals(InvoiceSuggestionStatus.PENDING_CONFIRMATION.name(),
                captor.getValue().getStatus());
        assertEquals(7L, captor.getValue().getCreatedBy());
        verify(suggestionMapper).selectById(10L);
    }

    @Test
    void rejectsAttachmentFromAnotherReport() {
        when(currentUserService.requireCurrentUserId()).thenReturn(7L);
        when(reportMapper.selectById(1L)).thenReturn(report(1L, 7L, "DRAFT"));
        when(attachmentMapper.selectById(2L)).thenReturn(attachment(2L, 99L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createSuggestion(1L, 2L, recognitionResult()));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(suggestionMapper, never())
                .insert(any(InvoiceRecognitionSuggestion.class));
    }

    @Test
    void confirmsSuggestionUpdatesItemTotalAndFormalInvoice() {
        InvoiceRecognitionSuggestion suggestion = suggestion(10L, "PENDING_CONFIRMATION");
        ExpenseReport report = report(1L, 7L, "DRAFT");
        ExpenseItem item = new ExpenseItem();
        item.setId(3L);
        item.setReportId(1L);
        Attachment attachment = attachment(2L, 1L);
        when(currentUserService.requireCurrentUserId()).thenReturn(7L);
        when(suggestionMapper.selectById(10L))
                .thenReturn(suggestion, suggestion);
        when(reportMapper.selectById(1L)).thenReturn(report);
        when(itemMapper.selectById(3L)).thenReturn(item);
        when(itemMapper.updateById(item)).thenReturn(1);
        when(attachmentMapper.selectById(2L)).thenReturn(attachment);
        when(invoiceMapper.insert(any(Invoice.class))).thenReturn(1);
        when(suggestionMapper.updateById(suggestion)).thenReturn(1);

        service.confirmSuggestion(1L, 10L, confirmRequest());

        assertEquals(new BigDecimal("120.00"), item.getAmount());
        assertEquals("修改后的商户", item.getMerchant());
        verify(reportTotalService).recalculateAndSave(report);
        ArgumentCaptor<Invoice> invoiceCaptor =
                ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceMapper).insert(invoiceCaptor.capture());
        assertEquals("INV-002", invoiceCaptor.getValue().getInvoiceNumber());
        assertEquals(InvoiceSuggestionStatus.CONFIRMED.name(),
                suggestion.getStatus());
    }

    @Test
    void rejectsAlreadyProcessedSuggestion() {
        when(currentUserService.requireCurrentUserId()).thenReturn(7L);
        when(suggestionMapper.selectById(10L))
                .thenReturn(suggestion(10L, "CONFIRMED"));
        when(reportMapper.selectById(1L)).thenReturn(report(1L, 7L, "DRAFT"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.confirmSuggestion(1L, 10L, confirmRequest()));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(itemMapper, never()).updateById(any(ExpenseItem.class));
    }

    @Test
    void reportsOptimisticLockConflict() {
        InvoiceRecognitionSuggestion suggestion = suggestion(10L, "PENDING_CONFIRMATION");
        ExpenseReport report = report(1L, 7L, "DRAFT");
        ExpenseItem item = new ExpenseItem();
        item.setId(3L);
        item.setReportId(1L);
        when(currentUserService.requireCurrentUserId()).thenReturn(7L);
        when(suggestionMapper.selectById(10L)).thenReturn(suggestion);
        when(reportMapper.selectById(1L)).thenReturn(report);
        when(itemMapper.selectById(3L)).thenReturn(item);
        when(itemMapper.updateById(item)).thenReturn(1);
        when(attachmentMapper.selectById(2L)).thenReturn(attachment(2L, 1L));
        when(invoiceMapper.insert(any(Invoice.class))).thenReturn(1);
        when(suggestionMapper.updateById(suggestion)).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.confirmSuggestion(1L, 10L, confirmRequest()));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    @Test
    void validatesConfirmationFieldsInsideService() {
        ConfirmInvoiceSuggestionRequest invalid =
                new ConfirmInvoiceSuggestionRequest(
                        0L, " ", LocalDate.now(), BigDecimal.ZERO, " ");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.confirmSuggestion(1L, 10L, invalid));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(currentUserService, never()).requireCurrentUserId();
    }

    @Test
    void rejectsPendingSuggestionAndReloadsIt() {
        InvoiceRecognitionSuggestion suggestion = suggestion(10L, "PENDING_CONFIRMATION");
        InvoiceRecognitionSuggestion persisted = suggestion(10L, "REJECTED");
        when(currentUserService.requireCurrentUserId()).thenReturn(7L);
        when(suggestionMapper.selectById(10L))
                .thenReturn(suggestion, persisted);
        when(reportMapper.selectById(1L)).thenReturn(report(1L, 7L, "DRAFT"));
        when(suggestionMapper.updateById(suggestion)).thenReturn(1);

        assertEquals("REJECTED",
                service.rejectSuggestion(1L, 10L).status());
    }

    private ExpenseReport report(Long id, Long applicantId, String status) {
        ExpenseReport report = new ExpenseReport();
        report.setId(id);
        report.setApplicantId(applicantId);
        report.setStatus(status);
        return report;
    }

    private Attachment attachment(Long id, Long reportId) {
        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setReportId(reportId);
        attachment.setStoragePath("reports/1/invoice.pdf");
        attachment.setSha256("abc");
        return attachment;
    }

    private InvoiceRecognitionSuggestion suggestion(Long id, String status) {
        InvoiceRecognitionSuggestion suggestion = new InvoiceRecognitionSuggestion();
        suggestion.setId(id);
        suggestion.setReportId(1L);
        suggestion.setAttachmentId(2L);
        suggestion.setStatus(status);
        suggestion.setVersion(0);
        return suggestion;
    }

    private InvoiceRecognitionResult recognitionResult() {
        return new InvoiceRecognitionResult(
                "INV-001",
                LocalDate.now(),
                new BigDecimal("100.00"),
                "示例商户",
                new BigDecimal("0.95"));
    }

    private ConfirmInvoiceSuggestionRequest confirmRequest() {
        return new ConfirmInvoiceSuggestionRequest(
                3L,
                "INV-002",
                LocalDate.now(),
                new BigDecimal("120.00"),
                "修改后的商户");
    }
}
