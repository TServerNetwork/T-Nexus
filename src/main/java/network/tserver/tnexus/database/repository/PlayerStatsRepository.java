package network.tserver.tnexus.database.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.database.DatabaseManager;

/**
 * Persists aggregate player session statistics.
 */
public final class PlayerStatsRepository {

    private final DatabaseManager databaseManager;
    private final String tableName;
    private final String deathStatsTableName;
    private final String distanceStatsTableName;
    private final String blockStatsTableName;

    /**
     * Creates a new player stats repository.
     *
     * @param databaseManager database manager
     */
    public PlayerStatsRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.tableName = this.databaseManager.getTablePrefix() + "player_stats";
        this.deathStatsTableName = this.databaseManager.getTablePrefix() + "death_stats";
        this.distanceStatsTableName = this.databaseManager.getTablePrefix() + "distance_stats";
        this.blockStatsTableName = this.databaseManager.getTablePrefix() + "block_stats";
    }

    /**
     * Ensures the player stats row exists without overwriting the original first-login timestamp.
     *
     * @param playerId player id
     * @param firstLoginAt first login timestamp candidate
     * @return completion future
     */
    public CompletableFuture<Void> ensurePlayerExists(UUID playerId, Instant firstLoginAt) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(firstLoginAt, "firstLoginAt");
        String sql = """
                INSERT INTO %s (player_uuid, first_login)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE player_uuid = player_uuid
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setTimestamp(2, Timestamp.from(firstLoginAt));
                statement.executeUpdate();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to ensure player stats row exists", exception);
            }
        });
    }

    /**
     * Adds the given session duration to the stored total play time.
     *
     * @param playerId player id
     * @param playTimeSeconds session duration in seconds
     * @return completion future
     */
    public CompletableFuture<Void> addPlayTime(UUID playerId, long playTimeSeconds) {
        Objects.requireNonNull(playerId, "playerId");
        String sql = """
                INSERT INTO %s (player_uuid, play_time)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE play_time = play_time + VALUES(play_time)
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, playTimeSeconds);
                statement.executeUpdate();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player play time", exception);
            }
        });
    }

    /**
     * Adds aggregate distance statistics for one or more players.
     *
     * @param totalDistances total distance by player id
     * @param travelDistances travel-type distance by player id
     * @return completion future
     */
    public CompletableFuture<Void> addDistanceStats(
            Map<UUID, Double> totalDistances,
            Map<UUID, Map<String, Double>> travelDistances) {
        Objects.requireNonNull(totalDistances, "totalDistances");
        Objects.requireNonNull(travelDistances, "travelDistances");
        if (totalDistances.isEmpty() && travelDistances.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String totalDistanceSql = """
                INSERT INTO %s (player_uuid, distance)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE distance = distance + VALUES(distance)
                """.formatted(this.tableName);
        String travelDistanceSql = """
                INSERT INTO %s (player_uuid, travel_type, distance)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE distance = distance + VALUES(distance)
                """.formatted(this.distanceStatsTableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try (var totalDistanceStatement = connection.prepareStatement(totalDistanceSql);
                     var travelDistanceStatement = connection.prepareStatement(travelDistanceSql)) {
                    addTotalDistanceBatch(totalDistanceStatement, totalDistances);
                    addTravelDistanceBatch(travelDistanceStatement, travelDistances);
                    totalDistanceStatement.executeBatch();
                    travelDistanceStatement.executeBatch();
                    connection.commit();
                    return null;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player distance stats", exception);
            }
        });
    }

    /**
     * Adds aggregate block placement and break statistics for one or more players.
     *
     * @param totalPlacedCounts total placed counts by player id
     * @param totalBrokenCounts total broken counts by player id
     * @param materialStats material deltas by player id
     * @return completion future
     */
    public CompletableFuture<Void> addBlockStats(
            Map<UUID, Integer> totalPlacedCounts,
            Map<UUID, Integer> totalBrokenCounts,
            Map<UUID, Map<String, BlockStatsDelta>> materialStats) {
        Objects.requireNonNull(totalPlacedCounts, "totalPlacedCounts");
        Objects.requireNonNull(totalBrokenCounts, "totalBrokenCounts");
        Objects.requireNonNull(materialStats, "materialStats");
        if (totalPlacedCounts.isEmpty() && totalBrokenCounts.isEmpty() && materialStats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String totalSql = """
                INSERT INTO %s (player_uuid, blocks_placed, blocks_broken)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    blocks_placed = blocks_placed + VALUES(blocks_placed),
                    blocks_broken = blocks_broken + VALUES(blocks_broken)
                """.formatted(this.tableName);
        String materialSql = """
                INSERT INTO %s (player_uuid, material, placed_count, broken_count)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    placed_count = placed_count + VALUES(placed_count),
                    broken_count = broken_count + VALUES(broken_count)
                """.formatted(this.blockStatsTableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try (var totalStatement = connection.prepareStatement(totalSql);
                     var materialStatement = connection.prepareStatement(materialSql)) {
                    addBlockTotalBatch(totalStatement, totalPlacedCounts, totalBrokenCounts);
                    addBlockMaterialBatch(materialStatement, materialStats);
                    totalStatement.executeBatch();
                    materialStatement.executeBatch();
                    connection.commit();
                    return null;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player block stats", exception);
            }
        });
    }

    /**
     * Increments the total death counter for the given player.
     *
     * @param playerId player id
     * @return completion future
     */
    public CompletableFuture<Void> incrementDeaths(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return incrementPlayerStat(playerId, "deaths");
    }

    /**
     * Increments the total respawn counter for the given player.
     *
     * @param playerId player id
     * @return completion future
     */
    public CompletableFuture<Void> incrementRespawns(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return incrementPlayerStat(playerId, "respawns");
    }

    /**
     * Increments the death cause counter for the given player.
     *
     * @param playerId player id
     * @param cause death cause label
     * @return completion future
     */
    public CompletableFuture<Void> incrementDeathCause(UUID playerId, String cause) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(cause, "cause");
        String sql = """
                INSERT INTO %s (player_uuid, cause, count)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE count = count + 1
                """.formatted(this.deathStatsTableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, cause);
                statement.executeUpdate();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player death cause stats", exception);
            }
        });
    }

    private CompletableFuture<Void> incrementPlayerStat(UUID playerId, String columnName) {
        String sql = """
                INSERT INTO %s (player_uuid, %s)
                VALUES (?, 1)
                ON DUPLICATE KEY UPDATE %s = %s + 1
                """.formatted(this.tableName, columnName, columnName, columnName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player stat column " + columnName, exception);
            }
        });
    }

    private void addTotalDistanceBatch(
            java.sql.PreparedStatement statement,
            Map<UUID, Double> totalDistances) throws Exception {
        for (Map.Entry<UUID, Double> entry : totalDistances.entrySet()) {
            statement.setString(1, entry.getKey().toString());
            statement.setDouble(2, entry.getValue());
            statement.addBatch();
        }
    }

    private void addTravelDistanceBatch(
            java.sql.PreparedStatement statement,
            Map<UUID, Map<String, Double>> travelDistances) throws Exception {
        for (Map.Entry<UUID, Map<String, Double>> playerEntry : travelDistances.entrySet()) {
            for (Map.Entry<String, Double> travelEntry : playerEntry.getValue().entrySet()) {
                statement.setString(1, playerEntry.getKey().toString());
                statement.setString(2, travelEntry.getKey());
                statement.setDouble(3, travelEntry.getValue());
                statement.addBatch();
            }
        }
    }

    private void addBlockTotalBatch(
            java.sql.PreparedStatement statement,
            Map<UUID, Integer> totalPlacedCounts,
            Map<UUID, Integer> totalBrokenCounts) throws Exception {
        java.util.HashSet<UUID> playerIds = new java.util.HashSet<>(totalPlacedCounts.keySet());
        playerIds.addAll(totalBrokenCounts.keySet());
        for (UUID playerId : playerIds) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, totalPlacedCounts.getOrDefault(playerId, 0));
            statement.setInt(3, totalBrokenCounts.getOrDefault(playerId, 0));
            statement.addBatch();
        }
    }

    private void addBlockMaterialBatch(
            java.sql.PreparedStatement statement,
            Map<UUID, Map<String, BlockStatsDelta>> materialStats) throws Exception {
        for (Map.Entry<UUID, Map<String, BlockStatsDelta>> playerEntry : materialStats.entrySet()) {
            for (Map.Entry<String, BlockStatsDelta> materialEntry : playerEntry.getValue().entrySet()) {
                statement.setString(1, playerEntry.getKey().toString());
                statement.setString(2, materialEntry.getKey());
                statement.setInt(3, materialEntry.getValue().placedCount());
                statement.setInt(4, materialEntry.getValue().brokenCount());
                statement.addBatch();
            }
        }
    }
}
