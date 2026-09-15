CREATE TABLE department (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    parent_id BIGINT UNSIGNED NULL COMMENT 'Parent department',
    name VARCHAR(100) NOT NULL COMMENT 'Department name',
    code VARCHAR(50) NOT NULL COMMENT 'Department code',
    manager_user_id BIGINT UNSIGNED NULL COMMENT 'Manager user id',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_department_code (code),
    KEY idx_department_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Department';

CREATE TABLE sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    department_id BIGINT UNSIGNED NULL,
    employee_no VARCHAR(50) NOT NULL COMMENT 'Employee number',
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    email VARCHAR(120) NULL,
    mobile VARCHAR(30) NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    last_login_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_employee_no (employee_no),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email),
    KEY idx_user_department_status (department_id, status),
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System user';

CREATE TABLE sys_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System role';

CREATE TABLE sys_user_role (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User role mapping';

CREATE TABLE expense_report (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    report_no VARCHAR(40) NOT NULL COMMENT 'Business report number',
    applicant_id BIGINT UNSIGNED NOT NULL,
    department_id BIGINT UNSIGNED NOT NULL COMMENT 'Snapshot of applicant department',
    title VARCHAR(150) NOT NULL,
    description VARCHAR(1000) NULL,
    total_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, APPROVING, FINANCE_REVIEW, PENDING_PAYMENT, COMPLETED, REJECTED, WITHDRAWN',
    current_approver_id BIGINT UNSIGNED NULL,
    current_node_code VARCHAR(50) NULL,
    approval_round INT NOT NULL DEFAULT 1 COMMENT 'Increments after rejected report is resubmitted',
    submitted_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_expense_report_no (report_no),
    KEY idx_report_applicant_created (applicant_id, created_at),
    KEY idx_report_department_status (department_id, status),
    KEY idx_report_approver_status (current_approver_id, status),
    CONSTRAINT fk_report_applicant FOREIGN KEY (applicant_id) REFERENCES sys_user (id),
    CONSTRAINT fk_report_department FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Expense report';

CREATE TABLE expense_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    report_id BIGINT UNSIGNED NOT NULL,
    expense_date DATE NOT NULL,
    expense_type VARCHAR(50) NOT NULL COMMENT 'TRAVEL, MEAL, TRANSPORT, OFFICE, OTHER',
    merchant VARCHAR(150) NULL,
    description VARCHAR(500) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    tax_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_item_report (report_id, deleted),
    KEY idx_item_type_date (expense_type, expense_date),
    CONSTRAINT fk_item_report FOREIGN KEY (report_id) REFERENCES expense_report (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Expense report item';

CREATE TABLE invoice (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    expense_item_id BIGINT UNSIGNED NOT NULL,
    invoice_code VARCHAR(50) NULL,
    invoice_number VARCHAR(80) NULL,
    seller_name VARCHAR(200) NULL,
    issued_date DATE NULL,
    amount DECIMAL(15,2) NULL,
    file_url VARCHAR(500) NOT NULL,
    file_hash CHAR(64) NULL COMMENT 'SHA-256 for duplicate detection',
    recognition_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PROCESSING, SUCCEEDED, FAILED, CONFIRMED',
    ai_raw_result JSON NULL,
    confirmed_by BIGINT UNSIGNED NULL,
    confirmed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_code_number (invoice_code, invoice_number),
    KEY idx_invoice_item (expense_item_id),
    KEY idx_invoice_file_hash (file_hash),
    CONSTRAINT fk_invoice_item FOREIGN KEY (expense_item_id) REFERENCES expense_item (id),
    CONSTRAINT fk_invoice_confirmer FOREIGN KEY (confirmed_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Invoice and AI recognition result';

CREATE TABLE approval_rule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    node_code VARCHAR(50) NOT NULL,
    node_name VARCHAR(100) NOT NULL,
    sequence_no INT NOT NULL,
    min_amount DECIMAL(15,2) NULL,
    max_amount DECIMAL(15,2) NULL,
    approver_type VARCHAR(30) NOT NULL COMMENT 'DEPARTMENT_MANAGER, ROLE, SPECIFIED_USER',
    approver_value VARCHAR(100) NULL COMMENT 'Role code or user id',
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_node_sequence (node_code, sequence_no),
    KEY idx_rule_enabled_amount (enabled, min_amount, max_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Approval routing rule';

CREATE TABLE approval_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    report_id BIGINT UNSIGNED NOT NULL,
    approval_round INT NOT NULL,
    node_code VARCHAR(50) NOT NULL,
    node_name VARCHAR(100) NOT NULL,
    sequence_no INT NOT NULL,
    approver_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, APPROVED, REJECTED, CANCELLED',
    comment VARCHAR(1000) NULL,
    acted_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_round_node_approver (report_id, approval_round, node_code, approver_id),
    KEY idx_task_approver_status (approver_id, status, created_at),
    KEY idx_task_report_round (report_id, approval_round, sequence_no),
    CONSTRAINT fk_task_report FOREIGN KEY (report_id) REFERENCES expense_report (id),
    CONSTRAINT fk_task_approver FOREIGN KEY (approver_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Current and historical approval task';

CREATE TABLE approval_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    report_id BIGINT UNSIGNED NOT NULL,
    approval_round INT NOT NULL,
    task_id BIGINT UNSIGNED NULL,
    action VARCHAR(30) NOT NULL COMMENT 'SUBMIT, APPROVE, REJECT, WITHDRAW, PAY',
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    operator_id BIGINT UNSIGNED NOT NULL,
    comment VARCHAR(1000) NULL,
    operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_record_report_time (report_id, operated_at),
    KEY idx_record_operator_time (operator_id, operated_at),
    CONSTRAINT fk_record_report FOREIGN KEY (report_id) REFERENCES expense_report (id),
    CONSTRAINT fk_record_task FOREIGN KEY (task_id) REFERENCES approval_task (id),
    CONSTRAINT fk_record_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable approval audit trail';

CREATE TABLE notification (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(30) NOT NULL COMMENT 'APPROVAL_TODO, APPROVED, REJECTED, PAID',
    title VARCHAR(150) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    business_type VARCHAR(50) NOT NULL DEFAULT 'EXPENSE_REPORT',
    business_id BIGINT UNSIGNED NOT NULL,
    message_key VARCHAR(100) NOT NULL COMMENT 'MQ consumer idempotency key',
    is_read TINYINT NOT NULL DEFAULT 0,
    read_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_message_recipient (message_key, recipient_id),
    KEY idx_notification_recipient_read (recipient_id, is_read, created_at),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='In-app notification';

CREATE TABLE operation_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    operator_id BIGINT UNSIGNED NULL,
    operation VARCHAR(100) NOT NULL,
    business_type VARCHAR(50) NULL,
    business_id BIGINT UNSIGNED NULL,
    request_id VARCHAR(80) NULL,
    http_method VARCHAR(10) NULL,
    request_path VARCHAR(300) NULL,
    ip_address VARCHAR(64) NULL,
    result VARCHAR(20) NOT NULL COMMENT 'SUCCESS or FAILURE',
    error_message VARCHAR(1000) NULL,
    duration_ms BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_operation_business (business_type, business_id),
    KEY idx_operation_operator_time (operator_id, created_at),
    KEY idx_operation_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Operation audit log';

CREATE TABLE idempotency_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(100) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL COMMENT 'PROCESSING, SUCCEEDED, FAILED',
    response_snapshot JSON NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_key_user (idempotency_key, user_id),
    KEY idx_idempotency_expiry (expires_at),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Database idempotency fallback';

INSERT INTO sys_role (code, name, description) VALUES
    ('EMPLOYEE', 'Employee', 'Create and view own expense reports'),
    ('MANAGER', 'Manager', 'Approve department expense reports'),
    ('FINANCE', 'Finance', 'Review and pay approved expense reports'),
    ('ADMIN', 'Administrator', 'Manage organization and system configuration');

INSERT INTO department (name, code, sort_order) VALUES
    ('Head Office', 'HEAD_OFFICE', 0),
    ('Research and Development', 'R_AND_D', 10),
    ('Finance', 'FINANCE', 20);

INSERT INTO approval_rule
    (name, node_code, node_name, sequence_no, min_amount, max_amount, approver_type, approver_value)
VALUES
    ('Default manager approval', 'MANAGER_APPROVAL', 'Manager Approval', 10, 0.00, NULL, 'DEPARTMENT_MANAGER', NULL),
    ('Default finance review', 'FINANCE_REVIEW', 'Finance Review', 20, 0.00, NULL, 'ROLE', 'FINANCE');

