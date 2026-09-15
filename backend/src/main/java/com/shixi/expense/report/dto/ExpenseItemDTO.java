package com.shixi.expense.report.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/** 创建或修改费用明细时接收的参数。 */
@Data
public class ExpenseItemDTO {

    @NotNull(message = "expenseDate must not be null")
    @PastOrPresent(message = "expenseDate must not be in the future")
    private LocalDate expenseDate;

    @NotBlank(message = "expenseType must not be blank")
    @Size(max = 50, message = "expenseType must not exceed 50 characters")
    private String expenseType;

    @Size(max = 150, message = "merchant must not exceed 150 characters")
    private String merchant;

    @NotBlank(message = "description must not be blank")
    @Size(max = 500, message = "description must not exceed 500 characters")
    private String description;

    /** 金额必须大于 0，整数最多 13 位，小数最多 2 位。 */
    @NotNull(message = "amount must not be null")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Digits(integer = 13, fraction = 2, message = "amount format is invalid")
    private BigDecimal amount;

    @DecimalMin(value = "0.00", message = "taxAmount must not be negative")
    @Digits(integer = 13, fraction = 2, message = "taxAmount format is invalid")
    private BigDecimal taxAmount;

    private Integer sortOrder;
}
