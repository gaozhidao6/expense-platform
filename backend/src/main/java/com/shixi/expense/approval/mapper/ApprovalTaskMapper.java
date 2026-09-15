package com.shixi.expense.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.approval.entity.ApprovalTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

/** 审批任务数据访问入口。 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {

    /**
     * 查询真正有效的待办。
     *
     * <p>任务、报销单状态、当前节点、当前处理人和审批轮次必须全部一致，
     * 防止已撤回或旧轮次任务继续出现在审批工作台。</p>
     */
    @Select("""
            SELECT t.*
            FROM approval_task t
            JOIN expense_report r ON r.id = t.report_id AND r.deleted = 0
            WHERE t.approver_id = #{approverId}
              AND t.status = 'PENDING'
              AND t.approval_round = r.approval_round
              AND t.approver_id = r.current_approver_id
              AND t.node_code = r.current_node_code
              AND ((t.node_code = 'MANAGER_APPROVAL' AND r.status = 'APPROVING')
                   OR (t.node_code = 'FINANCE_REVIEW' AND r.status = 'FINANCE_REVIEW'))
            ORDER BY t.created_at ASC
            """)
    List<ApprovalTask> selectValidPendingByApproverId(
            @Param("approverId") Long approverId);

    /** 撤回时一次性取消该审批轮次尚未处理的任务。 */
    @Update("""
            UPDATE approval_task
            SET status = 'CANCELLED',
                comment = #{comment},
                acted_at = #{actedAt},
                version = version + 1
            WHERE report_id = #{reportId}
              AND approval_round = #{approvalRound}
              AND status = 'PENDING'
            """)
    int cancelPendingTasks(
            @Param("reportId") Long reportId,
            @Param("approvalRound") Integer approvalRound,
            @Param("comment") String comment,
            @Param("actedAt") LocalDateTime actedAt);
}
