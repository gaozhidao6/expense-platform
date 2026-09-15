CREATE TABLE invoice_recognition_suggestion (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    report_id BIGINT UNSIGNED NOT NULL COMMENT '识别时所属报销单',
    expense_item_id BIGINT UNSIGNED NULL COMMENT '用户准备写入的费用明细，可以在确认前为空',
    attachment_id BIGINT UNSIGNED NOT NULL COMMENT '本次识别使用的附件',
    invoice_number VARCHAR(80) NOT NULL,
    invoice_date DATE NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    merchant_name VARCHAR(200) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL COMMENT '0 到 1 之间的 AI 置信度',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_CONFIRMATION'
        COMMENT 'PENDING_CONFIRMATION, CONFIRMED, REJECTED',
    created_by BIGINT UNSIGNED NOT NULL,
    confirmed_by BIGINT UNSIGNED NULL,
    confirmed_at DATETIME(3) NULL,
    rejected_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_suggestion_report_status (report_id, status, created_at),
    KEY idx_suggestion_attachment (attachment_id, created_at),
    KEY idx_suggestion_item (expense_item_id),
    CONSTRAINT fk_suggestion_report FOREIGN KEY (report_id)
        REFERENCES expense_report (id),
    CONSTRAINT fk_suggestion_item FOREIGN KEY (expense_item_id)
        REFERENCES expense_item (id),
    CONSTRAINT fk_suggestion_attachment FOREIGN KEY (attachment_id)
        REFERENCES attachment (id),
    CONSTRAINT fk_suggestion_creator FOREIGN KEY (created_by)
        REFERENCES sys_user (id),
    CONSTRAINT fk_suggestion_confirmer FOREIGN KEY (confirmed_by)
        REFERENCES sys_user (id),
    CONSTRAINT chk_suggestion_amount CHECK (amount > 0),
    CONSTRAINT chk_suggestion_confidence CHECK (
        confidence >= 0 AND confidence <= 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 发票识别建议历史';
