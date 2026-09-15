package com.shixi.expense.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 当前审批任务实体，对应 approval_task 表。
 *
 * <p>任务记录当前节点由谁处理；历史变化仍写入 ApprovalRecord。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("approval_task")
public class ApprovalTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属报销单 ID。 */
    private Long reportId;

    /** 所属审批轮次，驳回重提后会变化。 */
    private Integer approvalRound;

    /** 审批节点编码，例如 MANAGER_APPROVAL、FINANCE_REVIEW。 */
    private String nodeCode;

    /** 审批节点名称。 */
    private String nodeName;

    /** 节点在流程中的顺序。 */
    private Integer sequenceNo;

    /** 当前任务处理人 ID。 */
    private Long approverId;

    /** PENDING、APPROVED、REJECTED 或 CANCELLED。 */
    private String status;

    /** 审批意见。 */
    private String comment;

    /** 实际处理时间。 */
    private LocalDateTime actedAt;

    /** 乐观锁版本，防止同一任务被并发处理两次。 */
    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
