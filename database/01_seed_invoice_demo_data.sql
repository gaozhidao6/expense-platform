-- 可重复执行的发票识别联调数据。
-- 所有记录使用 DEMO-INVOICE-* 标识，便于识别和清理。

SET @employee_id = (
    SELECT id FROM sys_user WHERE username = 'employee' AND deleted = 0 LIMIT 1
);
SET @employee_department_id = (
    SELECT department_id FROM sys_user WHERE id = @employee_id
);
SET @manager_id = (
    SELECT id FROM sys_user WHERE username = 'manager' AND deleted = 0 LIMIT 1
);

-- 补齐报销单状态：REJECTED。
INSERT INTO expense_report (
    report_no, applicant_id, department_id, title, description,
    total_amount, currency, status, approval_round
)
SELECT
    'DEMO-REJECTED-001', @employee_id, @employee_department_id,
    '被驳回的客户招待费', '用于验证驳回后编辑和重新提交界面',
    288.00, 'CNY', 'REJECTED', 1
WHERE NOT EXISTS (
    SELECT 1 FROM expense_report WHERE report_no = 'DEMO-REJECTED-001'
);

SET @rejected_report_id = (
    SELECT id FROM expense_report WHERE report_no = 'DEMO-REJECTED-001'
);

INSERT INTO expense_item (
    report_id, expense_date, expense_type, merchant,
    description, amount, tax_amount, sort_order
)
SELECT
    @rejected_report_id, '2026-09-05', 'MEAL', '示例餐厅',
    '被驳回场景明细', 288.00, 0.00, 0
WHERE NOT EXISTS (
    SELECT 1 FROM expense_item
    WHERE report_id = @rejected_report_id
      AND description = '被驳回场景明细'
);

-- 补齐报销单状态：WITHDRAWN。
INSERT INTO expense_report (
    report_no, applicant_id, department_id, title, description,
    total_amount, currency, status, approval_round
)
SELECT
    'DEMO-WITHDRAWN-001', @employee_id, @employee_department_id,
    '已撤回的差旅补贴', '用于验证撤回状态展示',
    450.00, 'CNY', 'WITHDRAWN', 1
WHERE NOT EXISTS (
    SELECT 1 FROM expense_report WHERE report_no = 'DEMO-WITHDRAWN-001'
);

SET @withdrawn_report_id = (
    SELECT id FROM expense_report WHERE report_no = 'DEMO-WITHDRAWN-001'
);

INSERT INTO expense_item (
    report_id, expense_date, expense_type, merchant,
    description, amount, tax_amount, sort_order
)
SELECT
    @withdrawn_report_id, '2026-09-06', 'TRAVEL', '示例酒店',
    '撤回场景明细', 450.00, 0.00, 0
WHERE NOT EXISTS (
    SELECT 1 FROM expense_item
    WHERE report_id = @withdrawn_report_id
      AND description = '撤回场景明细'
);

-- 创建独立草稿，用于完整演示发票识别、确认和拒绝。
INSERT INTO expense_report (
    report_no, applicant_id, department_id, title, description,
    total_amount, currency, status, approval_round
)
SELECT
    'DEMO-INVOICE-001', @employee_id, @employee_department_id,
    '发票识别完整场景', '包含待确认、已确认和已拒绝三种识别建议',
    168.80, 'CNY', 'DRAFT', 1
WHERE NOT EXISTS (
    SELECT 1 FROM expense_report WHERE report_no = 'DEMO-INVOICE-001'
);

SET @invoice_report_id = (
    SELECT id FROM expense_report WHERE report_no = 'DEMO-INVOICE-001'
);

INSERT INTO expense_item (
    report_id, expense_date, expense_type, merchant,
    description, amount, tax_amount, sort_order
)
SELECT
    @invoice_report_id, '2026-09-08', 'OFFICE', '演示办公用品商店',
    '发票识别目标明细', 168.80, 0.00, 0
WHERE NOT EXISTS (
    SELECT 1 FROM expense_item
    WHERE report_id = @invoice_report_id
      AND description = '发票识别目标明细'
);

SET @invoice_item_id = (
    SELECT id FROM expense_item
    WHERE report_id = @invoice_report_id
      AND description = '发票识别目标明细'
    LIMIT 1
);

INSERT INTO attachment (
    report_id, original_filename, storage_path,
    file_size, content_type, sha256
)
SELECT
    @invoice_report_id, 'demo-invoice.pdf',
    'demo-invoice/demo-invoice.pdf', 477,
    'application/pdf',
    'd30ce86d1809735ef28ef84959458adace7df2a56762453e7ca00bc5a2781292'
WHERE NOT EXISTS (
    SELECT 1 FROM attachment
    WHERE report_id = @invoice_report_id
      AND sha256 = 'd30ce86d1809735ef28ef84959458adace7df2a56762453e7ca00bc5a2781292'
);

SET @invoice_attachment_id = (
    SELECT id FROM attachment
    WHERE report_id = @invoice_report_id
      AND sha256 = 'd30ce86d1809735ef28ef84959458adace7df2a56762453e7ca00bc5a2781292'
    LIMIT 1
);

-- 待确认建议：用于继续操作确认或拒绝。
INSERT INTO invoice_recognition_suggestion (
    report_id, attachment_id, invoice_number, invoice_date,
    amount, merchant_name, confidence, status, created_by, version
)
SELECT
    @invoice_report_id, @invoice_attachment_id,
    'DEMO-PENDING-001', '2026-09-08', 168.80,
    '演示办公用品商店', 0.6200,
    'PENDING_CONFIRMATION', @employee_id, 0
WHERE NOT EXISTS (
    SELECT 1 FROM invoice_recognition_suggestion
    WHERE invoice_number = 'DEMO-PENDING-001'
);

-- 已拒绝建议：用于验证历史状态展示。
INSERT INTO invoice_recognition_suggestion (
    report_id, attachment_id, invoice_number, invoice_date,
    amount, merchant_name, confidence, status, created_by,
    rejected_at, version
)
SELECT
    @invoice_report_id, @invoice_attachment_id,
    'DEMO-REJECTED-001', '2026-09-08', 160.00,
    '识别错误商户', 0.3500,
    'REJECTED', @employee_id, CURRENT_TIMESTAMP(3), 0
WHERE NOT EXISTS (
    SELECT 1 FROM invoice_recognition_suggestion
    WHERE invoice_number = 'DEMO-REJECTED-001'
);

-- 已确认建议及对应正式发票。
INSERT INTO invoice_recognition_suggestion (
    report_id, expense_item_id, attachment_id,
    invoice_number, invoice_date, amount, merchant_name,
    confidence, status, created_by, confirmed_by, confirmed_at, version
)
SELECT
    @invoice_report_id, @invoice_item_id, @invoice_attachment_id,
    'DEMO-CONFIRMED-001', '2026-09-08', 168.80,
    '演示办公用品商店', 0.9800,
    'CONFIRMED', @employee_id, @employee_id, CURRENT_TIMESTAMP(3), 1
WHERE NOT EXISTS (
    SELECT 1 FROM invoice_recognition_suggestion
    WHERE invoice_number = 'DEMO-CONFIRMED-001'
);

INSERT INTO invoice (
    expense_item_id, invoice_number, seller_name, issued_date,
    amount, file_url, file_hash, recognition_status,
    confirmed_by, confirmed_at
)
SELECT
    @invoice_item_id, 'DEMO-CONFIRMED-001',
    '演示办公用品商店', '2026-09-08', 168.80,
    'demo-invoice/demo-invoice.pdf',
    'd30ce86d1809735ef28ef84959458adace7df2a56762453e7ca00bc5a2781292',
    'CONFIRMED', @employee_id, CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (
    SELECT 1 FROM invoice WHERE invoice_number = 'DEMO-CONFIRMED-001'
);

-- 为驳回报销单补充已驳回审批任务和审计记录。
INSERT INTO approval_task (
    report_id, approval_round, node_code, node_name,
    sequence_no, approver_id, status, comment, acted_at, version
)
SELECT
    @rejected_report_id, 1, 'MANAGER_APPROVAL', '经理审批',
    10, @manager_id, 'REJECTED', '演示：票据说明不完整',
    CURRENT_TIMESTAMP(3), 0
WHERE NOT EXISTS (
    SELECT 1 FROM approval_task
    WHERE report_id = @rejected_report_id
      AND approval_round = 1
      AND node_code = 'MANAGER_APPROVAL'
      AND approver_id = @manager_id
);

SET @rejected_task_id = (
    SELECT id FROM approval_task
    WHERE report_id = @rejected_report_id
      AND node_code = 'MANAGER_APPROVAL'
    LIMIT 1
);

INSERT INTO approval_record (
    report_id, approval_round, task_id, action,
    from_status, to_status, operator_id, comment
)
SELECT
    @rejected_report_id, 1, @rejected_task_id, 'REJECT',
    'APPROVING', 'REJECTED', @manager_id, '演示驳回记录'
WHERE NOT EXISTS (
    SELECT 1 FROM approval_record
    WHERE report_id = @rejected_report_id AND action = 'REJECT'
);

-- 为撤回报销单补充取消任务和撤回审计记录。
INSERT INTO approval_task (
    report_id, approval_round, node_code, node_name,
    sequence_no, approver_id, status, comment, acted_at, version
)
SELECT
    @withdrawn_report_id, 1, 'MANAGER_APPROVAL', '经理审批',
    10, @manager_id, 'CANCELLED', '申请人撤回后取消',
    CURRENT_TIMESTAMP(3), 0
WHERE NOT EXISTS (
    SELECT 1 FROM approval_task
    WHERE report_id = @withdrawn_report_id
      AND approval_round = 1
      AND node_code = 'MANAGER_APPROVAL'
      AND approver_id = @manager_id
);

INSERT INTO approval_record (
    report_id, approval_round, action,
    from_status, to_status, operator_id, comment
)
SELECT
    @withdrawn_report_id, 1, 'WITHDRAW',
    'APPROVING', 'WITHDRAWN', @employee_id, '演示撤回记录'
WHERE NOT EXISTS (
    SELECT 1 FROM approval_record
    WHERE report_id = @withdrawn_report_id AND action = 'WITHDRAW'
);

-- 给已完成报销单补充付款审计记录。
SET @completed_report_id = (
    SELECT id FROM expense_report
    WHERE report_no = 'DEMO-DONE-001'
    LIMIT 1
);

INSERT INTO approval_record (
    report_id, approval_round, action,
    from_status, to_status, operator_id, comment
)
SELECT
    @completed_report_id, 1, 'PAY',
    'PENDING_PAYMENT', 'COMPLETED',
    (SELECT id FROM sys_user WHERE username = 'finance' LIMIT 1),
    '演示付款记录'
WHERE @completed_report_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_record
      WHERE report_id = @completed_report_id AND action = 'PAY'
  );

-- 幂等记录覆盖处理中、成功、失败以及已过期数据。
INSERT INTO idempotency_record (
    idempotency_key, user_id, business_type, business_id,
    status, response_snapshot, expires_at
)
SELECT
    'DEMO-IDEMPOTENCY-PROCESSING', @employee_id,
    'EXPENSE_REPORT_SUBMIT', @invoice_report_id,
    'PROCESSING', NULL, '2099-12-31 23:59:59.000'
WHERE NOT EXISTS (
    SELECT 1 FROM idempotency_record
    WHERE idempotency_key = 'DEMO-IDEMPOTENCY-PROCESSING'
      AND user_id = @employee_id
);

INSERT INTO idempotency_record (
    idempotency_key, user_id, business_type, business_id,
    status, response_snapshot, expires_at
)
SELECT
    'DEMO-IDEMPOTENCY-SUCCEEDED', @employee_id,
    'EXPENSE_REPORT_SUBMIT', @completed_report_id,
    'SUCCEEDED', JSON_OBJECT('message', '演示成功快照'),
    '2099-12-31 23:59:59.000'
WHERE NOT EXISTS (
    SELECT 1 FROM idempotency_record
    WHERE idempotency_key = 'DEMO-IDEMPOTENCY-SUCCEEDED'
      AND user_id = @employee_id
);

INSERT INTO idempotency_record (
    idempotency_key, user_id, business_type, business_id,
    status, response_snapshot, expires_at
)
SELECT
    'DEMO-IDEMPOTENCY-FAILED-EXPIRED', @employee_id,
    'EXPENSE_REPORT_SUBMIT', @rejected_report_id,
    'FAILED', JSON_OBJECT('message', '演示失败快照'),
    '2020-01-01 00:00:00.000'
WHERE NOT EXISTS (
    SELECT 1 FROM idempotency_record
    WHERE idempotency_key = 'DEMO-IDEMPOTENCY-FAILED-EXPIRED'
      AND user_id = @employee_id
);
