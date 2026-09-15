package com.shixi.expense.approval.enums;

/** 审批任务状态，数据库保存枚举名称字符串。 */
public enum ApprovalTaskStatus {

    /** 等待当前审批人处理。 */
    PENDING,

    /** 当前审批节点已通过。 */
    APPROVED,

    /** 当前审批节点已驳回。 */
    REJECTED,

    /** 由于撤回或流程变化而取消。 */
    CANCELLED
}
