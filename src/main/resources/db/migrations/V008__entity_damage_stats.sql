CREATE TABLE IF NOT EXISTS ${table_prefix}entity_damage_stats (
    player_uuid VARCHAR(36) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    damage_dealt DOUBLE DEFAULT 0,
    damage_taken DOUBLE DEFAULT 0,
    PRIMARY KEY (player_uuid, entity_type)
);
