CREATE TABLE IF NOT EXISTS ${table_prefix}shop_chests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shop_id BIGINT NOT NULL,
    chest_order INT NOT NULL,
    chest_world VARCHAR(64) NOT NULL,
    chest_x INT NOT NULL,
    chest_y INT NOT NULL,
    chest_z INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_${table_prefix}shop_chests_order (shop_id, chest_order),
    UNIQUE KEY uq_${table_prefix}shop_chests_position (chest_world, chest_x, chest_y, chest_z),
    CONSTRAINT fk_${table_prefix}shop_chests_shop
        FOREIGN KEY (shop_id) REFERENCES ${table_prefix}shops (id)
        ON DELETE CASCADE
);

INSERT INTO ${table_prefix}shop_chests (
    shop_id,
    chest_order,
    chest_world,
    chest_x,
    chest_y,
    chest_z
)
SELECT
    id,
    0,
    chest_world,
    chest_x,
    chest_y,
    chest_z
FROM ${table_prefix}shops
WHERE chest_world IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM ${table_prefix}shop_chests sc
      WHERE sc.shop_id = ${table_prefix}shops.id
  );
