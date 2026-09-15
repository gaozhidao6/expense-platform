package com.shixi.expense.invoice.controller;

import com.shixi.expense.invoice.service.InvoiceRecognitionService;
import com.shixi.expense.invoice.vo.InvoiceSuggestionVO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 发票识别接口。 */
@RestController
@RequestMapping("/api/reports/{reportId}/attachments/{attachmentId}/recognition")
public class InvoiceRecognitionController {

    private final InvoiceRecognitionService recognitionService;

    public InvoiceRecognitionController(
            InvoiceRecognitionService recognitionService) {
        this.recognitionService = recognitionService;
    }

    /** 触发一次识别并返回建议结果。 */
    @PostMapping
    public InvoiceSuggestionVO recognize(
            @PathVariable Long reportId,
            @PathVariable Long attachmentId) {
        return recognitionService.recognize(reportId, attachmentId);
    }
}
