package com.shixi.expense.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 报销单主表实体，对应数据库中的 expense_report。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("expense_report")
public class ExpenseReport {

    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外展示的报销单业务编号。 */
    private String reportNo;

    /** 申请人用户 ID。 */
    private Long applicantId;

    /** 创建报销单时申请人所在部门的快照。 */
    private Long departmentId;

    /** 报销单标题。 */
    private String title;

    /** 报销说明。 */
    private String description;

    /** 有效费用明细的金额合计，必须由后端计算。 */
    private BigDecimal totalAmount;

    /** 币种，当前默认使用 CNY。 */
    private String currency;

    /** 报销单状态，例如 DRAFT。 */
    private String status;

    /** 当前审批人 ID，草稿阶段可以为空。 */
    private Long currentApproverId;

    /** 当前审批节点编码。 */
    private String currentNodeCode;

    /** 审批轮次，驳回后重新提交时递增。 */
    private Integer approvalRound;

    /** 首次或最近一次提交时间。 */
    private LocalDateTime submittedAt;

    /** 报销流程完成时间。 */
    private LocalDateTime completedAt;

    /** MyBatis-Plus 乐观锁版本号，用于防止并发更新覆盖。 */
    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0 未删除，1 已删除。 */
    @TableLogic
    private Integer deleted;
}
