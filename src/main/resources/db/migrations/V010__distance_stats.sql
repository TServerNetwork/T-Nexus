CREATE TABLE IF NOT EXISTS ${table_prefix}distance_stats (
    player_uuid VARCHAR(36) NOT NULL,
    travel_type VARCHAR(32) NOT NULL,
    distance DOUBLE DEFAULT 0,
    PRIMARY KEY (player_uuid, travel_type)
);
