CREATE TABLE IF NOT EXISTS ${table_prefix}item_stats (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    pickup_count INT DEFAULT 0,
    drop_count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material)
);
