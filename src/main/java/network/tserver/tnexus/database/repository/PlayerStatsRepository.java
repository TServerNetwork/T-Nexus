package network.tserver.tnexus.database.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import network.tserver.tnexus.database.DatabaseManager;
import network.tserver.tnexus.util.PlayerStatsResetTarget;

/**
 * Persists aggregate player session statistics.
 */
public final class PlayerStatsRepository {

    private final DatabaseManager databaseManager;
    private final String tableName;
    private final String deathStatsTableName;
    private final String distanceStatsTableName;
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
    private final String playSessionsTableName;

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
        this.entityDamageStatsTableName = this.databaseManager.getTablePrefix() + "entity_damage_stats";
        this.killStatsTableName = this.databaseManager.getTablePrefix() + "kill_stats";
        this.craftStatsTableName = this.databaseManager.getTablePrefix() + "craft_stats";
        this.smeltStatsTableName = this.databaseManager.getTablePrefix() + "smelt_stats";
        this.enchantStatsTableName = this.databaseManager.getTablePrefix() + "enchant_stats";
        this.enchantItemStatsTableName = this.databaseManager.getTablePrefix() + "enchant_item_stats";
        this.harvestStatsTableName = this.databaseManager.getTablePrefix() + "harvest_stats";
        this.breedStatsTableName = this.databaseManager.getTablePrefix() + "breed_stats";
        this.fishStatsTableName = this.databaseManager.getTablePrefix() + "fish_stats";
        this.itemStatsTableName = this.databaseManager.getTablePrefix() + "item_stats";
        this.projectileStatsTableName = this.databaseManager.getTablePrefix() + "projectile_stats";
        this.playSessionsTableName = this.databaseManager.getTablePrefix() + "player_play_sessions";
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
     * Adds the given AFK duration to the stored total AFK time.
     *
     * @param playerId player id
     * @param afkTimeSeconds AFK duration in seconds
     * @return completion future
     */
    public CompletableFuture<Void> addAfkTime(UUID playerId, long afkTimeSeconds) {
        Objects.requireNonNull(playerId, "playerId");
        String sql = """
                INSERT INTO %s (player_uuid, afk_time)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE afk_time = afk_time + VALUES(afk_time)
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, afkTimeSeconds);
                statement.executeUpdate();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player AFK time", exception);
            }
        });
    }

    /**
     * Records a completed player session in both aggregate and historical tables.
     *
     * @param playerId player id
     * @param sessionStart session start time
     * @param sessionEnd session end time
     * @param playTimeSeconds session duration in seconds
     * @return completion future
     */
    public CompletableFuture<Void> recordPlaySession(
            UUID playerId,
            Instant sessionStart,
            Instant sessionEnd,
            long playTimeSeconds) {
        return recordPlaySession(playerId, sessionStart, sessionEnd, playTimeSeconds, 0L, false);
    }

    /**
     * Records a completed player session in both aggregate and historical tables.
     *
     * @param playerId player id
     * @param sessionStart session start time
     * @param sessionEnd session end time
     * @param playTimeSeconds session duration in seconds
     * @param afkTimeSeconds session AFK duration in seconds
     * @return completion future
     */
    public CompletableFuture<Void> recordPlaySession(
            UUID playerId,
            Instant sessionStart,
            Instant sessionEnd,
            long playTimeSeconds,
            long afkTimeSeconds) {
        return recordPlaySession(playerId, sessionStart, sessionEnd, playTimeSeconds, afkTimeSeconds, false);
    }

    /**
     * Records a completed player session synchronously, intended for plugin shutdown.
     *
     * @param playerId player id
     * @param sessionStart session start time
     * @param sessionEnd session end time
     * @param playTimeSeconds session duration in seconds
     * @return completion future
     */
    public CompletableFuture<Void> recordPlaySessionSync(
            UUID playerId,
            Instant sessionStart,
            Instant sessionEnd,
            long playTimeSeconds) {
        return recordPlaySession(playerId, sessionStart, sessionEnd, playTimeSeconds, 0L, true);
    }

    /**
     * Records a completed player session synchronously, intended for plugin shutdown.
     *
     * @param playerId player id
     * @param sessionStart session start time
     * @param sessionEnd session end time
     * @param playTimeSeconds session duration in seconds
     * @param afkTimeSeconds session AFK duration in seconds
     * @return completion future
     */
    public CompletableFuture<Void> recordPlaySessionSync(
            UUID playerId,
            Instant sessionStart,
            Instant sessionEnd,
            long playTimeSeconds,
            long afkTimeSeconds) {
        return recordPlaySession(playerId, sessionStart, sessionEnd, playTimeSeconds, afkTimeSeconds, true);
    }

    private CompletableFuture<Void> recordPlaySession(
            UUID playerId,
            Instant sessionStart,
            Instant sessionEnd,
            long playTimeSeconds,
            long afkTimeSeconds,
            boolean synchronous) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionStart, "sessionStart");
        Objects.requireNonNull(sessionEnd, "sessionEnd");
        String totalSql = """
                INSERT INTO %s (player_uuid, play_time, afk_time)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE play_time = play_time + VALUES(play_time)
                , afk_time = afk_time + VALUES(afk_time)
                """.formatted(this.tableName);
        String historySql = """
                INSERT INTO %s (player_uuid, session_start, session_end, duration_seconds, afk_duration_seconds)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(this.playSessionsTableName);
        return submitQuery(synchronous, () -> {
            try (var connection = this.databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try (var totalStatement = connection.prepareStatement(totalSql);
                     var historyStatement = connection.prepareStatement(historySql)) {
                    totalStatement.setString(1, playerId.toString());
                    totalStatement.setLong(2, playTimeSeconds);
                    totalStatement.setLong(3, afkTimeSeconds);
                    totalStatement.executeUpdate();

                    historyStatement.setString(1, playerId.toString());
                    historyStatement.setTimestamp(2, Timestamp.from(sessionStart));
                    historyStatement.setTimestamp(3, Timestamp.from(sessionEnd));
                    historyStatement.setLong(4, playTimeSeconds);
                    historyStatement.setLong(5, afkTimeSeconds);
                    historyStatement.executeUpdate();

                    connection.commit();
                    return null;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to persist player play session", exception);
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
        return addDistanceStats(totalDistances, travelDistances, false);
    }

    /**
     * Adds aggregate distance statistics synchronously, intended for plugin shutdown.
     *
     * @param totalDistances total distance by player id
     * @param travelDistances travel-type distance by player id
     * @return completion future
     */
    public CompletableFuture<Void> addDistanceStatsSync(
            Map<UUID, Double> totalDistances,
            Map<UUID, Map<String, Double>> travelDistances) {
        return addDistanceStats(totalDistances, travelDistances, true);
    }

    private CompletableFuture<Void> addDistanceStats(
            Map<UUID, Double> totalDistances,
            Map<UUID, Map<String, Double>> travelDistances,
            boolean synchronous) {
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
                INSERT INTO %s (player_uuid, travel_type, stat_date, distance)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE distance = distance + VALUES(distance)
                """.formatted(this.distanceStatsTableName);
        return submitQuery(synchronous, () -> {
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
        return addBlockStats(totalPlacedCounts, totalBrokenCounts, materialStats, false);
    }

    /**
     * Adds aggregate block statistics synchronously, intended for plugin shutdown.
     *
     * @param totalPlacedCounts total placed counts by player id
     * @param totalBrokenCounts total broken counts by player id
     * @param materialStats material deltas by player id
     * @return completion future
     */
    public CompletableFuture<Void> addBlockStatsSync(
            Map<UUID, Integer> totalPlacedCounts,
            Map<UUID, Integer> totalBrokenCounts,
            Map<UUID, Map<String, BlockStatsDelta>> materialStats) {
        return addBlockStats(totalPlacedCounts, totalBrokenCounts, materialStats, true);
    }

    private CompletableFuture<Void> addBlockStats(
            Map<UUID, Integer> totalPlacedCounts,
            Map<UUID, Integer> totalBrokenCounts,
            Map<UUID, Map<String, BlockStatsDelta>> materialStats,
            boolean synchronous) {
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
                INSERT INTO %s (player_uuid, material, stat_date, placed_count, broken_count)
                VALUES (?, ?, CURRENT_DATE, ?, ?)
                ON DUPLICATE KEY UPDATE
                    placed_count = placed_count + VALUES(placed_count),
                    broken_count = broken_count + VALUES(broken_count)
                """.formatted(this.blockStatsTableName);
        return submitQuery(synchronous, () -> {
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
     * Adds aggregate entity damage statistics for one or more players.
     *
     * @param damageStats damage deltas by player id and entity identifier
     * @return completion future
     */
    public CompletableFuture<Void> addEntityDamageStats(Map<UUID, Map<String, EntityDamageDelta>> damageStats) {
        return addEntityDamageStats(damageStats, false);
    }

    /**
     * Adds aggregate entity damage statistics synchronously, intended for plugin shutdown.
     *
     * @param damageStats damage deltas by player id and entity identifier
     * @return completion future
     */
    public CompletableFuture<Void> addEntityDamageStatsSync(Map<UUID, Map<String, EntityDamageDelta>> damageStats) {
        return addEntityDamageStats(damageStats, true);
    }

    private CompletableFuture<Void> addEntityDamageStats(
            Map<UUID, Map<String, EntityDamageDelta>> damageStats,
            boolean synchronous) {
        Objects.requireNonNull(damageStats, "damageStats");
        if (damageStats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql = """
                INSERT INTO %s (player_uuid, entity_type, stat_date, damage_dealt, damage_taken)
                VALUES (?, ?, CURRENT_DATE, ?, ?)
                ON DUPLICATE KEY UPDATE
                    damage_dealt = damage_dealt + VALUES(damage_dealt),
                    damage_taken = damage_taken + VALUES(damage_taken)
                """.formatted(this.entityDamageStatsTableName);
        return submitQuery(synchronous, () -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                addEntityDamageBatch(statement, damageStats);
                statement.executeBatch();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player entity damage stats", exception);
            }
        });
    }

    /**
     * Adds aggregate kill statistics for one or more players.
     *
     * @param killStats kill counts by player id and target identifier
     * @return completion future
     */
    public CompletableFuture<Void> addKillStats(Map<UUID, Map<String, Integer>> killStats) {
        return addKillStats(killStats, false);
    }

    /**
     * Adds aggregate kill statistics synchronously, intended for plugin shutdown.
     *
     * @param killStats kill counts by player id and target identifier
     * @return completion future
     */
    public CompletableFuture<Void> addKillStatsSync(Map<UUID, Map<String, Integer>> killStats) {
        return addKillStats(killStats, true);
    }

    private CompletableFuture<Void> addKillStats(
            Map<UUID, Map<String, Integer>> killStats,
            boolean synchronous) {
        Objects.requireNonNull(killStats, "killStats");
        if (killStats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql = """
                INSERT INTO %s (player_uuid, target, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE count = count + VALUES(count)
                """.formatted(this.killStatsTableName);
        return submitQuery(synchronous, () -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                addNamedStatsBatch(statement, killStats);
                statement.executeBatch();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player kill stats", exception);
            }
        });
    }

    /**
     * Adds aggregate craft statistics for one or more players.
     *
     * @param craftStats crafted item counts by player id and material
     * @return completion future
     */
    public CompletableFuture<Void> addCraftStats(Map<UUID, Map<String, Integer>> craftStats) {
        return addCraftStats(craftStats, false);
    }

    /**
     * Adds aggregate craft statistics synchronously, intended for plugin shutdown.
     *
     * @param craftStats crafted item counts by player id and material
     * @return completion future
     */
    public CompletableFuture<Void> addCraftStatsSync(Map<UUID, Map<String, Integer>> craftStats) {
        return addCraftStats(craftStats, true);
    }

    private CompletableFuture<Void> addCraftStats(
            Map<UUID, Map<String, Integer>> craftStats,
            boolean synchronous) {
        Objects.requireNonNull(craftStats, "craftStats");
        if (craftStats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql = """
                INSERT INTO %s (player_uuid, material, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE count = count + VALUES(count)
                """.formatted(this.craftStatsTableName);
        return submitQuery(synchronous, () -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                addCraftStatsBatch(statement, craftStats);
                statement.executeBatch();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player craft stats", exception);
            }
        });
    }

    /**
     * Adds aggregate brew, smelt, and enchant statistics for one or more players.
     *
     * @param brewCounts brew counts by player id
     * @param smeltStats smelted material counts by player id
     * @param enchantStats enchantment counts by player id
     * @param enchantItemStats enchanted item material counts by player id
     * @return completion future
     */
    public CompletableFuture<Void> addProcessingStats(
            Map<UUID, Integer> brewCounts,
            Map<UUID, Map<String, Integer>> smeltStats,
            Map<UUID, Map<String, Integer>> enchantStats,
            Map<UUID, Map<String, Integer>> enchantItemStats) {
        return addProcessingStats(brewCounts, smeltStats, enchantStats, enchantItemStats, false);
    }

    /**
     * Adds aggregate processing statistics synchronously, intended for plugin shutdown.
     *
     * @param brewCounts brew counts by player id
     * @param smeltStats smelted material counts by player id
     * @param enchantStats enchantment counts by player id
     * @param enchantItemStats enchanted item material counts by player id
     * @return completion future
     */
    public CompletableFuture<Void> addProcessingStatsSync(
            Map<UUID, Integer> brewCounts,
            Map<UUID, Map<String, Integer>> smeltStats,
            Map<UUID, Map<String, Integer>> enchantStats,
            Map<UUID, Map<String, Integer>> enchantItemStats) {
        return addProcessingStats(brewCounts, smeltStats, enchantStats, enchantItemStats, true);
    }

    private CompletableFuture<Void> addProcessingStats(
            Map<UUID, Integer> brewCounts,
            Map<UUID, Map<String, Integer>> smeltStats,
            Map<UUID, Map<String, Integer>> enchantStats,
            Map<UUID, Map<String, Integer>> enchantItemStats,
            boolean synchronous) {
        Objects.requireNonNull(brewCounts, "brewCounts");
        Objects.requireNonNull(smeltStats, "smeltStats");
        Objects.requireNonNull(enchantStats, "enchantStats");
        Objects.requireNonNull(enchantItemStats, "enchantItemStats");
        if (brewCounts.isEmpty() && smeltStats.isEmpty() && enchantStats.isEmpty() && enchantItemStats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String brewSql = """
                INSERT INTO %s (player_uuid, brew_count)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE brew_count = brew_count + VALUES(brew_count)
                """.formatted(this.tableName);
        String smeltSql = """
                INSERT INTO %s (player_uuid, material, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE count = count + VALUES(count)
                """.formatted(this.smeltStatsTableName);
        String enchantSql = """
                INSERT INTO %s (player_uuid, enchantment, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE count = count + VALUES(count)
                """.formatted(this.enchantStatsTableName);
        String enchantItemSql = """
                INSERT INTO %s (player_uuid, material, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE count = count + VALUES(count)
                """.formatted(this.enchantItemStatsTableName);
        return submitQuery(synchronous, () -> {
            try (var connection = this.databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try (var brewStatement = connection.prepareStatement(brewSql);
                     var smeltStatement = connection.prepareStatement(smeltSql);
                     var enchantStatement = connection.prepareStatement(enchantSql);
                     var enchantItemStatement = connection.prepareStatement(enchantItemSql)) {
                    addIntegerStatsBatch(brewStatement, brewCounts);
                    addMaterialStatsBatch(smeltStatement, smeltStats);
                    addNamedStatsBatch(enchantStatement, enchantStats);
                    addMaterialStatsBatch(enchantItemStatement, enchantItemStats);
                    brewStatement.executeBatch();
                    smeltStatement.executeBatch();
                    enchantStatement.executeBatch();
                    enchantItemStatement.executeBatch();
                    connection.commit();
                    return null;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player processing stats", exception);
            }
        });
    }

    /**
     * Adds aggregate harvest, breed, and fishing statistics for one or more players.
     *
     * @param harvestStats harvested material counts by player id
     * @param breedStats bred entity counts by player id
     * @param fishStats caught fish material counts by player id
     * @return completion future
     */
    public CompletableFuture<Void> addFarmingStats(
            Map<UUID, Map<String, Integer>> harvestStats,
            Map<UUID, Map<String, Integer>> breedStats,
            Map<UUID, Map<String, Integer>> fishStats) {
        return addFarmingStats(harvestStats, breedStats, fishStats, false);
    }

    /**
     * Adds aggregate farming statistics synchronously, intended for plugin shutdown.
     *
     * @param harvestStats harvested material counts by player id
     * @param breedStats bred entity counts by player id
     * @param fishStats caught fish material counts by player id
     * @return completion future
     */
    public CompletableFuture<Void> addFarmingStatsSync(
            Map<UUID, Map<String, Integer>> harvestStats,
            Map<UUID, Map<String, Integer>> breedStats,
            Map<UUID, Map<String, Integer>> fishStats) {
        return addFarmingStats(harvestStats, breedStats, fishStats, true);
    }

    private CompletableFuture<Void> addFarmingStats(
            Map<UUID, Map<String, Integer>> harvestStats,
            Map<UUID, Map<String, Integer>> breedStats,
            Map<UUID, Map<String, Integer>> fishStats,
            boolean synchronous) {
        Objects.requireNonNull(harvestStats, "harvestStats");
        Objects.requireNonNull(breedStats, "breedStats");
        Objects.requireNonNull(fishStats, "fishStats");
        if (harvestStats.isEmpty() && breedStats.isEmpty() && fishStats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String harvestSql = """
                INSERT INTO %s (player_uuid, material, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE count = count + VALUES(count)
                """.formatted(this.harvestStatsTableName);
        String breedSql = """
                INSERT INTO %s (player_uuid, entity_type, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE count = count + VALUES(count)
                """.formatted(this.breedStatsTableName);
        String fishSql = """
                INSERT INTO %s (player_uuid, material, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE count = count + VALUES(count)
                """.formatted(this.fishStatsTableName);
        return submitQuery(synchronous, () -> {
            try (var connection = this.databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try (var harvestStatement = connection.prepareStatement(harvestSql);
                     var breedStatement = connection.prepareStatement(breedSql);
                     var fishStatement = connection.prepareStatement(fishSql)) {
                    addMaterialStatsBatch(harvestStatement, harvestStats);
                    addNamedStatsBatch(breedStatement, breedStats);
                    addMaterialStatsBatch(fishStatement, fishStats);
                    harvestStatement.executeBatch();
                    breedStatement.executeBatch();
                    fishStatement.executeBatch();
                    connection.commit();
                    return null;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player farming stats", exception);
            }
        });
    }

    /**
     * Adds aggregate pickup and drop statistics for one or more players.
     *
     * @param itemStats item deltas by player id and material
     * @return completion future
     */
    public CompletableFuture<Void> addItemStats(Map<UUID, Map<String, ItemStatsDelta>> itemStats) {
        return addItemStats(itemStats, false);
    }

    /**
     * Adds aggregate pickup and drop statistics synchronously, intended for plugin shutdown.
     *
     * @param itemStats item deltas by player id and material
     * @return completion future
     */
    public CompletableFuture<Void> addItemStatsSync(Map<UUID, Map<String, ItemStatsDelta>> itemStats) {
        return addItemStats(itemStats, true);
    }

    private CompletableFuture<Void> addItemStats(
            Map<UUID, Map<String, ItemStatsDelta>> itemStats,
            boolean synchronous) {
        Objects.requireNonNull(itemStats, "itemStats");
        if (itemStats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql = """
                INSERT INTO %s (player_uuid, material, stat_date, pickup_count, drop_count)
                VALUES (?, ?, CURRENT_DATE, ?, ?)
                ON DUPLICATE KEY UPDATE
                    pickup_count = pickup_count + VALUES(pickup_count),
                    drop_count = drop_count + VALUES(drop_count)
                """.formatted(this.itemStatsTableName);
        return submitQuery(synchronous, () -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                addItemStatsBatch(statement, itemStats);
                statement.executeBatch();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player item stats", exception);
            }
        });
    }

    /**
     * Resets every tracked stat for the given player while preserving identity fields.
     *
     * @param playerId player id
     * @return completion future
     */
    public CompletableFuture<Void> resetAllStatsForPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    zeroAggregateColumns(connection, playerId);
                    deleteAllDetailStats(connection, playerId);
                    connection.commit();
                    return null;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to reset all player stats", exception);
            }
        });
    }

    /**
     * Resets every tracked stat for every player.
     *
     * @return completion future with affected player count
     */
    public CompletableFuture<Integer> resetAllStats() {
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    int affectedPlayers = countTrackedPlayers(connection);
                    zeroAggregateColumns(connection, null);
                    deleteAllDetailStats(connection, null);
                    connection.commit();
                    return affectedPlayers;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to reset all tracked stats", exception);
            }
        });
    }

    /**
     * Resets one specific stat target for the given player.
     *
     * @param playerId player id
     * @param resetTarget parsed reset target
     * @return completion future resolving to {@code true} when handled
     */
    public CompletableFuture<Boolean> resetSpecificStat(UUID playerId, PlayerStatsResetTarget resetTarget) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(resetTarget, "resetTarget");
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    boolean handled = resetSpecificStat(connection, playerId, resetTarget);
                    connection.commit();
                    return handled;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to reset player stat " + resetTarget.canonicalKey(), exception);
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
     * Increments the total sleep counter for the given player.
     *
     * @param playerId player id
     * @return completion future
     */
    public CompletableFuture<Void> incrementSleepCount(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return incrementPlayerStat(playerId, "sleep_count");
    }

    /**
     * Increments the total portal usage counter for the given player.
     *
     * @param playerId player id
     * @return completion future
     */
    public CompletableFuture<Void> incrementPortalCount(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return incrementPlayerStat(playerId, "portal_count");
    }

    /**
     * Increments the total chat message counter for the given player.
     *
     * @param playerId player id
     * @return completion future
     */
    public CompletableFuture<Void> incrementChatCount(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return incrementPlayerStat(playerId, "chat_count");
    }

    /**
     * Increments the projectile launch counter for the given player and entity type.
     *
     * @param playerId player id
     * @param entityType projectile entity type
     * @return completion future
     */
    public CompletableFuture<Void> incrementProjectileCount(UUID playerId, String entityType) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(entityType, "entityType");
        String sql = """
                INSERT INTO %s (player_uuid, entity_type, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, 1)
                ON DUPLICATE KEY UPDATE count = count + 1
                """.formatted(this.projectileStatsTableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, entityType);
                statement.executeUpdate();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update player projectile stats", exception);
            }
        });
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
                INSERT INTO %s (player_uuid, cause, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, 1)
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

    private boolean resetSpecificStat(
            java.sql.Connection connection,
            UUID playerId,
            PlayerStatsResetTarget resetTarget) throws Exception {
        return switch (resetTarget.type()) {
            case PLAY_TIME -> {
                updateAggregateColumn(connection, "play_time", playerId, 0);
                updateAggregateColumn(connection, "afk_time", playerId, 0);
                deleteByPlayer(connection, this.playSessionsTableName, playerId);
                yield true;
            }
            case DISTANCE -> {
                updateAggregateColumn(connection, "distance", playerId, 0);
                deleteByPlayer(connection, this.distanceStatsTableName, playerId);
                yield true;
            }
            case DEATHS -> {
                updateAggregateColumn(connection, "deaths", playerId, 0);
                deleteByPlayer(connection, this.deathStatsTableName, playerId);
                yield true;
            }
            case RESPAWNS -> {
                updateAggregateColumn(connection, "respawns", playerId, 0);
                yield true;
            }
            case CHAT_COUNT -> {
                updateAggregateColumn(connection, "chat_count", playerId, 0);
                yield true;
            }
            case SLEEP_COUNT -> {
                updateAggregateColumn(connection, "sleep_count", playerId, 0);
                yield true;
            }
            case PORTAL_COUNT -> {
                updateAggregateColumn(connection, "portal_count", playerId, 0);
                yield true;
            }
            case BREW_COUNT -> {
                updateAggregateColumn(connection, "brew_count", playerId, 0);
                yield true;
            }
            case BLOCK_MATERIAL -> {
                BlockStatsDelta delta = queryBlockStatsDelta(connection, playerId, Objects.requireNonNull(resetTarget.qualifier()));
                deleteByColumn(connection, this.blockStatsTableName, playerId, "material", resetTarget.qualifier());
                if (delta != null) {
                    decrementBlockAggregates(connection, playerId, delta);
                }
                yield true;
            }
            case ENTITY_DAMAGE_TARGET -> {
                deleteByColumn(connection, this.entityDamageStatsTableName, playerId, "entity_type", resetTarget.qualifier());
                yield true;
            }
            case KILL_TARGET -> {
                deleteByColumn(connection, this.killStatsTableName, playerId, "target", resetTarget.qualifier());
                yield true;
            }
            case COMBAT_TARGET -> {
                deleteByColumn(connection, this.entityDamageStatsTableName, playerId, "entity_type", resetTarget.qualifier());
                deleteByColumn(connection, this.killStatsTableName, playerId, "target", resetTarget.qualifier());
                yield true;
            }
            case CRAFT_ALL -> {
                deleteByPlayer(connection, this.craftStatsTableName, playerId);
                yield true;
            }
            case CRAFT_MATERIAL -> {
                deleteByColumn(connection, this.craftStatsTableName, playerId, "material", resetTarget.qualifier());
                yield true;
            }
            case SMELT_ALL -> {
                deleteByPlayer(connection, this.smeltStatsTableName, playerId);
                yield true;
            }
            case SMELT_MATERIAL -> {
                deleteByColumn(connection, this.smeltStatsTableName, playerId, "material", resetTarget.qualifier());
                yield true;
            }
            case ENCHANT_ALL -> {
                deleteByPlayer(connection, this.enchantStatsTableName, playerId);
                yield true;
            }
            case ENCHANTMENT -> {
                deleteByColumn(connection, this.enchantStatsTableName, playerId, "enchantment", resetTarget.qualifier());
                yield true;
            }
            case ENCHANT_ITEM_MATERIAL -> {
                deleteByColumn(connection, this.enchantItemStatsTableName, playerId, "material", resetTarget.qualifier());
                yield true;
            }
            case HARVEST_ALL -> {
                deleteByPlayer(connection, this.harvestStatsTableName, playerId);
                yield true;
            }
            case HARVEST_MATERIAL -> {
                deleteByColumn(connection, this.harvestStatsTableName, playerId, "material", resetTarget.qualifier());
                yield true;
            }
            case BREED_ALL -> {
                deleteByPlayer(connection, this.breedStatsTableName, playerId);
                yield true;
            }
            case BREED_ENTITY -> {
                deleteByColumn(connection, this.breedStatsTableName, playerId, "entity_type", resetTarget.qualifier());
                yield true;
            }
            case FISH_ALL -> {
                deleteByPlayer(connection, this.fishStatsTableName, playerId);
                yield true;
            }
            case FISH_MATERIAL -> {
                deleteByColumn(connection, this.fishStatsTableName, playerId, "material", resetTarget.qualifier());
                yield true;
            }
            case ITEM_ALL -> {
                deleteByPlayer(connection, this.itemStatsTableName, playerId);
                yield true;
            }
            case ITEM_MATERIAL -> {
                deleteByColumn(connection, this.itemStatsTableName, playerId, "material", resetTarget.qualifier());
                yield true;
            }
            case PROJECTILE_ALL -> {
                deleteByPlayer(connection, this.projectileStatsTableName, playerId);
                yield true;
            }
            case PROJECTILE_TYPE -> {
                deleteByColumn(connection, this.projectileStatsTableName, playerId, "entity_type", resetTarget.qualifier());
                yield true;
            }
        };
    }

    private void zeroAggregateColumns(java.sql.Connection connection, UUID playerId) throws Exception {
        String sql = playerId == null
                ? """
                UPDATE %s
                SET play_time = 0,
                    afk_time = 0,
                    deaths = 0,
                    respawns = 0,
                    distance = 0,
                    blocks_placed = 0,
                    blocks_broken = 0,
                    sleep_count = 0,
                    portal_count = 0,
                    chat_count = 0,
                    brew_count = 0
                """.formatted(this.tableName)
                : """
                UPDATE %s
                SET play_time = 0,
                    afk_time = 0,
                    deaths = 0,
                    respawns = 0,
                    distance = 0,
                    blocks_placed = 0,
                    blocks_broken = 0,
                    sleep_count = 0,
                    portal_count = 0,
                    chat_count = 0,
                    brew_count = 0
                WHERE player_uuid = ?
                """.formatted(this.tableName);
        try (var statement = connection.prepareStatement(sql)) {
            if (playerId != null) {
                statement.setString(1, playerId.toString());
            }
            statement.executeUpdate();
        }
    }

    private void deleteAllDetailStats(java.sql.Connection connection, UUID playerId) throws Exception {
        deleteByPlayer(connection, this.deathStatsTableName, playerId);
        deleteByPlayer(connection, this.distanceStatsTableName, playerId);
        deleteByPlayer(connection, this.blockStatsTableName, playerId);
        deleteByPlayer(connection, this.entityDamageStatsTableName, playerId);
        deleteByPlayer(connection, this.killStatsTableName, playerId);
        deleteByPlayer(connection, this.craftStatsTableName, playerId);
        deleteByPlayer(connection, this.smeltStatsTableName, playerId);
        deleteByPlayer(connection, this.enchantStatsTableName, playerId);
        deleteByPlayer(connection, this.enchantItemStatsTableName, playerId);
        deleteByPlayer(connection, this.harvestStatsTableName, playerId);
        deleteByPlayer(connection, this.breedStatsTableName, playerId);
        deleteByPlayer(connection, this.fishStatsTableName, playerId);
        deleteByPlayer(connection, this.itemStatsTableName, playerId);
        deleteByPlayer(connection, this.projectileStatsTableName, playerId);
        deleteByPlayer(connection, this.playSessionsTableName, playerId);
    }

    private void updateAggregateColumn(
            java.sql.Connection connection,
            String columnName,
            UUID playerId,
            int value) throws Exception {
        String sql = """
                UPDATE %s
                SET %s = ?
                WHERE player_uuid = ?
                """.formatted(this.tableName, columnName);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, value);
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        }
    }

    private void updateAggregateColumn(
            java.sql.Connection connection,
            String columnName,
            UUID playerId,
            double value) throws Exception {
        String sql = """
                UPDATE %s
                SET %s = ?
                WHERE player_uuid = ?
                """.formatted(this.tableName, columnName);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, value);
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        }
    }

    private void deleteByPlayer(java.sql.Connection connection, String tableName, UUID playerId) throws Exception {
        String sql = playerId == null
                ? "DELETE FROM %s".formatted(tableName)
                : "DELETE FROM %s WHERE player_uuid = ?".formatted(tableName);
        try (var statement = connection.prepareStatement(sql)) {
            if (playerId != null) {
                statement.setString(1, playerId.toString());
            }
            statement.executeUpdate();
        }
    }

    private void deleteByColumn(
            java.sql.Connection connection,
            String tableName,
            UUID playerId,
            String columnName,
            @org.jetbrains.annotations.Nullable String value) throws Exception {
        String sql = """
                DELETE FROM %s
                WHERE player_uuid = ? AND %s = ?
                """.formatted(tableName, columnName);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private BlockStatsDelta queryBlockStatsDelta(
            java.sql.Connection connection,
            UUID playerId,
            String material) throws Exception {
        String sql = """
                SELECT COALESCE(SUM(placed_count), 0) AS placed_total,
                       COALESCE(SUM(broken_count), 0) AS broken_total
                FROM %s
                WHERE player_uuid = ? AND material = ?
                """.formatted(this.blockStatsTableName);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, material);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new BlockStatsDelta(
                        resultSet.getInt("placed_total"),
                        resultSet.getInt("broken_total"));
            }
        }
    }

    private void decrementBlockAggregates(
            java.sql.Connection connection,
            UUID playerId,
            BlockStatsDelta delta) throws Exception {
        String sql = """
                UPDATE %s
                SET blocks_placed = CASE
                        WHEN blocks_placed >= ? THEN blocks_placed - ?
                        ELSE 0
                    END,
                    blocks_broken = CASE
                        WHEN blocks_broken >= ? THEN blocks_broken - ?
                        ELSE 0
                    END
                WHERE player_uuid = ?
                """.formatted(this.tableName);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, delta.placedCount());
            statement.setInt(2, delta.placedCount());
            statement.setInt(3, delta.brokenCount());
            statement.setInt(4, delta.brokenCount());
            statement.setString(5, playerId.toString());
            statement.executeUpdate();
        }
    }

    private int countTrackedPlayers(java.sql.Connection connection) throws Exception {
        String sql = "SELECT COUNT(*) AS player_count FROM %s".formatted(this.tableName);
        try (var statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt("player_count");
        }
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

    private void addEntityDamageBatch(
            java.sql.PreparedStatement statement,
            Map<UUID, Map<String, EntityDamageDelta>> damageStats) throws Exception {
        for (Map.Entry<UUID, Map<String, EntityDamageDelta>> playerEntry : damageStats.entrySet()) {
            for (Map.Entry<String, EntityDamageDelta> entityEntry : playerEntry.getValue().entrySet()) {
                statement.setString(1, playerEntry.getKey().toString());
                statement.setString(2, entityEntry.getKey());
                statement.setDouble(3, entityEntry.getValue().damageDealt());
                statement.setDouble(4, entityEntry.getValue().damageTaken());
                statement.addBatch();
            }
        }
    }

    private void addCraftStatsBatch(
            java.sql.PreparedStatement statement,
            Map<UUID, Map<String, Integer>> craftStats) throws Exception {
        for (Map.Entry<UUID, Map<String, Integer>> playerEntry : craftStats.entrySet()) {
            for (Map.Entry<String, Integer> materialEntry : playerEntry.getValue().entrySet()) {
                statement.setString(1, playerEntry.getKey().toString());
                statement.setString(2, materialEntry.getKey());
                statement.setInt(3, materialEntry.getValue());
                statement.addBatch();
            }
        }
    }

    private void addIntegerStatsBatch(
            java.sql.PreparedStatement statement,
            Map<UUID, Integer> statCounts) throws Exception {
        for (Map.Entry<UUID, Integer> entry : statCounts.entrySet()) {
            statement.setString(1, entry.getKey().toString());
            statement.setInt(2, entry.getValue());
            statement.addBatch();
        }
    }

    private void addMaterialStatsBatch(
            java.sql.PreparedStatement statement,
            Map<UUID, Map<String, Integer>> materialStats) throws Exception {
        for (Map.Entry<UUID, Map<String, Integer>> playerEntry : materialStats.entrySet()) {
            for (Map.Entry<String, Integer> materialEntry : playerEntry.getValue().entrySet()) {
                statement.setString(1, playerEntry.getKey().toString());
                statement.setString(2, materialEntry.getKey());
                statement.setInt(3, materialEntry.getValue());
                statement.addBatch();
            }
        }
    }

    private void addNamedStatsBatch(
            java.sql.PreparedStatement statement,
            Map<UUID, Map<String, Integer>> namedStats) throws Exception {
        for (Map.Entry<UUID, Map<String, Integer>> playerEntry : namedStats.entrySet()) {
            for (Map.Entry<String, Integer> statEntry : playerEntry.getValue().entrySet()) {
                statement.setString(1, playerEntry.getKey().toString());
                statement.setString(2, statEntry.getKey());
                statement.setInt(3, statEntry.getValue());
                statement.addBatch();
            }
        }
    }

    private void addItemStatsBatch(
            java.sql.PreparedStatement statement,
            Map<UUID, Map<String, ItemStatsDelta>> itemStats) throws Exception {
        for (Map.Entry<UUID, Map<String, ItemStatsDelta>> playerEntry : itemStats.entrySet()) {
            for (Map.Entry<String, ItemStatsDelta> materialEntry : playerEntry.getValue().entrySet()) {
                statement.setString(1, playerEntry.getKey().toString());
                statement.setString(2, materialEntry.getKey());
                statement.setInt(3, materialEntry.getValue().pickupCount());
                statement.setInt(4, materialEntry.getValue().dropCount());
                statement.addBatch();
            }
        }
    }

    private <T> CompletableFuture<T> submitQuery(boolean synchronous, Supplier<T> supplier) {
        return synchronous ? this.databaseManager.querySync(supplier) : this.databaseManager.queryAsync(supplier);
    }
}
