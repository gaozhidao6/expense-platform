package com.shixi.expense.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 报销单状态变化的不可变审批记录，对应 approval_record 表。
 *
 * <p>这张表只追加记录，不应通过业务接口修改或删除历史记录。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("approval_record")
public class ApprovalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报销单 ID。 */
    private Long reportId;

    /** 本次操作所属审批轮次。 */
    private Integer approvalRound;

    /** 对应的审批任务 ID，员工提交或撤回时可以为空。 */
    private Long taskId;

    /** 操作类型，例如 SUBMIT、WITHDRAW、APPROVE、REJECT、PAY。 */
    private String action;

    /** 操作前状态。 */
    private String fromStatus;

    /** 操作后状态。 */
    private String toStatus;

    /** 执行操作的用户 ID。 */
    private Long operatorId;

    /** 审批或操作意见。 */
    private String comment;

    /** 操作发生时间。 */
    private LocalDateTime operatedAt;
}
