package com.shixi.expense.report.vo;

import java.math.BigDecimal;

import lombok.Data;

/** 报销单统计结果响应对象框架。 */
@Data
public class ExpenseReportStatisticsVO {

    /** 报销单总数量。 */
    private long totalCount;

    /** 草稿数量。 */
    private long draftCount;

    /** 审批中数量。 */
    private long approvingCount;

    /** 财务审核中的报销单数量。 */
    private long financeReviewCount;

    /** 驳回数量。 */
    private long rejectedCount;

    /** 待付款数量。 */
    private long pendingPaymentCount;

    /** 已完成数量。 */
    private long completedCount;

    /** 已撤回的报销单数量。 */
    private long withdrawnCount;

    /** 报销金额总和。 */
    private BigDecimal totalAmount;
}
