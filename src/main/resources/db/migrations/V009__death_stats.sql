CREATE TABLE IF NOT EXISTS ${table_prefix}death_stats (
    player_uuid VARCHAR(36) NOT NULL,
    cause VARCHAR(64) NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, cause)
);
