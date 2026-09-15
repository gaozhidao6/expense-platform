package com.shixi.expense.report.enums;

/**
 * 报销单状态枚举。
 *
 * <p>数据库仍保存枚举名称字符串；业务代码使用该枚举可以减少状态拼写错误。</p>
 */
public enum ExpenseReportStatus {

    /** 草稿，可以编辑报销单和费用明细。 */
    DRAFT,

    /** 经理审批中。 */
    APPROVING,

    /** 财务审核中。 */
    FINANCE_REVIEW,

    /** 等待财务付款。 */
    PENDING_PAYMENT,

    /** 流程已完成。 */
    COMPLETED,

    /** 审批被驳回。 */
    REJECTED,

    /** 申请人主动撤回。 */
    WITHDRAWN
}
