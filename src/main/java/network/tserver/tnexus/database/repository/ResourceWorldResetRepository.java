package network.tserver.tnexus.database.repository;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.database.DatabaseManager;
import org.jetbrains.annotations.Nullable;

/**
 * Persists resource world reset scheduling state.
 */
public final class ResourceWorldResetRepository {

    private final DatabaseManager databaseManager;
    private final String tableName;

    /**
     * Creates a new resource world reset repository.
     *
     * @param databaseManager database manager
     */
    public ResourceWorldResetRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.tableName = this.databaseManager.getTablePrefix() + "resource_world_resets";
    }

    /**
     * Inserts a new reset record.
     *
     * @param record reset record
     * @return completion future with the persisted row id
     */
    public CompletableFuture<Long> insert(ResourceWorldResetRecord record) {
        Objects.requireNonNull(record, "record");
        String sql = """
                INSERT INTO %s (world_name, reset_at, next_reset_at, status, seed, error_message)
                VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, record.worldName());
                statement.setTimestamp(2, Timestamp.valueOf(record.resetAt()));
                statement.setTimestamp(3, Timestamp.valueOf(record.nextResetAt()));
                statement.setString(4, record.status().databaseValue());
                if (record.seed() == null) {
                    statement.setNull(5, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(5, record.seed());
                }
                statement.setString(6, record.errorMessage());
                statement.executeUpdate();
                try (var generatedKeys = statement.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        throw new IllegalStateException("Failed to read generated resource world reset id");
                    }
                    return generatedKeys.getLong(1);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to insert resource world reset", exception);
            }
        });
    }

    /**
     * Finds a reset record by world name and next reset time.
     *
     * @param worldName world name
     * @param nextResetAt next reset timestamp
     * @return completion future containing the matching record when present
     */
    public CompletableFuture<Optional<ResourceWorldResetEntry>> findByWorldNameAndNextResetAt(
            String worldName,
            LocalDateTime nextResetAt) {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(nextResetAt, "nextResetAt");
        String sql = """
                SELECT id, world_name, reset_at, next_reset_at, status, seed, error_message, created_at
                FROM %s
                WHERE world_name = ? AND next_reset_at = ?
                ORDER BY id DESC
                LIMIT 1
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, worldName);
                statement.setTimestamp(2, Timestamp.valueOf(nextResetAt));
                try (var resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapEntry(resultSet));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to read resource world reset", exception);
            }
        });
    }

    /**
     * Updates only the status and optional error message for an existing reset record.
     *
     * @param id row id
     * @param status new status
     * @param errorMessage optional error message
     * @return completion future indicating whether a row was updated
     */
    public CompletableFuture<Boolean> updateStatus(long id, ResetStatus status, @Nullable String errorMessage) {
        Objects.requireNonNull(status, "status");
        String sql = """
                UPDATE %s
                SET status = ?, error_message = ?
                WHERE id = ?
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, status.databaseValue());
                statement.setString(2, errorMessage);
                statement.setLong(3, id);
                return statement.executeUpdate() > 0;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update resource world reset status", exception);
            }
        });
    }

    /**
     * Updates completion state for an existing reset record.
     *
     * @param id row id
     * @param status new status
     * @param seed applied world seed
     * @return completion future indicating whether a row was updated
     */
    public CompletableFuture<Boolean> updateStatusAndSeed(long id, ResetStatus status, long seed) {
        Objects.requireNonNull(status, "status");
        String sql = """
                UPDATE %s
                SET status = ?, seed = ?, error_message = NULL
                WHERE id = ?
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, status.databaseValue());
                statement.setLong(2, seed);
                statement.setLong(3, id);
                return statement.executeUpdate() > 0;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to update resource world reset status and seed", exception);
            }
        });
    }

    /**
     * Upserts the latest scheduled reset timestamp for a world.
     *
     * @param worldName world name
     * @param nextResetAt next scheduled reset timestamp
     * @param seed optional seed value
     * @return completion future
     */
    public CompletableFuture<Void> upsertScheduledReset(String worldName, LocalDateTime nextResetAt, @Nullable Long seed) {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(nextResetAt, "nextResetAt");
        String selectSql = """
                SELECT id
                FROM %s
                WHERE world_name = ?
                ORDER BY id DESC
                LIMIT 1
                """.formatted(this.tableName);
        String insertSql = """
                INSERT INTO %s (world_name, reset_at, next_reset_at, status, seed, error_message)
                VALUES (?, ?, ?, ?, ?, NULL)
                """.formatted(this.tableName);
        String updateSql = """
                UPDATE %s
                SET reset_at = ?, next_reset_at = ?, status = ?, seed = ?, error_message = NULL
                WHERE id = ?
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection()) {
                Long existingId = null;
                try (var selectStatement = connection.prepareStatement(selectSql)) {
                    selectStatement.setString(1, worldName);
                    try (var resultSet = selectStatement.executeQuery()) {
                        if (resultSet.next()) {
                            existingId = resultSet.getLong("id");
                        }
                    }
                }

                if (existingId == null) {
                    try (var insertStatement = connection.prepareStatement(insertSql)) {
                        insertStatement.setString(1, worldName);
                        insertStatement.setTimestamp(2, Timestamp.valueOf(nextResetAt));
                        insertStatement.setTimestamp(3, Timestamp.valueOf(nextResetAt));
                        insertStatement.setString(4, ResetStatus.SCHEDULED.databaseValue());
                        if (seed == null) {
                            insertStatement.setNull(5, java.sql.Types.BIGINT);
                        } else {
                            insertStatement.setLong(5, seed);
                        }
                        insertStatement.executeUpdate();
                    }
                } else {
                    try (var updateStatement = connection.prepareStatement(updateSql)) {
                        updateStatement.setTimestamp(1, Timestamp.valueOf(nextResetAt));
                        updateStatement.setTimestamp(2, Timestamp.valueOf(nextResetAt));
                        updateStatement.setString(3, ResetStatus.SCHEDULED.databaseValue());
                        if (seed == null) {
                            updateStatement.setNull(4, java.sql.Types.BIGINT);
                        } else {
                            updateStatement.setLong(4, seed);
                        }
                        updateStatement.setLong(5, existingId);
                        updateStatement.executeUpdate();
                    }
                }
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to upsert resource world reset schedule", exception);
            }
        });
    }

    /**
     * Finds the latest scheduled reset time for a world.
     *
     * @param worldName world name
     * @return completion future with the latest next reset timestamp when present
     */
    public CompletableFuture<Optional<LocalDateTime>> findNextResetTime(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        String sql = """
                SELECT next_reset_at
                FROM %s
                WHERE world_name = ?
                ORDER BY id DESC
                LIMIT 1
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, worldName);
                try (var resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(resultSet.getTimestamp("next_reset_at").toLocalDateTime());
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to read next resource world reset time", exception);
            }
        });
    }

    /**
     * Finds the latest reset entry for a world.
     *
     * @param worldName world name
     * @return completion future containing the latest entry when present
     */
    public CompletableFuture<Optional<ResourceWorldResetEntry>> findLatestEntry(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        String sql = """
                SELECT id, world_name, reset_at, next_reset_at, status, seed, error_message, created_at
                FROM %s
                WHERE world_name = ?
                ORDER BY id DESC
                LIMIT 1
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, worldName);
                try (var resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapEntry(resultSet));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to read latest resource world reset", exception);
            }
        });
    }

    /**
     * Finds the latest completed reset entry for a world.
     *
     * @param worldName world name
     * @return completion future containing the latest completed entry when present
     */
    public CompletableFuture<Optional<ResourceWorldResetEntry>> findLatestCompletedEntry(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        String sql = """
                SELECT id, world_name, reset_at, next_reset_at, status, seed, error_message, created_at
                FROM %s
                WHERE world_name = ? AND status = ?
                ORDER BY id DESC
                LIMIT 1
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, worldName);
                statement.setString(2, ResetStatus.COMPLETED.databaseValue());
                try (var resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapEntry(resultSet));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to read latest completed resource world reset", exception);
            }
        });
    }

    private ResourceWorldResetEntry mapEntry(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ResourceWorldResetEntry(
                resultSet.getLong("id"),
                resultSet.getString("world_name"),
                resultSet.getTimestamp("reset_at").toLocalDateTime(),
                resultSet.getTimestamp("next_reset_at").toLocalDateTime(),
                ResetStatus.fromDatabaseValue(resultSet.getString("status")),
                readNullableLong(resultSet, "seed"),
                resultSet.getString("error_message"),
                resultSet.getTimestamp("created_at").toLocalDateTime());
    }

    private @Nullable Long readNullableLong(java.sql.ResultSet resultSet, String columnName) throws java.sql.SQLException {
        long value = resultSet.getLong(columnName);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    /**
     * Resource world reset status values stored in the database.
     */
    public enum ResetStatus {
        SCHEDULED("scheduled"),
        IN_PROGRESS("in_progress"),
        COMPLETED("completed"),
        FAILED("failed");

        private final String databaseValue;

        ResetStatus(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        /**
         * Returns the serialized database value.
         *
         * @return lowercase database value
         */
        public String databaseValue() {
            return this.databaseValue;
        }

        /**
         * Resolves a status from the database representation.
         *
         * @param databaseValue lowercase database value
         * @return matching status
         */
        public static ResetStatus fromDatabaseValue(String databaseValue) {
            for (ResetStatus status : values()) {
                if (status.databaseValue.equals(databaseValue)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown reset status: " + databaseValue);
        }
    }

    /**
     * Immutable reset record used for inserts.
     *
     * @param worldName world name
     * @param resetAt reset execution time
     * @param nextResetAt next scheduled reset time
     * @param status reset status
     * @param seed optional world seed
     * @param errorMessage optional error message
     */
    public record ResourceWorldResetRecord(
            String worldName,
            LocalDateTime resetAt,
            LocalDateTime nextResetAt,
            ResetStatus status,
            @Nullable Long seed,
            @Nullable String errorMessage) {

        /**
         * Creates a scheduled reset record.
         *
         * @param worldName world name
         * @param resetAt reset execution time
         * @param nextResetAt next scheduled reset time
         * @param seed optional world seed
         * @return scheduled reset record
         */
        public static ResourceWorldResetRecord scheduled(
                String worldName,
                LocalDateTime resetAt,
                LocalDateTime nextResetAt,
                @Nullable Long seed) {
            return new ResourceWorldResetRecord(
                    Objects.requireNonNull(worldName, "worldName"),
                    Objects.requireNonNull(resetAt, "resetAt"),
                    Objects.requireNonNull(nextResetAt, "nextResetAt"),
                    ResetStatus.SCHEDULED,
                    seed,
                    null);
        }
    }

    /**
     * Immutable persisted reset entry.
     *
     * @param id row id
     * @param worldName world name
     * @param resetAt reset execution time
     * @param nextResetAt next scheduled reset time
     * @param status reset status
     * @param seed optional world seed
     * @param errorMessage optional error message
     * @param createdAt creation time
     */
    public record ResourceWorldResetEntry(
            long id,
            String worldName,
            LocalDateTime resetAt,
            LocalDateTime nextResetAt,
            ResetStatus status,
            @Nullable Long seed,
            @Nullable String errorMessage,
            LocalDateTime createdAt) {
    }
}
