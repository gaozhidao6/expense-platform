package com.shixi.expense.report.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 返回给前端的报销单详情。 */
@Data
public class ExpenseReportVO {

    private Long id;
    private String reportNo;
    private Long applicantId;
    private Long departmentId;
    private String title;
    private String description;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private Integer approvalRound;
    private Integer version;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ExpenseItemVO> items = new ArrayList<>();
}
