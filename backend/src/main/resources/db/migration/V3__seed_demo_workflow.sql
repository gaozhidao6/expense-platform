-- 本迁移仅用于本地开发演示：补齐经理、财务账号，并准备各流程状态的报销单。
-- 所有插入均以业务唯一键判断，便于开发环境重复核对数据。

INSERT INTO sys_user
    (department_id, employee_no, username, password_hash, real_name, email, status)
SELECT d.id, 'DEMO-MANAGER', 'manager',
       '$2b$10$.RRmN.Ea5.YqNYa7jzHNZuyJIn0.Nbyj9UpWybEahmw40w5di6QkG',
       '研发部经理', 'manager@example.local', 1
FROM department d
WHERE d.code = 'R_AND_D'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'manager');

INSERT INTO sys_user
    (department_id, employee_no, username, password_hash, real_name, email, status)
SELECT d.id, 'DEMO-FINANCE', 'finance',
       '$2b$10$95.vn1r9JfyovbZng1aJHebHKkanD2XoIY6H/WSuTW5tFubvxtCum',
       '财务专员', 'finance@example.local', 1
FROM department d
WHERE d.code = 'FINANCE'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'finance');

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.code = 'MANAGER'
WHERE u.username = 'manager'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.code = 'FINANCE'
WHERE u.username = 'finance'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

UPDATE department d
JOIN sys_user u ON u.username = 'manager'
SET d.manager_user_id = u.id
WHERE d.code = 'R_AND_D'
  AND (d.manager_user_id IS NULL OR d.manager_user_id <> u.id);

-- 草稿：员工可直接编辑、提交。
INSERT INTO expense_report
    (report_no, applicant_id, department_id, title, description, total_amount, currency, status, approval_round, created_at)
SELECT 'DEMO-DRAFT-001', u.id, u.department_id, '客户拜访交通费',
       '用于测试草稿编辑、明细增删和提交审批。', 86.50, 'CNY', 'DRAFT', 1,
       DATE_SUB(NOW(3), INTERVAL 5 DAY)
FROM sys_user u
WHERE u.username = 'employee'
  AND NOT EXISTS (SELECT 1 FROM expense_report WHERE report_no = 'DEMO-DRAFT-001');

-- 经理待审批：登录 manager 后可在审批工作台通过或驳回。
INSERT INTO expense_report
    (report_no, applicant_id, department_id, title, description, total_amount, currency, status,
     current_approver_id, current_node_code, approval_round, submitted_at, created_at)
SELECT 'DEMO-MANAGER-001', e.id, e.department_id, '产品调研差旅费',
       '用于测试经理审批节点。', 1280.00, 'CNY', 'APPROVING', m.id,
       'MANAGER_APPROVAL', 1, DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 3 DAY)
FROM sys_user e JOIN sys_user m ON m.username = 'manager'
WHERE e.username = 'employee'
  AND NOT EXISTS (SELECT 1 FROM expense_report WHERE report_no = 'DEMO-MANAGER-001');

-- 财务待审核：登录 finance 后可审核。
INSERT INTO expense_report
    (report_no, applicant_id, department_id, title, description, total_amount, currency, status,
     current_approver_id, current_node_code, approval_round, submitted_at, created_at)
SELECT 'DEMO-FINANCE-001', e.id, e.department_id, '季度团队活动费用',
       '用于测试财务审核节点。', 2368.00, 'CNY', 'FINANCE_REVIEW', f.id,
       'FINANCE_REVIEW', 1, DATE_SUB(NOW(3), INTERVAL 4 DAY), DATE_SUB(NOW(3), INTERVAL 5 DAY)
FROM sys_user e JOIN sys_user f ON f.username = 'finance'
WHERE e.username = 'employee'
  AND NOT EXISTS (SELECT 1 FROM expense_report WHERE report_no = 'DEMO-FINANCE-001');

-- 待付款：登录 finance 后可从报销列表执行付款。
INSERT INTO expense_report
    (report_no, applicant_id, department_id, title, description, total_amount, currency, status,
     current_approver_id, current_node_code, approval_round, submitted_at, created_at)
SELECT 'DEMO-PAY-001', e.id, e.department_id, '办公用品采购',
       '用于测试付款及完成状态。', 599.00, 'CNY', 'PENDING_PAYMENT', f.id,
       'PAYMENT', 1, DATE_SUB(NOW(3), INTERVAL 7 DAY), DATE_SUB(NOW(3), INTERVAL 8 DAY)
FROM sys_user e JOIN sys_user f ON f.username = 'finance'
WHERE e.username = 'employee'
  AND NOT EXISTS (SELECT 1 FROM expense_report WHERE report_no = 'DEMO-PAY-001');

-- 已完成：用于列表、统计与导出测试。
INSERT INTO expense_report
    (report_no, applicant_id, department_id, title, description, total_amount, currency, status,
     approval_round, submitted_at, completed_at, created_at)
SELECT 'DEMO-DONE-001', e.id, e.department_id, '八月通讯补贴',
       '用于测试历史数据查询、统计与导出。', 200.00, 'CNY', 'COMPLETED', 1,
       DATE_SUB(NOW(3), INTERVAL 15 DAY), DATE_SUB(NOW(3), INTERVAL 10 DAY), DATE_SUB(NOW(3), INTERVAL 16 DAY)
FROM sys_user e
WHERE e.username = 'employee'
  AND NOT EXISTS (SELECT 1 FROM expense_report WHERE report_no = 'DEMO-DONE-001');

INSERT INTO expense_item
    (report_id, expense_date, expense_type, merchant, description, amount, tax_amount, sort_order)
SELECT r.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 'TRANSPORT', '城市出行', '客户拜访打车', 86.50, 0, 1
FROM expense_report r WHERE r.report_no = 'DEMO-DRAFT-001'
  AND NOT EXISTS (SELECT 1 FROM expense_item i WHERE i.report_id = r.id AND i.sort_order = 1);

INSERT INTO expense_item
    (report_id, expense_date, expense_type, merchant, description, amount, tax_amount, sort_order)
SELECT r.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 'TRAVEL', '铁路出行', '调研往返交通', 880.00, 0, 1
FROM expense_report r WHERE r.report_no = 'DEMO-MANAGER-001'
  AND NOT EXISTS (SELECT 1 FROM expense_item i WHERE i.report_id = r.id AND i.sort_order = 1);

INSERT INTO expense_item
    (report_id, expense_date, expense_type, merchant, description, amount, tax_amount, sort_order)
SELECT r.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 'MEAL', '商务酒店', '调研住宿及餐费', 400.00, 0, 2
FROM expense_report r WHERE r.report_no = 'DEMO-MANAGER-001'
  AND NOT EXISTS (SELECT 1 FROM expense_item i WHERE i.report_id = r.id AND i.sort_order = 2);

INSERT INTO expense_item
    (report_id, expense_date, expense_type, merchant, description, amount, tax_amount, sort_order)
SELECT r.id, DATE_SUB(CURDATE(), INTERVAL 6 DAY), 'MEAL', '团队餐厅', '季度团队活动', 2368.00, 0, 1
FROM expense_report r WHERE r.report_no = 'DEMO-FINANCE-001'
  AND NOT EXISTS (SELECT 1 FROM expense_item i WHERE i.report_id = r.id AND i.sort_order = 1);

INSERT INTO expense_item
    (report_id, expense_date, expense_type, merchant, description, amount, tax_amount, sort_order)
SELECT r.id, DATE_SUB(CURDATE(), INTERVAL 9 DAY), 'OFFICE', '办公商城', '键鼠及耗材', 599.00, 0, 1
FROM expense_report r WHERE r.report_no = 'DEMO-PAY-001'
  AND NOT EXISTS (SELECT 1 FROM expense_item i WHERE i.report_id = r.id AND i.sort_order = 1);

INSERT INTO expense_item
    (report_id, expense_date, expense_type, merchant, description, amount, tax_amount, sort_order)
SELECT r.id, DATE_SUB(CURDATE(), INTERVAL 18 DAY), 'OTHER', '通信运营商', '八月通讯补贴', 200.00, 0, 1
FROM expense_report r WHERE r.report_no = 'DEMO-DONE-001'
  AND NOT EXISTS (SELECT 1 FROM expense_item i WHERE i.report_id = r.id AND i.sort_order = 1);

INSERT INTO approval_task
    (report_id, approval_round, node_code, node_name, sequence_no, approver_id, status, created_at)
SELECT r.id, 1, 'MANAGER_APPROVAL', '经理审批', 1, m.id, 'PENDING', r.submitted_at
FROM expense_report r JOIN sys_user m ON m.username = 'manager'
WHERE r.report_no = 'DEMO-MANAGER-001'
  AND NOT EXISTS (SELECT 1 FROM approval_task t WHERE t.report_id = r.id AND t.node_code = 'MANAGER_APPROVAL');

INSERT INTO approval_task
    (report_id, approval_round, node_code, node_name, sequence_no, approver_id, status, acted_at, created_at)
SELECT r.id, 1, 'MANAGER_APPROVAL', '经理审批', 1, m.id, 'APPROVED',
       DATE_SUB(NOW(3), INTERVAL 3 DAY), r.submitted_at
FROM expense_report r JOIN sys_user m ON m.username = 'manager'
WHERE r.report_no = 'DEMO-FINANCE-001'
  AND NOT EXISTS (SELECT 1 FROM approval_task t WHERE t.report_id = r.id AND t.node_code = 'MANAGER_APPROVAL');

INSERT INTO approval_task
    (report_id, approval_round, node_code, node_name, sequence_no, approver_id, status, created_at)
SELECT r.id, 1, 'FINANCE_REVIEW', '财务审核', 2, f.id, 'PENDING', DATE_SUB(NOW(3), INTERVAL 3 DAY)
FROM expense_report r JOIN sys_user f ON f.username = 'finance'
WHERE r.report_no = 'DEMO-FINANCE-001'
  AND NOT EXISTS (SELECT 1 FROM approval_task t WHERE t.report_id = r.id AND t.node_code = 'FINANCE_REVIEW');

INSERT INTO approval_record
    (report_id, approval_round, task_id, action, from_status, to_status, operator_id, comment, operated_at)
SELECT r.id, 1, t.id, 'SUBMIT', 'DRAFT', 'APPROVING', e.id, NULL, r.submitted_at
FROM expense_report r JOIN sys_user e ON e.username = 'employee'
JOIN approval_task t ON t.report_id = r.id AND t.node_code = 'MANAGER_APPROVAL'
WHERE r.report_no IN ('DEMO-MANAGER-001', 'DEMO-FINANCE-001')
  AND NOT EXISTS (SELECT 1 FROM approval_record ar WHERE ar.report_id = r.id AND ar.action = 'SUBMIT');

INSERT INTO approval_record
    (report_id, approval_round, task_id, action, from_status, to_status, operator_id, comment, operated_at)
SELECT r.id, 1, t.id, 'APPROVE', 'APPROVING', 'FINANCE_REVIEW', m.id,
       '资料完整，同意报销。', t.acted_at
FROM expense_report r JOIN sys_user m ON m.username = 'manager'
JOIN approval_task t ON t.report_id = r.id AND t.node_code = 'MANAGER_APPROVAL'
WHERE r.report_no = 'DEMO-FINANCE-001'
  AND NOT EXISTS (SELECT 1 FROM approval_record ar WHERE ar.report_id = r.id AND ar.action = 'APPROVE');

