-- 取引履歴（Auditログ）
CREATE TABLE IF NOT EXISTS ${table_prefix}transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    type ENUM('DEPOSIT','WITHDRAW','PAYMENT_SENT','PAYMENT_RECEIVED','SHOP_BUY','SHOP_SELL') NOT NULL,
    amount DOUBLE NOT NULL,
    balance_after DOUBLE NOT NULL,
    description VARCHAR(255),
    counterpart_uuid VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_player (player_uuid),
    INDEX idx_created (created_at)
);

-- サーバーショップ
CREATE TABLE IF NOT EXISTS ${table_prefix}server_shops (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    material VARCHAR(64) NOT NULL,
    amount INT NOT NULL DEFAULT 1,
    buy_price DOUBLE,
    sell_price DOUBLE,
    category VARCHAR(64),
    enabled BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 個人ショップ
CREATE TABLE IF NOT EXISTS ${table_prefix}player_shops (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    amount INT NOT NULL DEFAULT 1,
    price DOUBLE NOT NULL,
    type ENUM('SELL','BUY') NOT NULL,
    stock INT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_owner (owner_uuid)
);
