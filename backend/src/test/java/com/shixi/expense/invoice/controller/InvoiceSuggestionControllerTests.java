package com.shixi.expense.invoice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shixi.expense.invoice.dto.ConfirmInvoiceSuggestionRequest;
import com.shixi.expense.invoice.service.InvoiceSuggestionService;
import com.shixi.expense.invoice.vo.InvoiceSuggestionVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InvoiceSuggestionControllerTests {

    @Test
    void delegatesQueryConfirmAndReject() {
        InvoiceSuggestionService service =
                Mockito.mock(InvoiceSuggestionService.class);
        InvoiceSuggestionController controller =
                new InvoiceSuggestionController(service);
        InvoiceSuggestionVO vo = suggestionVO();
        ConfirmInvoiceSuggestionRequest request =
                new ConfirmInvoiceSuggestionRequest(
                        3L,
                        "INV-001",
                        LocalDate.now(),
                        new BigDecimal("100.00"),
                        "示例商户");
        when(service.getSuggestion(1L, 10L)).thenReturn(vo);
        when(service.confirmSuggestion(1L, 10L, request)).thenReturn(vo);
        when(service.rejectSuggestion(1L, 10L)).thenReturn(vo);

        assertEquals(vo, controller.getSuggestion(1L, 10L));
        assertEquals(vo, controller.confirmSuggestion(1L, 10L, request));
        assertEquals(vo, controller.rejectSuggestion(1L, 10L));
        verify(service).getSuggestion(1L, 10L);
        verify(service).confirmSuggestion(1L, 10L, request);
        verify(service).rejectSuggestion(1L, 10L);
    }

    private InvoiceSuggestionVO suggestionVO() {
        return new InvoiceSuggestionVO(
                10L, 1L, 3L, 2L, "INV-001", LocalDate.now(),
                new BigDecimal("100.00"), "示例商户",
                new BigDecimal("0.95"), "PENDING_CONFIRMATION", null, null);
    }
}
