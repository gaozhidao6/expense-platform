ALTER TABLE idempotency_record ADD COLUMN owner_token VARCHAR(36) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE notification_outbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_key VARCHAR(100) NOT NULL,
    recipient_id BIGINT UNSIGNED NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    claim_token VARCHAR(36) NULL,
    lease_until DATETIME(3) NULL,
    last_error VARCHAR(200) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    sent_at DATETIME(3) NULL,
    UNIQUE KEY uk_outbox_event_recipient (message_key, recipient_id),
    KEY idx_outbox_due (status, available_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attachment_cleanup_job (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    storage_path VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    claim_token VARCHAR(36) NULL,
    lease_until DATETIME(3) NULL,
    last_error VARCHAR(200) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_cleanup_due (status, available_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
