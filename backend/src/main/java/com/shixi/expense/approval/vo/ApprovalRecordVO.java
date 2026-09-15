package com.shixi.expense.approval.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 报销单审批历史响应对象。
 *
 * <p>审批记录只允许追加，VO 用于向前端展示状态变化历史。</p>
 */
@Data
public class ApprovalRecordVO {

    /** 审批记录 ID。 */
    private Long id;

    /** 报销单 ID。 */
    private Long reportId;

    /** 所属审批轮次。 */
    private Integer approvalRound;

    /** 对应审批任务 ID。 */
    private Long taskId;

    /** 操作类型，例如 SUBMIT、APPROVE、REJECT、PAY。 */
    private String action;

    /** 操作前状态。 */
    private String fromStatus;

    /** 操作后状态。 */
    private String toStatus;

    /** 操作人 ID。 */
    private Long operatorId;

    /** 审批意见或操作说明。 */
    private String comment;

    /** 操作发生时间。 */
    private LocalDateTime operatedAt;
}
