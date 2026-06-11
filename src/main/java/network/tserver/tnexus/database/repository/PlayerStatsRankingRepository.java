package network.tserver.tnexus.database.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.database.DatabaseManager;

/**
 * Loads persisted play-time data for the stats ranking viewer.
 */
public final class PlayerStatsRankingRepository {

    private final DatabaseManager databaseManager;
    private final String playerStatsTableName;
    private final String playSessionsTableName;

    /**
     * Creates a new repository.
     *
     * @param databaseManager database manager
     */
    public PlayerStatsRankingRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        String tablePrefix = this.databaseManager.getTablePrefix();
        this.playerStatsTableName = tablePrefix + "player_stats";
        this.playSessionsTableName = tablePrefix + "player_play_sessions";
    }

    /**
     * Loads persisted all-time play-time totals keyed by player UUID.
     *
     * @return completion future
     */
    public CompletableFuture<Map<UUID, Long>> loadAllTimePlayTimes() {
        String sql = """
                SELECT player_uuid, play_time
                FROM %s
                WHERE play_time > 0
                """.formatted(this.playerStatsTableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql);
                 var resultSet = statement.executeQuery()) {
                Map<UUID, Long> totals = new LinkedHashMap<>();
                while (resultSet.next()) {
                    totals.put(
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getLong("play_time"));
                }
                return totals;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to load all-time play-time rankings", exception);
            }
        });
    }

    /**
     * Loads persisted play sessions that overlap the requested lower bound.
     *
     * @param periodStart lower bound instant
     * @return completion future
     */
    public CompletableFuture<List<PlaySessionRecord>> loadSessionsSince(Instant periodStart) {
        Objects.requireNonNull(periodStart, "periodStart");
        String sql = """
                SELECT player_uuid, session_start, session_end, duration_seconds
                FROM %s
                WHERE session_end > ?
                ORDER BY session_end ASC
                """.formatted(this.playSessionsTableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(periodStart));
                try (var resultSet = statement.executeQuery()) {
                    List<PlaySessionRecord> sessions = new ArrayList<>();
                    while (resultSet.next()) {
                        sessions.add(new PlaySessionRecord(
                                UUID.fromString(resultSet.getString("player_uuid")),
                                resultSet.getTimestamp("session_start").toInstant(),
                                resultSet.getTimestamp("session_end").toInstant(),
                                resultSet.getLong("duration_seconds")));
                    }
                    return sessions;
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to load filtered play-time rankings", exception);
            }
        });
    }

    /**
     * Persisted play-session record.
     *
     * @param playerId player UUID
     * @param sessionStart session start time
     * @param sessionEnd session end time
     * @param durationSeconds persisted duration in seconds
     */
    public record PlaySessionRecord(
            UUID playerId,
            Instant sessionStart,
            Instant sessionEnd,
            long durationSeconds) {
    }
}
