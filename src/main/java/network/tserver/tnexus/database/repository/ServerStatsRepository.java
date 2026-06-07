package network.tserver.tnexus.database.repository;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.database.DatabaseManager;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;

/**
 * Loads aggregate server statistics from the database.
 */
public final class ServerStatsRepository {

    private final DatabaseManager databaseManager;
    private final String transactionsTableName;
    private final String serverShopsTableName;
    private final String playerShopsTableName;

    /**
     * Creates a new repository instance.
     *
     * @param databaseManager database manager
     */
    public ServerStatsRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        String tablePrefix = this.databaseManager.getTablePrefix();
        this.transactionsTableName = tablePrefix + "transactions";
        this.serverShopsTableName = tablePrefix + "server_shops";
        this.playerShopsTableName = tablePrefix + "player_shops";
    }

    /**
     * Loads the database-backed aggregate server statistics.
     *
     * @return completion future with aggregated stats
     */
    public CompletableFuture<DatabaseServerStats> loadStats() {
        return this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection()) {
                long totalTransactions = queryLong(connection,
                        "SELECT COUNT(*) FROM %s".formatted(this.transactionsTableName));
                double totalTransactionAmount = queryDouble(connection,
                        "SELECT COALESCE(SUM(amount), 0) FROM %s".formatted(this.transactionsTableName));
                double circulationAmount = queryDouble(connection, """
                        SELECT COALESCE(SUM(amount), 0)
                        FROM %s
                        WHERE type IN (?, ?)
                        """.formatted(this.transactionsTableName), TransactionType.DEPOSIT.name(), TransactionType.WITHDRAW.name());
                long activeServerShops = queryLong(connection,
                        "SELECT COUNT(*) FROM %s WHERE enabled = ?".formatted(this.serverShopsTableName), true);
                long activePlayerShops = queryLong(connection,
                        "SELECT COUNT(*) FROM %s WHERE enabled = ?".formatted(this.playerShopsTableName), true);
                return new DatabaseServerStats(
                        totalTransactions,
                        totalTransactionAmount,
                        circulationAmount,
                        activeServerShops + activePlayerShops);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to load server stats", exception);
            }
        });
    }

    private long queryLong(java.sql.Connection connection, String sql, Object... parameters) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return 0L;
                }
                return resultSet.getLong(1);
            }
        }
    }

    private double queryDouble(java.sql.Connection connection, String sql, Object... parameters) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return 0.0D;
                }
                return resultSet.getDouble(1);
            }
        }
    }

    private void bindParameters(java.sql.PreparedStatement statement, Object... parameters) throws Exception {
        for (int index = 0; index < parameters.length; index++) {
            Object parameter = parameters[index];
            int jdbcIndex = index + 1;
            if (parameter instanceof Boolean value) {
                statement.setBoolean(jdbcIndex, value);
            } else if (parameter instanceof Number value) {
                statement.setObject(jdbcIndex, value);
            } else {
                statement.setString(jdbcIndex, String.valueOf(parameter));
            }
        }
    }

    /**
     * Database-backed aggregate values used to build the server stats GUI.
     *
     * @param totalTransactions total transaction row count
     * @param totalTransactionAmount total transaction amount
     * @param circulationAmount total DEPOSIT and WITHDRAW amount
     * @param activeShopCount active shop count across server and player shops
     */
    public record DatabaseServerStats(
            long totalTransactions,
            double totalTransactionAmount,
            double circulationAmount,
            long activeShopCount) {
    }
}
