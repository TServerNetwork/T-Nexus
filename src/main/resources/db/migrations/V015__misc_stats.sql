ALTER TABLE ${table_prefix}player_stats
    ADD COLUMN sleep_count INT DEFAULT 0;

ALTER TABLE ${table_prefix}player_stats
    ADD COLUMN portal_count INT DEFAULT 0;

ALTER TABLE ${table_prefix}player_stats
    ADD COLUMN chat_count INT DEFAULT 0;

CREATE TABLE IF NOT EXISTS ${table_prefix}projectile_stats (
    player_uuid VARCHAR(36) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, entity_type)
);
