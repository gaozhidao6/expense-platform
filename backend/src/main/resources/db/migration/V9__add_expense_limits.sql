CREATE TABLE expense_policy (
    expense_type VARCHAR(50) NOT NULL PRIMARY KEY,
    limit_amount DECIMAL(15,2) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_expense_policy_limit CHECK (limit_amount > 0)
);

-- Demo limits in CNY, per expense item; these are not real company policies.
INSERT INTO expense_policy (expense_type, limit_amount) VALUES
    ('TRAVEL', 1000.00), ('MEAL', 300.00), ('TRANSPORT', 200.00),
    ('OFFICE', 500.00), ('OTHER', 500.00);

ALTER TABLE expense_item
    ADD COLUMN over_limit_reason VARCHAR(500) NULL,
    ADD COLUMN applied_limit_amount DECIMAL(15,2) NULL COMMENT 'Limit snapshot of the latest submitted approval round; NULL means no limit snapshot';
