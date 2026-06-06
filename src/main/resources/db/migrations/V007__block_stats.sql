CREATE TABLE IF NOT EXISTS ${table_prefix}block_stats (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    placed_count INT DEFAULT 0,
    broken_count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material)
);
