CREATE TABLE IF NOT EXISTS ${table_prefix}stats_favorites (
    player_uuid VARCHAR(36) NOT NULL,
    slot_position INT NOT NULL,
    stat_key VARCHAR(128) NOT NULL,
    PRIMARY KEY (player_uuid, slot_position)
);
