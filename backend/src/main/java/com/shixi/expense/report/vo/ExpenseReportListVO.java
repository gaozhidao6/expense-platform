package com.shixi.expense.report.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/** 报销单列表响应对象，只返回摘要，不返回费用明细。 */
@Data
public class ExpenseReportListVO {

    private Long id;
    private String reportNo;
    private Long applicantId;
    private Long departmentId;
    private String title;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private Integer approvalRound;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
