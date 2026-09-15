package com.shixi.expense.report.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/** CSV 导出行对象，只保留允许导出的报销单字段。 */
@Data
public class ExpenseReportExportRowVO {
    private String reportNo;
    private String title;
    private Long applicantId;
    private Long departmentId;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private Integer approvalRound;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
