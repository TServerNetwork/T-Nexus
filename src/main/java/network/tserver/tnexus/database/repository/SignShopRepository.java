package network.tserver.tnexus.database.repository;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.database.DatabaseManager;
import network.tserver.tnexus.manager.ShopType;
import network.tserver.tnexus.manager.SignShop;
import network.tserver.tnexus.util.BlockPosition;
import network.tserver.tnexus.util.ItemStackSerializer;

/**
 * Persists SignShop records.
 */
public final class SignShopRepository {

    private final DatabaseManager databaseManager;
    private final String tableName;
    private final String chestTableName;

    /**
     * Creates a new shop repository.
     *
     * @param databaseManager database manager
     */
    public SignShopRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.tableName = this.databaseManager.getTablePrefix() + "shops";
        this.chestTableName = this.databaseManager.getTablePrefix() + "shop_chests";
    }

    /**
     * Loads every stored shop.
     *
     * @return completion future with all shops
     */
    public CompletableFuture<List<SignShop>> loadAll() {
        String sql = "SELECT * FROM %s".formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            List<SignShop> shops = new ArrayList<>();
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql);
                 var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    shops.add(mapShop(resultSet));
                }
                loadLinkedChests(connection, shops);
                return shops;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to load sign shops", exception);
            }
        });
    }

    /**
     * Inserts a new shop and returns its generated id.
     *
     * @param shop shop to insert
     * @return completion future with generated id
     */
    public CompletableFuture<Long> insert(SignShop shop) {
        Objects.requireNonNull(shop, "shop");
        String sql = """
                INSERT INTO %s (
                    shop_type,
                    owner_uuid,
                    owner_name,
                    sign_world,
                    sign_x,
                    sign_y,
                    sign_z,
                    chest_world,
                    chest_x,
                    chest_y,
                    chest_z,
                    item_stack,
                    item_name,
                    buy_price,
                    sell_price,
                    note,
                    enabled
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                connection.setAutoCommit(false);
                bindShop(statement, shop);
                statement.executeUpdate();
                try (var generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long shopId = generatedKeys.getLong(1);
                        insertLinkedChests(connection, shopId, shop.getLinkedChestPositions());
                        connection.commit();
                        return shopId;
                    }
                }
                throw new IllegalStateException("Shop insert did not return a generated key");
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to insert sign shop", exception);
            }
        });
    }

    /**
     * Updates mutable shop state.
     *
     * @param shop updated shop
     * @return completion future
     */
    public CompletableFuture<Void> update(SignShop shop) {
        Objects.requireNonNull(shop, "shop");
        String sql = """
                UPDATE %s
                SET chest_world = ?,
                    chest_x = ?,
                    chest_y = ?,
                    chest_z = ?,
                    item_stack = ?,
                    item_name = ?,
                    buy_price = ?,
                    sell_price = ?,
                    note = ?,
                    enabled = ?
                WHERE id = ?
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                connection.setAutoCommit(false);
                BlockPosition chestPosition = shop.getLinkedChestPosition();
                if (chestPosition == null) {
                    statement.setString(1, null);
                    statement.setNull(2, java.sql.Types.INTEGER);
                    statement.setNull(3, java.sql.Types.INTEGER);
                    statement.setNull(4, java.sql.Types.INTEGER);
                } else {
                    statement.setString(1, chestPosition.worldName());
                    statement.setInt(2, chestPosition.x());
                    statement.setInt(3, chestPosition.y());
                    statement.setInt(4, chestPosition.z());
                }
                statement.setString(5, ItemStackSerializer.serialize(shop.getItemStack()));
                statement.setString(6, shop.getItemName());
                if (shop.getBuyPrice() == null) {
                    statement.setNull(7, java.sql.Types.DOUBLE);
                } else {
                    statement.setDouble(7, shop.getBuyPrice());
                }
                if (shop.getSellPrice() == null) {
                    statement.setNull(8, java.sql.Types.DOUBLE);
                } else {
                    statement.setDouble(8, shop.getSellPrice());
                }
                statement.setString(9, shop.getNote());
                statement.setBoolean(10, shop.isEnabled());
                statement.setLong(11, shop.getId());
                statement.executeUpdate();
                deleteLinkedChests(connection, shop.getId());
                insertLinkedChests(connection, shop.getId(), shop.getLinkedChestPositions());
                connection.commit();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update sign shop", exception);
            }
        });
    }

    /**
     * Deletes a shop by id.
     *
     * @param shopId shop id
     * @return completion future
     */
    public CompletableFuture<Void> delete(long shopId) {
        String sql = "DELETE FROM %s WHERE id = ?".formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                connection.setAutoCommit(false);
                deleteLinkedChests(connection, shopId);
                statement.setLong(1, shopId);
                statement.executeUpdate();
                connection.commit();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to delete sign shop", exception);
            }
        });
    }

    private void loadLinkedChests(java.sql.Connection connection, List<SignShop> shops) throws Exception {
        if (shops.isEmpty()) {
            return;
        }

        String sql = """
                SELECT shop_id, chest_world, chest_x, chest_y, chest_z
                FROM %s
                ORDER BY shop_id ASC, chest_order ASC
                """.formatted(this.chestTableName);
        java.util.Map<Long, List<BlockPosition>> positionsByShopId = new java.util.HashMap<>();
        try (var statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                positionsByShopId.computeIfAbsent(resultSet.getLong("shop_id"), ignored -> new ArrayList<>())
                        .add(new BlockPosition(
                                resultSet.getString("chest_world"),
                                resultSet.getInt("chest_x"),
                                resultSet.getInt("chest_y"),
                                resultSet.getInt("chest_z")));
            }
        }

        for (SignShop shop : shops) {
            List<BlockPosition> positions = positionsByShopId.get(shop.getId());
            if (positions != null && !positions.isEmpty()) {
                shop.setLinkedChestPositions(positions);
            }
        }
    }

    private void insertLinkedChests(java.sql.Connection connection, long shopId, List<BlockPosition> positions) throws Exception {
        if (positions.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO %s (
                    shop_id,
                    chest_order,
                    chest_world,
                    chest_x,
                    chest_y,
                    chest_z
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(this.chestTableName);
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < positions.size(); index++) {
                BlockPosition position = positions.get(index);
                statement.setLong(1, shopId);
                statement.setInt(2, index);
                statement.setString(3, position.worldName());
                statement.setInt(4, position.x());
                statement.setInt(5, position.y());
                statement.setInt(6, position.z());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void deleteLinkedChests(java.sql.Connection connection, long shopId) throws Exception {
        String sql = "DELETE FROM %s WHERE shop_id = ?".formatted(this.chestTableName);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, shopId);
            statement.executeUpdate();
        }
    }

    private void bindShop(java.sql.PreparedStatement statement, SignShop shop) throws Exception {
        statement.setString(1, shop.getType().name());
        statement.setString(2, shop.getOwnerUuid().toString());
        statement.setString(3, shop.getOwnerName());
        statement.setString(4, shop.getSignPosition().worldName());
        statement.setInt(5, shop.getSignPosition().x());
        statement.setInt(6, shop.getSignPosition().y());
        statement.setInt(7, shop.getSignPosition().z());

        BlockPosition chestPosition = shop.getLinkedChestPosition();
        if (chestPosition == null) {
            statement.setString(8, null);
            statement.setNull(9, java.sql.Types.INTEGER);
            statement.setNull(10, java.sql.Types.INTEGER);
            statement.setNull(11, java.sql.Types.INTEGER);
        } else {
            statement.setString(8, chestPosition.worldName());
            statement.setInt(9, chestPosition.x());
            statement.setInt(10, chestPosition.y());
            statement.setInt(11, chestPosition.z());
        }

        statement.setString(12, ItemStackSerializer.serialize(shop.getItemStack()));
        statement.setString(13, shop.getItemName());
        if (shop.getBuyPrice() == null) {
            statement.setNull(14, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(14, shop.getBuyPrice());
        }
        if (shop.getSellPrice() == null) {
            statement.setNull(15, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(15, shop.getSellPrice());
        }
        statement.setString(16, shop.getNote());
        statement.setBoolean(17, shop.isEnabled());
    }

    private SignShop mapShop(java.sql.ResultSet resultSet) throws Exception {
        BlockPosition chestPosition = null;
        String chestWorld = resultSet.getString("chest_world");
        if (chestWorld != null && !chestWorld.isBlank()) {
            chestPosition = new BlockPosition(
                    chestWorld,
                    resultSet.getInt("chest_x"),
                    resultSet.getInt("chest_y"),
                    resultSet.getInt("chest_z"));
        }

        Double buyPrice = resultSet.getObject("buy_price", Double.class);
        Double sellPrice = resultSet.getObject("sell_price", Double.class);
        return new SignShop(
                resultSet.getLong("id"),
                ShopType.valueOf(resultSet.getString("shop_type")),
                UUID.fromString(resultSet.getString("owner_uuid")),
                resultSet.getString("owner_name"),
                new BlockPosition(
                        resultSet.getString("sign_world"),
                        resultSet.getInt("sign_x"),
                        resultSet.getInt("sign_y"),
                        resultSet.getInt("sign_z")),
                chestPosition,
                ItemStackSerializer.deserialize(resultSet.getString("item_stack")),
                resultSet.getString("item_name"),
                buyPrice,
                sellPrice,
                resultSet.getString("note"),
                resultSet.getBoolean("enabled"));
    }
}
