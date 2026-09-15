package com.shixi.expense.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 用户核对、修改识别结果后提交的确认数据。 */
public record ConfirmInvoiceSuggestionRequest(
        @NotNull(message = "费用明细 ID 不能为空")
        @Positive(message = "费用明细 ID 必须为正数")
        Long expenseItemId,

        @NotBlank(message = "发票号码不能为空")
        String invoiceNumber,

        @NotNull(message = "发票日期不能为空")
        LocalDate invoiceDate,

        @NotNull(message = "发票金额不能为空")
        @DecimalMin(value = "0.01", message = "发票金额必须大于 0")
        BigDecimal amount,

        @NotBlank(message = "商户名称不能为空")
        String merchantName) {
}
