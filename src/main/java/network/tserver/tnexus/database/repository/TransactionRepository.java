package network.tserver.tnexus.database.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.database.DatabaseManager;
import org.jetbrains.annotations.Nullable;

/**
 * Persists economy audit transactions.
 */
public final class TransactionRepository {

    private final DatabaseManager databaseManager;
    private final String tableName;

    /**
     * Creates a new transaction repository.
     *
     * @param databaseManager database manager
     */
    public TransactionRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.tableName = this.databaseManager.getTablePrefix() + "transactions";
    }

    /**
     * Stores an audit transaction record.
     *
     * @param record audit record
     * @return completion future
     */
    public CompletableFuture<Void> insert(AuditRecord record) {
        Objects.requireNonNull(record, "record");
        String sql = """
                INSERT INTO %s (player_uuid, type, amount, balance_after, description, counterpart_uuid)
                VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.playerUuid().toString());
                statement.setString(2, record.type().name());
                statement.setDouble(3, record.amount());
                statement.setDouble(4, record.balanceAfter());
                statement.setString(5, record.description());
                statement.setString(6, record.counterpartUuid() == null ? null : record.counterpartUuid().toString());
                statement.executeUpdate();
                return null;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to insert transaction audit record", exception);
            }
        });
    }

    /**
     * Loads audit history for a player, optionally filtered by transaction type.
     *
     * @param playerUuid player UUID
     * @param filterType optional transaction type filter
     * @return completion future with sorted audit entries
     */
    public CompletableFuture<List<AuditEntry>> findByPlayerUuid(UUID playerUuid, @Nullable TransactionType filterType) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        String sql = filterType == null
                ? """
                SELECT id, player_uuid, type, amount, balance_after, description, counterpart_uuid, created_at
                FROM %s
                WHERE player_uuid = ?
                ORDER BY created_at DESC, id DESC
                """.formatted(this.tableName)
                : """
                SELECT id, player_uuid, type, amount, balance_after, description, counterpart_uuid, created_at
                FROM %s
                WHERE player_uuid = ? AND type = ?
                ORDER BY created_at DESC, id DESC
                """.formatted(this.tableName);
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUuid.toString());
                if (filterType != null) {
                    statement.setString(2, filterType.name());
                }
                try (var resultSet = statement.executeQuery()) {
                    List<AuditEntry> entries = new ArrayList<>();
                    while (resultSet.next()) {
                        entries.add(new AuditEntry(
                                resultSet.getLong("id"),
                                UUID.fromString(resultSet.getString("player_uuid")),
                                TransactionType.valueOf(resultSet.getString("type")),
                                resultSet.getDouble("amount"),
                                resultSet.getDouble("balance_after"),
                                resultSet.getString("description"),
                                parseUuid(resultSet.getString("counterpart_uuid")),
                                resultSet.getTimestamp("created_at").toInstant()));
                    }
                    return entries;
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to load transaction audit history", exception);
            }
        });
    }

    private @Nullable UUID parseUuid(@Nullable String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return UUID.fromString(rawValue);
    }

    /**
     * Immutable transaction audit record.
     *
     * @param playerUuid affected player UUID
     * @param type transaction type
     * @param amount transaction amount
     * @param balanceAfter balance after transaction
     * @param description audit description
     * @param counterpartUuid optional counterpart UUID
     */
    public record AuditRecord(
            UUID playerUuid,
            TransactionType type,
            double amount,
            double balanceAfter,
            String description,
            UUID counterpartUuid) {
    }

    /**
     * Immutable transaction audit entry.
     *
     * @param id audit row id
     * @param playerUuid affected player UUID
     * @param type transaction type
     * @param amount transaction amount
     * @param balanceAfter balance after transaction
     * @param description audit description
     * @param counterpartUuid optional counterpart UUID
     * @param createdAt creation timestamp
     */
    public record AuditEntry(
            long id,
            UUID playerUuid,
            TransactionType type,
            double amount,
            double balanceAfter,
            String description,
            UUID counterpartUuid,
            Instant createdAt) {
    }

    /**
     * Supported transaction types persisted in tnexus_transactions.
     */
    public enum TransactionType {
        DEPOSIT,
        WITHDRAW,
        PAYMENT_SENT,
        PAYMENT_RECEIVED,
        SHOP_BUY,
        SHOP_SELL
    }
}
