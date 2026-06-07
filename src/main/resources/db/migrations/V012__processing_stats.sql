CREATE TABLE IF NOT EXISTS ${table_prefix}smelt_stats (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material)
);

CREATE TABLE IF NOT EXISTS ${table_prefix}enchant_stats (
    player_uuid VARCHAR(36) NOT NULL,
    enchantment VARCHAR(64) NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, enchantment)
);

CREATE TABLE IF NOT EXISTS ${table_prefix}enchant_item_stats (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material)
);

ALTER TABLE ${table_prefix}player_stats
    ADD COLUMN IF NOT EXISTS brew_count INT DEFAULT 0;
