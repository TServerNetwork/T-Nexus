package network.tserver.tnexus.database.repository;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.database.DatabaseManager;

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
