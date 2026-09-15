-- 终态或已撤回/驳回的报销单不应继续保留当前处理人和节点。
UPDATE expense_report
SET current_approver_id = NULL,
    current_node_code = NULL
WHERE status IN ('WITHDRAWN', 'REJECTED', 'COMPLETED')
  AND (current_approver_id IS NOT NULL OR current_node_code IS NOT NULL);

-- 取消历史遗留的无效待办：任务必须与报销单状态、轮次、节点及处理人完全一致。
UPDATE approval_task t
JOIN expense_report r ON r.id = t.report_id
SET t.status = 'CANCELLED',
    t.comment = COALESCE(t.comment, '系统修复：关联报销单已不在该审批节点'),
    t.acted_at = COALESCE(t.acted_at, CURRENT_TIMESTAMP(3)),
    t.version = t.version + 1
WHERE t.status = 'PENDING'
  AND (
      r.deleted <> 0
      OR t.approval_round <> r.approval_round
      OR r.current_approver_id IS NULL
      OR t.approver_id <> r.current_approver_id
      OR r.current_node_code IS NULL
      OR t.node_code <> r.current_node_code
      OR (t.node_code = 'MANAGER_APPROVAL' AND r.status <> 'APPROVING')
      OR (t.node_code = 'FINANCE_REVIEW' AND r.status <> 'FINANCE_REVIEW')
      OR t.node_code NOT IN ('MANAGER_APPROVAL', 'FINANCE_REVIEW')
  );
