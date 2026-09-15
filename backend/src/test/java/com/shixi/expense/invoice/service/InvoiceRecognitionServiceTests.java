package com.shixi.expense.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shixi.expense.attachment.service.AttachmentService;
import com.shixi.expense.attachment.vo.AttachmentDownload;
import com.shixi.expense.invoice.client.InvoiceRecognitionClient;
import com.shixi.expense.invoice.dto.InvoiceRecognitionResult;
import com.shixi.expense.invoice.vo.InvoiceSuggestionVO;
import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

@ExtendWith(MockitoExtension.class)
class InvoiceRecognitionServiceTests {

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private InvoiceRecognitionClient recognitionClient;

    @Mock
    private InvoiceSuggestionService suggestionService;

    private InvoiceRecognitionService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceRecognitionService(
                attachmentService,
                recognitionClient,
                suggestionService);
    }

    @Test
    void returnsValidRecognitionSuggestion() throws Exception {
        InvoiceRecognitionResult result = validResult();
        when(attachmentService.download(1L, 2L))
                .thenReturn(download());
        when(recognitionClient.recognize(any(), eq("application/pdf")))
                .thenReturn(result);
        InvoiceSuggestionVO suggestion = suggestionVO(10L, result);
        when(suggestionService.createSuggestion(1L, 2L, result))
                .thenReturn(suggestion);

        InvoiceSuggestionVO actual = service.recognize(1L, 2L);

        assertEquals(suggestion, actual);
        verify(recognitionClient).recognize(any(), eq("application/pdf"));
        verify(suggestionService).createSuggestion(1L, 2L, result);
    }

    @Test
    void rejectsInvalidIdsBeforeReadingAttachment() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.recognize(null, 2L));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.recognize(1L, 0L));

        verify(attachmentService, never()).download(any(), any());
    }

    @Test
    void rejectsNullRecognitionResult() throws Exception {
        when(attachmentService.download(1L, 2L))
                .thenReturn(download());
        when(recognitionClient.recognize(any(), eq("application/pdf")))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> service.recognize(1L, 2L));
    }

    @Test
    void rejectsNonPositiveAmount() throws Exception {
        when(attachmentService.download(1L, 2L))
                .thenReturn(download());
        InvoiceRecognitionResult result = new InvoiceRecognitionResult(
                "INV-001",
                LocalDate.now(),
                BigDecimal.ZERO,
                "示例商户",
                new BigDecimal("0.95"));
        when(recognitionClient.recognize(any(), eq("application/pdf")))
                .thenReturn(result);

        assertThrows(
                IllegalStateException.class,
                () -> service.recognize(1L, 2L));
    }

    @Test
    void rejectsConfidenceOutsideZeroAndOne() throws Exception {
        when(attachmentService.download(1L, 2L))
                .thenReturn(download());
        InvoiceRecognitionResult result = new InvoiceRecognitionResult(
                "INV-001",
                LocalDate.now(),
                new BigDecimal("100.00"),
                "示例商户",
                new BigDecimal("1.01"));
        when(recognitionClient.recognize(any(), eq("application/pdf")))
                .thenReturn(result);

        assertThrows(
                IllegalStateException.class,
                () -> service.recognize(1L, 2L));
    }

    @Test
    void rejectsBlankInvoiceNumber() throws Exception {
        assertInvalidResult(new InvoiceRecognitionResult(
                " ",
                LocalDate.now(),
                new BigDecimal("100.00"),
                "示例商户",
                new BigDecimal("0.95")));
    }

    @Test
    void rejectsBlankMerchantName() throws Exception {
        assertInvalidResult(new InvoiceRecognitionResult(
                "INV-001",
                LocalDate.now(),
                new BigDecimal("100.00"),
                " ",
                new BigDecimal("0.95")));
    }

    @Test
    void rejectsOverlongInvoiceNumber() throws Exception {
        assertInvalidResult(new InvoiceRecognitionResult(
                "X".repeat(81),
                LocalDate.now(),
                new BigDecimal("100.00"),
                "示例商户",
                new BigDecimal("0.95")));
    }

    @Test
    void rejectsOverlongMerchantName() throws Exception {
        assertInvalidResult(new InvoiceRecognitionResult(
                "INV-001",
                LocalDate.now(),
                new BigDecimal("100.00"),
                "商".repeat(201),
                new BigDecimal("0.95")));
    }

    @Test
    void rejectsDuplicateRecognitionWhileSameAttachmentIsRunning() throws Exception {
        InvoiceRecognitionResult result = validResult();
        when(attachmentService.download(1L, 2L)).thenReturn(download());
        when(recognitionClient.recognize(any(), eq("application/pdf")))
                .thenAnswer(invocation -> {
                    IllegalStateException duplicate = assertThrows(
                            IllegalStateException.class,
                            () -> service.recognize(1L, 2L));
                    assertEquals("该附件正在识别中，请勿重复提交", duplicate.getMessage());
                    return result;
                });
        when(suggestionService.createSuggestion(1L, 2L, result))
                .thenReturn(suggestionVO(10L, result));

        service.recognize(1L, 2L);

        verify(attachmentService).download(1L, 2L);
    }

    @Test
    void rejectsFutureInvoiceDate() throws Exception {
        assertInvalidResult(new InvoiceRecognitionResult(
                "INV-001",
                LocalDate.now().plusDays(1),
                new BigDecimal("100.00"),
                "示例商户",
                new BigDecimal("0.95")));
    }

    @Test
    void acceptsConfidenceBoundaryValues() throws Exception {
        when(attachmentService.download(1L, 2L))
                .thenReturn(download());
        InvoiceRecognitionResult zeroConfidence = new InvoiceRecognitionResult(
                "INV-001",
                LocalDate.now(),
                new BigDecimal("100.00"),
                "示例商户",
                BigDecimal.ZERO);
        InvoiceRecognitionResult oneConfidence = new InvoiceRecognitionResult(
                "INV-002",
                LocalDate.now(),
                new BigDecimal("100.00"),
                "示例商户",
                BigDecimal.ONE);
        when(recognitionClient.recognize(any(), eq("application/pdf")))
                .thenReturn(zeroConfidence, oneConfidence);

        InvoiceSuggestionVO zeroSuggestion =
                suggestionVO(10L, zeroConfidence);
        InvoiceSuggestionVO oneSuggestion =
                suggestionVO(11L, oneConfidence);
        when(suggestionService.createSuggestion(1L, 2L, zeroConfidence))
                .thenReturn(zeroSuggestion);
        when(suggestionService.createSuggestion(1L, 2L, oneConfidence))
                .thenReturn(oneSuggestion);

        assertEquals(zeroSuggestion, service.recognize(1L, 2L));
        assertEquals(oneSuggestion, service.recognize(1L, 2L));
    }

    @Test
    void wrapsRecognitionIOException() throws Exception {
        when(attachmentService.download(1L, 2L))
                .thenReturn(download());
        when(recognitionClient.recognize(any(), eq("application/pdf")))
                .thenThrow(new IOException("recognition failed"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.recognize(1L, 2L));

        assertEquals("读取或识别发票附件失败", exception.getMessage());
    }

    private void assertInvalidResult(InvoiceRecognitionResult result)
            throws Exception {
        when(attachmentService.download(1L, 2L))
                .thenReturn(download());
        when(recognitionClient.recognize(any(), eq("application/pdf")))
                .thenReturn(result);

        assertThrows(
                IllegalStateException.class,
                () -> service.recognize(1L, 2L));
    }

    private AttachmentDownload download() {
        byte[] content = "invoice".getBytes();
        return new AttachmentDownload(
                new ByteArrayResource(content),
                "invoice.pdf",
                "application/pdf",
                content.length);
    }

    private InvoiceRecognitionResult validResult() {
        return new InvoiceRecognitionResult(
                "INV-001",
                LocalDate.now(),
                new BigDecimal("100.00"),
                "示例商户",
                new BigDecimal("0.95"));
    }

    private InvoiceSuggestionVO suggestionVO(
            Long id,
            InvoiceRecognitionResult result) {
        return new InvoiceSuggestionVO(
                id,
                1L,
                null,
                2L,
                result.invoiceNumber(),
                result.invoiceDate(),
                result.amount(),
                result.merchantName(),
                result.confidence(),
                "PENDING_CONFIRMATION",
                null,
                null);
    }
}
