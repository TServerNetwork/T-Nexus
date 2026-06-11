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
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionStart, "sessionStart");
        Objects.requireNonNull(sessionEnd, "sessionEnd");
        String totalSql = """
                INSERT INTO %s (player_uuid, play_time)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE play_time = play_time + VALUES(play_time)
                """.formatted(this.tableName);
        String historySql = """
                INSERT INTO %s (player_uuid, session_start, session_end, duration_seconds)
                VALUES (?, ?, ?, ?)
                """.formatted(this.playSessionsTableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try (var totalStatement = connection.prepareStatement(totalSql);
                     var historyStatement = connection.prepareStatement(historySql)) {
                    totalStatement.setString(1, playerId.toString());
                    totalStatement.setLong(2, playTimeSeconds);
                    totalStatement.executeUpdate();

                    historyStatement.setString(1, playerId.toString());
                    historyStatement.setTimestamp(2, Timestamp.from(sessionStart));
                    historyStatement.setTimestamp(3, Timestamp.from(sessionEnd));
                    historyStatement.setLong(4, playTimeSeconds);
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
                INSERT INTO %s (player_uuid, material, stat_date, placed_count, broken_count)
                VALUES (?, ?, CURRENT_DATE, ?, ?)
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
     * Adds aggregate entity damage statistics for one or more players.
     *
     * @param damageStats damage deltas by player id and entity identifier
     * @return completion future
     */
    public CompletableFuture<Void> addEntityDamageStats(Map<UUID, Map<String, EntityDamageDelta>> damageStats) {
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
        return this.databaseManager.queryAsync(() -> {
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
        Objects.requireNonNull(killStats, "killStats");
        if (killStats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql = """
                INSERT INTO %s (player_uuid, target, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE count = count + VALUES(count)
                """.formatted(this.killStatsTableName);
        return this.databaseManager.queryAsync(() -> {
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
        Objects.requireNonNull(craftStats, "craftStats");
        if (craftStats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql = """
                INSERT INTO %s (player_uuid, material, stat_date, count)
                VALUES (?, ?, CURRENT_DATE, ?)
                ON DUPLICATE KEY UPDATE count = count + VALUES(count)
                """.formatted(this.craftStatsTableName);
        return this.databaseManager.queryAsync(() -> {
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
        return this.databaseManager.queryAsync(() -> {
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
        return this.databaseManager.queryAsync(() -> {
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
        return this.databaseManager.queryAsync(() -> {
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
}
