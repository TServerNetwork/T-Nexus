CREATE TABLE IF NOT EXISTS ${table_prefix}harvest_stats (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material)
);

CREATE TABLE IF NOT EXISTS ${table_prefix}breed_stats (
    player_uuid VARCHAR(36) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, entity_type)
);

CREATE TABLE IF NOT EXISTS ${table_prefix}fish_stats (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material)
);
