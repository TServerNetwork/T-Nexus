CREATE TABLE IF NOT EXISTS ${table_prefix}player_stats (
    player_uuid VARCHAR(36) PRIMARY KEY,
    play_time BIGINT DEFAULT 0,
    deaths INT DEFAULT 0,
    respawns INT DEFAULT 0,
    distance DOUBLE DEFAULT 0,
    blocks_placed INT DEFAULT 0,
    blocks_broken INT DEFAULT 0,
    first_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
