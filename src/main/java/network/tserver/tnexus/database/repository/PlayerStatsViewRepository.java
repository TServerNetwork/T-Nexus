package network.tserver.tnexus.database.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.database.DatabaseManager;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import org.jetbrains.annotations.Nullable;

/**
 * Loads player statistics snapshots for the stats viewer GUI.
 */
public final class PlayerStatsViewRepository {

    private static final List<Integer> FAVORITE_SLOTS =
            List.of(28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);

    private final DatabaseManager databaseManager;
    private final String playerStatsTableName;
    private final String blockStatsTableName;
    private final String entityDamageStatsTableName;
    private final String killStatsTableName;
    private final String craftStatsTableName;
    private final String smeltStatsTableName;
    private final String enchantStatsTableName;
    private final String enchantItemStatsTableName;
    private final String harvestStatsTableName;
    private final String breedStatsTableName;
    private final String fishStatsTableName;
    private final String itemStatsTableName;
    private final String projectileStatsTableName;
    private final String transactionsTableName;
    private final String favoritesTableName;

    /**
     * Creates a new repository.
     *
     * @param databaseManager database manager
     */
    public PlayerStatsViewRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        String tablePrefix = this.databaseManager.getTablePrefix();
        this.playerStatsTableName = tablePrefix + "player_stats";
        this.blockStatsTableName = tablePrefix + "block_stats";
        this.entityDamageStatsTableName = tablePrefix + "entity_damage_stats";
        this.killStatsTableName = tablePrefix + "kill_stats";
        this.craftStatsTableName = tablePrefix + "craft_stats";
        this.smeltStatsTableName = tablePrefix + "smelt_stats";
        this.enchantStatsTableName = tablePrefix + "enchant_stats";
        this.enchantItemStatsTableName = tablePrefix + "enchant_item_stats";
        this.harvestStatsTableName = tablePrefix + "harvest_stats";
        this.breedStatsTableName = tablePrefix + "breed_stats";
        this.fishStatsTableName = tablePrefix + "fish_stats";
        this.itemStatsTableName = tablePrefix + "item_stats";
        this.projectileStatsTableName = tablePrefix + "projectile_stats";
        this.transactionsTableName = tablePrefix + "transactions";
        this.favoritesTableName = tablePrefix + "stats_favorites";
    }

    /**
     * Loads the raw stats data needed by the stats GUI.
     *
     * @param viewerId viewer UUID used for favorites
     * @param targetId stats target UUID
     * @param periodStart optional period lower bound
     * @return completion future
     */
    public CompletableFuture<RawPlayerStatsData> loadSnapshot(
            UUID viewerId,
            UUID targetId,
            @Nullable Instant periodStart) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(targetId, "targetId");
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection()) {
                return new RawPlayerStatsData(
                        queryPlayerSummary(connection, targetId),
                        queryBlockStats(connection, targetId),
                        queryEntityDamageStats(connection, targetId),
                        queryIntegerStats(connection, this.killStatsTableName, "target", targetId),
                        queryIntegerStats(connection, this.craftStatsTableName, "material", targetId),
                        queryIntegerStats(connection, this.smeltStatsTableName, "material", targetId),
                        queryIntegerStats(connection, this.enchantStatsTableName, "enchantment", targetId),
                        queryIntegerStats(connection, this.enchantItemStatsTableName, "material", targetId),
                        queryIntegerStats(connection, this.harvestStatsTableName, "material", targetId),
                        queryIntegerStats(connection, this.breedStatsTableName, "entity_type", targetId),
                        queryIntegerStats(connection, this.fishStatsTableName, "material", targetId),
                        queryItemStats(connection, targetId),
                        queryIntegerStats(connection, this.projectileStatsTableName, "entity_type", targetId),
                        queryTransactionStats(connection, targetId, periodStart),
                        queryFavorites(connection, viewerId));
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to load player stats view snapshot", exception);
            }
        });
    }

    /**
     * Toggles a favorite stat key for the given viewer.
     *
     * @param viewerId viewer UUID
     * @param statKey stat key
     * @return completion future
     */
    public CompletableFuture<FavoriteMutation> toggleFavorite(UUID viewerId, String statKey) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(statKey, "statKey");
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Map<Integer, String> favorites = queryFavorites(connection, viewerId);
                    for (Map.Entry<Integer, String> entry : favorites.entrySet()) {
                        if (entry.getValue().equals(statKey)) {
                            deleteFavorite(connection, viewerId, entry.getKey());
                            favorites.remove(entry.getKey());
                            connection.commit();
                            return new FavoriteMutation(FavoriteMutationStatus.REMOVED, entry.getKey(), favorites);
                        }
                    }

                    Integer freeSlot = findFirstFreeSlot(favorites);
                    if (freeSlot == null) {
                        connection.rollback();
                        return new FavoriteMutation(FavoriteMutationStatus.FULL, -1, favorites);
                    }

                    insertFavorite(connection, viewerId, freeSlot, statKey);
                    favorites.put(freeSlot, statKey);
                    connection.commit();
                    return new FavoriteMutation(FavoriteMutationStatus.ADDED, freeSlot, favorites);
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to toggle player stats favorite", exception);
            }
        });
    }

    private PlayerSummary queryPlayerSummary(java.sql.Connection connection, UUID playerId) throws Exception {
        String sql = """
                SELECT play_time, deaths, respawns, distance, first_login, sleep_count, portal_count, chat_count, brew_count
                FROM %s
                WHERE player_uuid = ?
                """.formatted(this.playerStatsTableName);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new PlayerSummary(0L, 0, 0, 0.0D, null, 0, 0, 0, 0);
                }
                Timestamp firstLogin = resultSet.getTimestamp("first_login");
                return new PlayerSummary(
                        resultSet.getLong("play_time"),
                        resultSet.getInt("deaths"),
                        resultSet.getInt("respawns"),
                        resultSet.getDouble("distance"),
                        firstLogin == null ? null : firstLogin.toInstant(),
                        resultSet.getInt("sleep_count"),
                        resultSet.getInt("portal_count"),
                        resultSet.getInt("chat_count"),
                        resultSet.getInt("brew_count"));
            }
        }
    }

    private Map<String, BlockStatsDelta> queryBlockStats(java.sql.Connection connection, UUID playerId) throws Exception {
        String sql = """
                SELECT material, placed_count, broken_count
                FROM %s
                WHERE player_uuid = ?
                """.formatted(this.blockStatsTableName);
        Map<String, BlockStatsDelta> stats = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    stats.put(
                            resultSet.getString("material"),
                            new BlockStatsDelta(
                                    resultSet.getInt("placed_count"),
                                    resultSet.getInt("broken_count")));
                }
            }
        }
        return stats;
    }

    private Map<String, EntityDamageDelta> queryEntityDamageStats(
            java.sql.Connection connection,
            UUID playerId) throws Exception {
        String sql = """
                SELECT entity_type, damage_dealt, damage_taken
                FROM %s
                WHERE player_uuid = ?
                """.formatted(this.entityDamageStatsTableName);
        Map<String, EntityDamageDelta> stats = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    stats.put(
                            resultSet.getString("entity_type"),
                            new EntityDamageDelta(
                                    resultSet.getDouble("damage_dealt"),
                                    resultSet.getDouble("damage_taken")));
                }
            }
        }
        return stats;
    }

    private Map<String, Integer> queryIntegerStats(
            java.sql.Connection connection,
            String tableName,
            String keyColumn,
            UUID playerId) throws Exception {
        String sql = """
                SELECT %s, count
                FROM %s
                WHERE player_uuid = ?
                """.formatted(keyColumn, tableName);
        Map<String, Integer> stats = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    stats.put(resultSet.getString(1), resultSet.getInt("count"));
                }
            }
        }
        return stats;
    }

    private Map<String, ItemStatsDelta> queryItemStats(java.sql.Connection connection, UUID playerId) throws Exception {
        String sql = """
                SELECT material, pickup_count, drop_count
                FROM %s
                WHERE player_uuid = ?
                """.formatted(this.itemStatsTableName);
        Map<String, ItemStatsDelta> stats = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    stats.put(
                            resultSet.getString("material"),
                            new ItemStatsDelta(
                                    resultSet.getInt("pickup_count"),
                                    resultSet.getInt("drop_count")));
                }
            }
        }
        return stats;
    }

    private TransactionSummary queryTransactionStats(
            java.sql.Connection connection,
            UUID playerId,
            @Nullable Instant periodStart) throws Exception {
        String sql = periodStart == null
                ? """
                SELECT type, COUNT(*) AS entry_count, COALESCE(SUM(amount), 0) AS total_amount
                FROM %s
                WHERE player_uuid = ?
                GROUP BY type
                """.formatted(this.transactionsTableName)
                : """
                SELECT type, COUNT(*) AS entry_count, COALESCE(SUM(amount), 0) AS total_amount
                FROM %s
                WHERE player_uuid = ? AND created_at >= ?
                GROUP BY type
                """.formatted(this.transactionsTableName);

        Map<TransactionType, Integer> counts = new HashMap<>();
        Map<TransactionType, Double> amounts = new HashMap<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            if (periodStart != null) {
                statement.setTimestamp(2, Timestamp.from(periodStart));
            }
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    TransactionType type = TransactionType.valueOf(resultSet.getString("type"));
                    counts.put(type, resultSet.getInt("entry_count"));
                    amounts.put(type, resultSet.getDouble("total_amount"));
                }
            }
        }

        double totalVolume = amounts.values().stream().mapToDouble(Double::doubleValue).sum();
        return new TransactionSummary(counts, amounts, totalVolume);
    }

    private Map<Integer, String> queryFavorites(java.sql.Connection connection, UUID viewerId) throws Exception {
        String sql = """
                SELECT slot_position, stat_key
                FROM %s
                WHERE player_uuid = ?
                ORDER BY slot_position ASC
                """.formatted(this.favoritesTableName);
        Map<Integer, String> favorites = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, viewerId.toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    favorites.put(resultSet.getInt("slot_position"), resultSet.getString("stat_key"));
                }
            }
        }
        return favorites;
    }

    private void deleteFavorite(java.sql.Connection connection, UUID viewerId, int slotPosition) throws Exception {
        String sql = """
                DELETE FROM %s
                WHERE player_uuid = ? AND slot_position = ?
                """.formatted(this.favoritesTableName);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, viewerId.toString());
            statement.setInt(2, slotPosition);
            statement.executeUpdate();
        }
    }

    private void insertFavorite(java.sql.Connection connection, UUID viewerId, int slotPosition, String statKey)
            throws Exception {
        String sql = """
                INSERT INTO %s (player_uuid, slot_position, stat_key)
                VALUES (?, ?, ?)
                """.formatted(this.favoritesTableName);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, viewerId.toString());
            statement.setInt(2, slotPosition);
            statement.setString(3, statKey);
            statement.executeUpdate();
        }
    }

    private @Nullable Integer findFirstFreeSlot(Map<Integer, String> favorites) {
        for (Integer slot : FAVORITE_SLOTS) {
            if (!favorites.containsKey(slot)) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Raw database-backed player stats data.
     *
     * @param playerSummary player summary row
     * @param blockStats block stats by material
     * @param entityDamageStats combat damage stats by entity identifier
     * @param killStats kill counts by target identifier
     * @param craftStats crafted item counts
     * @param smeltStats smelted item counts
     * @param enchantStats enchantment counts
     * @param enchantItemStats enchanted item counts
     * @param harvestStats harvested item counts
     * @param breedStats bred entity counts
     * @param fishStats caught fish counts
     * @param itemStats pickup/drop stats
     * @param projectileStats projectile launch counts
     * @param transactionSummary transaction summary for the selected period
     * @param favorites favorite stat keys keyed by slot position
     */
    public record RawPlayerStatsData(
            PlayerSummary playerSummary,
            Map<String, BlockStatsDelta> blockStats,
            Map<String, EntityDamageDelta> entityDamageStats,
            Map<String, Integer> killStats,
            Map<String, Integer> craftStats,
            Map<String, Integer> smeltStats,
            Map<String, Integer> enchantStats,
            Map<String, Integer> enchantItemStats,
            Map<String, Integer> harvestStats,
            Map<String, Integer> breedStats,
            Map<String, Integer> fishStats,
            Map<String, ItemStatsDelta> itemStats,
            Map<String, Integer> projectileStats,
            TransactionSummary transactionSummary,
            Map<Integer, String> favorites) {
    }

    /**
     * Flattened player summary counters.
     *
     * @param playTimeSeconds total play time in seconds
     * @param deaths total deaths
     * @param respawns total respawns
     * @param distance total distance travelled
     * @param firstLogin first login timestamp
     * @param sleepCount bed sleep count
     * @param portalCount portal usage count
     * @param chatCount chat message count
     * @param brewCount brew count
     */
    public record PlayerSummary(
            long playTimeSeconds,
            int deaths,
            int respawns,
            double distance,
            @Nullable Instant firstLogin,
            int sleepCount,
            int portalCount,
            int chatCount,
            int brewCount) {
    }

    /**
     * Transaction aggregates for the selected period.
     *
     * @param counts counts by transaction type
     * @param amounts summed amounts by transaction type
     * @param totalVolume total transaction volume across all transaction types
     */
    public record TransactionSummary(
            Map<TransactionType, Integer> counts,
            Map<TransactionType, Double> amounts,
            double totalVolume) {
    }

    /**
     * Favorite toggle result.
     *
     * @param status mutation status
     * @param slotPosition affected slot position, or {@code -1}
     * @param favorites updated favorites snapshot
     */
    public record FavoriteMutation(
            FavoriteMutationStatus status,
            int slotPosition,
            Map<Integer, String> favorites) {
    }

    /**
     * Favorite mutation outcomes.
     */
    public enum FavoriteMutationStatus {
        ADDED,
        REMOVED,
        FULL
    }
}
