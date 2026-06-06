package network.tserver.tnexus.database.repository;

import java.sql.Timestamp;
import java.time.Instant;
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

    /**
     * Creates a new player stats repository.
     *
     * @param databaseManager database manager
     */
    public PlayerStatsRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.tableName = this.databaseManager.getTablePrefix() + "player_stats";
        this.deathStatsTableName = this.databaseManager.getTablePrefix() + "death_stats";
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
}
