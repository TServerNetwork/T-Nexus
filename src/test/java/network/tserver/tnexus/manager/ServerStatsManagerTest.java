package network.tserver.tnexus.manager;

import java.util.concurrent.TimeUnit;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.TransactionRepository;
import network.tserver.tnexus.database.repository.TransactionRepository.AuditRecord;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerStatsManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldLoadFormattedServerStatsSnapshot() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock first = this.server.addPlayer("First");
        this.server.addPlayer("Second");

        TransactionRepository repository = new TransactionRepository(plugin.getDatabaseManager());
        repository.insert(new AuditRecord(
                first.getUniqueId(),
                TransactionType.DEPOSIT,
                100.0D,
                100.0D,
                "Deposit",
                null)).get(5, TimeUnit.SECONDS);
        repository.insert(new AuditRecord(
                first.getUniqueId(),
                TransactionType.WITHDRAW,
                40.0D,
                60.0D,
                "Withdraw",
                null)).get(5, TimeUnit.SECONDS);
        try (var connection = plugin.getDatabaseManager().getConnection()) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tnexus_server_shops (name, material, amount, buy_price, sell_price, category, enabled, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, "Server Shop");
                statement.setString(2, "DIAMOND");
                statement.setInt(3, 1);
                statement.setDouble(4, 100.0D);
                statement.setDouble(5, 90.0D);
                statement.setString(6, "test");
                statement.setBoolean(7, true);
                statement.setString(8, first.getUniqueId().toString());
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tnexus_player_shops (owner_uuid, material, amount, price, type, stock, enabled) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, first.getUniqueId().toString());
                statement.setString(2, "EMERALD");
                statement.setInt(3, 1);
                statement.setDouble(4, 50.0D);
                statement.setString(5, "SELL");
                statement.setInt(6, 16);
                statement.setBoolean(7, true);
                statement.executeUpdate();
            }
        }

        java.util.concurrent.CompletableFuture<ServerStatsManager.ServerStatsSnapshot> snapshotFuture =
                plugin.getServerStatsManager().loadStats();
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (!snapshotFuture.isDone() && System.currentTimeMillis() < deadline) {
            this.server.getScheduler().performOneTick();
            Thread.sleep(25L);
        }
        ServerStatsManager.ServerStatsSnapshot loadedSnapshot = snapshotFuture.get(5, TimeUnit.SECONDS);

        assertEquals(2, loadedSnapshot.onlinePlayers());
        assertEquals("2", loadedSnapshot.totalTransactions());
        assertTrue(loadedSnapshot.totalTransactionAmount().contains("140"));
        assertTrue(loadedSnapshot.circulationAmount().contains("140"));
        assertEquals("2", loadedSnapshot.activeShopCount());
        assertTrue(loadedSnapshot.uptime().matches("\\d+:\\d{2}:\\d{2}"));
    }
}
