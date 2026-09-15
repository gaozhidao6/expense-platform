package com.shixi.expense.report.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 返回给前端的费用明细，不暴露逻辑删除字段。 */
@Data
public class ExpenseItemVO {

    private Long id;
    private LocalDate expenseDate;
    private String expenseType;
    private String merchant;
    private String description;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
