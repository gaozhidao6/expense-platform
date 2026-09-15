package com.shixi.expense.invoice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shixi.expense.invoice.service.InvoiceRecognitionService;
import com.shixi.expense.invoice.vo.InvoiceSuggestionVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceRecognitionControllerTests {

    @Test
    void delegatesRecognitionToService() {
        InvoiceRecognitionService service =
                org.mockito.Mockito.mock(InvoiceRecognitionService.class);
        InvoiceRecognitionController controller =
                new InvoiceRecognitionController(service);
        InvoiceSuggestionVO result = new InvoiceSuggestionVO(
                10L,
                1L,
                null,
                2L,
                "INV-001",
                LocalDate.now(),
                new BigDecimal("100.00"),
                "示例商户",
                new BigDecimal("0.95"),
                "PENDING_CONFIRMATION",
                null,
                null);
        when(service.recognize(1L, 2L)).thenReturn(result);

        InvoiceSuggestionVO actual = controller.recognize(1L, 2L);

        assertEquals(result, actual);
        verify(service).recognize(1L, 2L);
    }
}
