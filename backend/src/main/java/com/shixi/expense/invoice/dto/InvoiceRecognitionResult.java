package com.shixi.expense.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** AI 返回的发票识别建议，不会直接修改正式报销数据。 */
public record InvoiceRecognitionResult(
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal amount,
        String merchantName,
        BigDecimal confidence) {
}
