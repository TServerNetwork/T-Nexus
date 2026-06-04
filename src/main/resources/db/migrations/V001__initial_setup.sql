CREATE TABLE IF NOT EXISTS ${table_prefix}schema_version (
    version INT NOT NULL,
    description VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (version)
);
