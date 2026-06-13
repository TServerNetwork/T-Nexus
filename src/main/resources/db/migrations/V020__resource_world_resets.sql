CREATE TABLE IF NOT EXISTS ${table_prefix}resource_world_resets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    world_name VARCHAR(64) NOT NULL,
    reset_at DATETIME NOT NULL,
    next_reset_at DATETIME NOT NULL,
    status ENUM('scheduled', 'in_progress', 'completed', 'failed') NOT NULL DEFAULT 'scheduled',
    seed BIGINT NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_world_status (world_name, status),
    INDEX idx_next_reset (next_reset_at)
);
