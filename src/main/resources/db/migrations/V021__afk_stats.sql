ALTER TABLE ${table_prefix}player_stats
    ADD COLUMN afk_time BIGINT DEFAULT 0;

ALTER TABLE ${table_prefix}player_play_sessions
    ADD COLUMN afk_duration_seconds BIGINT DEFAULT 0;
