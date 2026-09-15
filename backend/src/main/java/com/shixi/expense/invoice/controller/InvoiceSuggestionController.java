package com.shixi.expense.invoice.controller;

import com.shixi.expense.invoice.dto.ConfirmInvoiceSuggestionRequest;
import com.shixi.expense.invoice.service.InvoiceSuggestionService;
import com.shixi.expense.invoice.vo.InvoiceSuggestionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 发票识别建议的查询、确认和拒绝接口。 */
@RestController
@RequestMapping("/api/reports/{reportId}/invoice-suggestions")
public class InvoiceSuggestionController {

    private final InvoiceSuggestionService suggestionService;

    public InvoiceSuggestionController(
            InvoiceSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping("/{suggestionId}")
    public InvoiceSuggestionVO getSuggestion(
            @PathVariable Long reportId,
            @PathVariable Long suggestionId) {
        return suggestionService.getSuggestion(reportId, suggestionId);
    }

    @PutMapping("/{suggestionId}/confirm")
    public InvoiceSuggestionVO confirmSuggestion(
            @PathVariable Long reportId,
            @PathVariable Long suggestionId,
            @Valid @RequestBody ConfirmInvoiceSuggestionRequest request) {
        return suggestionService.confirmSuggestion(
                reportId,
                suggestionId,
                request);
    }

    @PostMapping("/{suggestionId}/reject")
    public InvoiceSuggestionVO rejectSuggestion(
            @PathVariable Long reportId,
            @PathVariable Long suggestionId) {
        return suggestionService.rejectSuggestion(reportId, suggestionId);
    }
}
