CREATE TABLE IF NOT EXISTS ${table_prefix}kill_stats (
    player_uuid VARCHAR(36) NOT NULL,
    target VARCHAR(64) NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, target)
);
