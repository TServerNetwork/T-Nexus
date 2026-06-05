package network.tserver.tnexus.database.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.database.DatabaseManager;

/**
 * Persists pending pay confirmation tokens.
 */
public final class PayQueueRepository {

    private final DatabaseManager databaseManager;
    private final String tableName;

    /**
     * Creates a new pay queue repository.
     *
     * @param databaseManager database manager
     */
    public PayQueueRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.tableName = this.databaseManager.getTablePrefix() + "pay_queue";
    }

    /**
     * Saves a pending payment confirmation entry.
     *
     * @param entry queue entry
     * @return completion future
     */
    public CompletableFuture<Void> insert(PayQueueEntry entry) {
        Objects.requireNonNull(entry, "entry");
        String sql = """
                INSERT INTO %s (token, sender_uuid, target_uuid, amount, created_at)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, entry.token());
                statement.setString(2, entry.senderUuid().toString());
                statement.setString(3, entry.targetUuid().toString());
                statement.setDouble(4, entry.amount());
                statement.setTimestamp(5, Timestamp.from(entry.createdAt()));
                statement.executeUpdate();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to insert pay queue entry", exception);
            }
        });
    }

    /**
     * Looks up a queue entry by token.
     *
     * @param token queue token
     * @return optional queue entry
     */
    public CompletableFuture<Optional<PayQueueEntry>> findByToken(String token) {
        Objects.requireNonNull(token, "token");
        String sql = """
                SELECT token, sender_uuid, target_uuid, amount, created_at
                FROM %s
                WHERE token = ?
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, token);
                try (var resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new PayQueueEntry(
                            resultSet.getString("token"),
                            UUID.fromString(resultSet.getString("sender_uuid")),
                            UUID.fromString(resultSet.getString("target_uuid")),
                            resultSet.getDouble("amount"),
                            resultSet.getTimestamp("created_at").toInstant()));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to read pay queue entry", exception);
            }
        });
    }

    /**
     * Deletes a queue entry by token.
     *
     * @param token queue token
     * @return whether an entry was deleted
     */
    public CompletableFuture<Boolean> deleteByToken(String token) {
        Objects.requireNonNull(token, "token");
        String sql = "DELETE FROM %s WHERE token = ?".formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, token);
                return statement.executeUpdate() > 0;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to delete pay queue entry", exception);
            }
        });
    }

    /**
     * Immutable pending pay queue record.
     *
     * @param token confirmation token
     * @param senderUuid sender UUID
     * @param targetUuid target UUID
     * @param amount payment amount
     * @param createdAt queue creation timestamp
     */
    public record PayQueueEntry(
            String token,
            UUID senderUuid,
            UUID targetUuid,
            double amount,
            Instant createdAt) {

        /**
         * Creates a new queue entry with a generated token.
         *
         * @param senderUuid sender UUID
         * @param targetUuid target UUID
         * @param amount payment amount
         * @param createdAt creation timestamp
         * @return queue entry
         */
        public static PayQueueEntry create(UUID senderUuid, UUID targetUuid, double amount, Instant createdAt) {
            return new PayQueueEntry(
                    UUID.randomUUID().toString(),
                    Objects.requireNonNull(senderUuid, "senderUuid"),
                    Objects.requireNonNull(targetUuid, "targetUuid"),
                    amount,
                    Objects.requireNonNull(createdAt, "createdAt"));
        }
    }
}
