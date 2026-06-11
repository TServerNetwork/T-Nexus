CREATE TABLE IF NOT EXISTS ${table_prefix}player_play_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    session_start TIMESTAMP NOT NULL,
    session_end TIMESTAMP NOT NULL,
    duration_seconds BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_player_play_sessions_player_uuid (player_uuid),
    INDEX idx_player_play_sessions_session_end (session_end)
);
