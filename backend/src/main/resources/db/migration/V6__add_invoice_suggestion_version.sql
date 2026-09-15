ALTER TABLE invoice_recognition_suggestion
    ADD COLUMN version INT NOT NULL DEFAULT 0
        COMMENT '乐观锁版本，防止同一建议被并发确认或拒绝'
        AFTER rejected_at;
