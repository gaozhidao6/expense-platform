package com.shixi.expense.invoice.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 返回给前端展示和人工确认的发票识别建议。 */
public record InvoiceSuggestionVO(
        Long id,
        Long reportId,
        Long expenseItemId,
        Long attachmentId,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal amount,
        String merchantName,
        BigDecimal confidence,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
