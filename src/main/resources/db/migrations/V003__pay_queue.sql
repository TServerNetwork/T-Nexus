CREATE TABLE IF NOT EXISTS ${table_prefix}pay_queue (
    token VARCHAR(36) PRIMARY KEY,
    sender_uuid VARCHAR(36) NOT NULL,
    target_uuid VARCHAR(36) NOT NULL,
    amount DOUBLE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sender (sender_uuid),
    INDEX idx_expires (created_at)
);
