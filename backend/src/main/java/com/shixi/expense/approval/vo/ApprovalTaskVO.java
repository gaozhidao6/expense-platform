package com.shixi.expense.approval.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 当前用户待审批任务的响应对象。
 *
 * <p>VO 只用于返回前端，不直接暴露 ApprovalTask 实体，
 * 同时补充报销单摘要，方便待办列表展示。</p>
 */
@Data
public class ApprovalTaskVO {

    /** 审批任务 ID。 */
    private Long id;

    /** 报销单 ID。 */
    private Long reportId;

    /** 报销单编号。 */
    private String reportNo;

    /** 报销单标题。 */
    private String reportTitle;

    /** 报销单申请人 ID。 */
    private Long applicantId;

    /** 报销单总金额。 */
    private BigDecimal totalAmount;

    /** 审批轮次。 */
    private Integer approvalRound;

    /** 审批节点编码。 */
    private String nodeCode;

    /** 审批节点名称。 */
    private String nodeName;

    /** 任务处理人 ID。 */
    private Long approverId;

    /** 任务状态，通常为 PENDING。 */
    private String status;

    /** 任务创建时间。 */
    private LocalDateTime createdAt;
}
