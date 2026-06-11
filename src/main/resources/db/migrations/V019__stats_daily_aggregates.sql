CREATE TABLE IF NOT EXISTS ${table_prefix}block_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    placed_count INT DEFAULT 0,
    broken_count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material, stat_date)
);

INSERT INTO ${table_prefix}block_stats_new (player_uuid, material, stat_date, placed_count, broken_count)
SELECT player_uuid, material, CURRENT_DATE, placed_count, broken_count
FROM ${table_prefix}block_stats;

DROP TABLE ${table_prefix}block_stats;

ALTER TABLE ${table_prefix}block_stats_new RENAME TO ${table_prefix}block_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}entity_damage_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    damage_dealt DOUBLE DEFAULT 0,
    damage_taken DOUBLE DEFAULT 0,
    PRIMARY KEY (player_uuid, entity_type, stat_date)
);

INSERT INTO ${table_prefix}entity_damage_stats_new (player_uuid, entity_type, stat_date, damage_dealt, damage_taken)
SELECT player_uuid, entity_type, CURRENT_DATE, damage_dealt, damage_taken
FROM ${table_prefix}entity_damage_stats;

DROP TABLE ${table_prefix}entity_damage_stats;

ALTER TABLE ${table_prefix}entity_damage_stats_new RENAME TO ${table_prefix}entity_damage_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}death_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    cause VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, cause, stat_date)
);

INSERT INTO ${table_prefix}death_stats_new (player_uuid, cause, stat_date, count)
SELECT player_uuid, cause, CURRENT_DATE, count
FROM ${table_prefix}death_stats;

DROP TABLE ${table_prefix}death_stats;

ALTER TABLE ${table_prefix}death_stats_new RENAME TO ${table_prefix}death_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}distance_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    travel_type VARCHAR(32) NOT NULL,
    stat_date DATE NOT NULL,
    distance DOUBLE DEFAULT 0,
    PRIMARY KEY (player_uuid, travel_type, stat_date)
);

INSERT INTO ${table_prefix}distance_stats_new (player_uuid, travel_type, stat_date, distance)
SELECT player_uuid, travel_type, CURRENT_DATE, distance
FROM ${table_prefix}distance_stats;

DROP TABLE ${table_prefix}distance_stats;

ALTER TABLE ${table_prefix}distance_stats_new RENAME TO ${table_prefix}distance_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}craft_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material, stat_date)
);

INSERT INTO ${table_prefix}craft_stats_new (player_uuid, material, stat_date, count)
SELECT player_uuid, material, CURRENT_DATE, count
FROM ${table_prefix}craft_stats;

DROP TABLE ${table_prefix}craft_stats;

ALTER TABLE ${table_prefix}craft_stats_new RENAME TO ${table_prefix}craft_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}smelt_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material, stat_date)
);

INSERT INTO ${table_prefix}smelt_stats_new (player_uuid, material, stat_date, count)
SELECT player_uuid, material, CURRENT_DATE, count
FROM ${table_prefix}smelt_stats;

DROP TABLE ${table_prefix}smelt_stats;

ALTER TABLE ${table_prefix}smelt_stats_new RENAME TO ${table_prefix}smelt_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}enchant_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    enchantment VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, enchantment, stat_date)
);

INSERT INTO ${table_prefix}enchant_stats_new (player_uuid, enchantment, stat_date, count)
SELECT player_uuid, enchantment, CURRENT_DATE, count
FROM ${table_prefix}enchant_stats;

DROP TABLE ${table_prefix}enchant_stats;

ALTER TABLE ${table_prefix}enchant_stats_new RENAME TO ${table_prefix}enchant_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}enchant_item_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material, stat_date)
);

INSERT INTO ${table_prefix}enchant_item_stats_new (player_uuid, material, stat_date, count)
SELECT player_uuid, material, CURRENT_DATE, count
FROM ${table_prefix}enchant_item_stats;

DROP TABLE ${table_prefix}enchant_item_stats;

ALTER TABLE ${table_prefix}enchant_item_stats_new RENAME TO ${table_prefix}enchant_item_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}harvest_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material, stat_date)
);

INSERT INTO ${table_prefix}harvest_stats_new (player_uuid, material, stat_date, count)
SELECT player_uuid, material, CURRENT_DATE, count
FROM ${table_prefix}harvest_stats;

DROP TABLE ${table_prefix}harvest_stats;

ALTER TABLE ${table_prefix}harvest_stats_new RENAME TO ${table_prefix}harvest_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}breed_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, entity_type, stat_date)
);

INSERT INTO ${table_prefix}breed_stats_new (player_uuid, entity_type, stat_date, count)
SELECT player_uuid, entity_type, CURRENT_DATE, count
FROM ${table_prefix}breed_stats;

DROP TABLE ${table_prefix}breed_stats;

ALTER TABLE ${table_prefix}breed_stats_new RENAME TO ${table_prefix}breed_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}fish_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material, stat_date)
);

INSERT INTO ${table_prefix}fish_stats_new (player_uuid, material, stat_date, count)
SELECT player_uuid, material, CURRENT_DATE, count
FROM ${table_prefix}fish_stats;

DROP TABLE ${table_prefix}fish_stats;

ALTER TABLE ${table_prefix}fish_stats_new RENAME TO ${table_prefix}fish_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}item_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    pickup_count INT DEFAULT 0,
    drop_count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, material, stat_date)
);

INSERT INTO ${table_prefix}item_stats_new (player_uuid, material, stat_date, pickup_count, drop_count)
SELECT player_uuid, material, CURRENT_DATE, pickup_count, drop_count
FROM ${table_prefix}item_stats;

DROP TABLE ${table_prefix}item_stats;

ALTER TABLE ${table_prefix}item_stats_new RENAME TO ${table_prefix}item_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}projectile_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, entity_type, stat_date)
);

INSERT INTO ${table_prefix}projectile_stats_new (player_uuid, entity_type, stat_date, count)
SELECT player_uuid, entity_type, CURRENT_DATE, count
FROM ${table_prefix}projectile_stats;

DROP TABLE ${table_prefix}projectile_stats;

ALTER TABLE ${table_prefix}projectile_stats_new RENAME TO ${table_prefix}projectile_stats;

CREATE TABLE IF NOT EXISTS ${table_prefix}kill_stats_new (
    player_uuid VARCHAR(36) NOT NULL,
    target VARCHAR(64) NOT NULL,
    stat_date DATE NOT NULL,
    count INT DEFAULT 0,
    PRIMARY KEY (player_uuid, target, stat_date)
);

INSERT INTO ${table_prefix}kill_stats_new (player_uuid, target, stat_date, count)
SELECT player_uuid, target, CURRENT_DATE, count
FROM ${table_prefix}kill_stats;

DROP TABLE ${table_prefix}kill_stats;

ALTER TABLE ${table_prefix}kill_stats_new RENAME TO ${table_prefix}kill_stats;
