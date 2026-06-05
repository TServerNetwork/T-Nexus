package network.tserver.tnexus.manager;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.PayQueueRepository;
import network.tserver.tnexus.database.repository.TransactionRepository;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldQueueConfirmAndAuditOnlinePayment() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock sender = this.server.addPlayer("Sender");
        PlayerMock target = this.server.addPlayer("Target");
        plugin.getEconomyManager().deposit(sender.getUniqueId(), 10000.0D).get(5, TimeUnit.SECONDS);

        Clock clock = Clock.fixed(Instant.parse("2026-06-05T00:00:00Z"), ZoneOffset.UTC);
        PaymentManager manager = new PaymentManager(
                plugin,
                plugin.getEconomyManager(),
                new PayQueueRepository(plugin.getDatabaseManager()),
                new TransactionRepository(plugin.getDatabaseManager()),
                clock,
                new ConcurrentHashMap<>());

        PaymentManager.QueueResult queueResult = manager.queuePayment(sender, target, 5000.0D)
                .get(5, TimeUnit.SECONDS);
        assertTrue(queueResult.isQueued());

        PaymentManager.ConfirmationResult confirmationResult = manager.confirmPayment(
                        sender,
                        queueResult.entry().token())
                .get(5, TimeUnit.SECONDS);

        assertEquals(PaymentManager.ConfirmationStatus.SUCCESS, confirmationResult.status());
        assertEquals(5000.0D, plugin.getEconomyManager().getBalance(sender.getUniqueId()).get(5, TimeUnit.SECONDS));
        assertEquals(5000.0D, plugin.getEconomyManager().getBalance(target.getUniqueId()).get(5, TimeUnit.SECONDS));
        assertEquals(1, countTransactions(plugin, sender.getUniqueId().toString(), "PAYMENT_SENT"));
        assertEquals(1, countTransactions(plugin, target.getUniqueId().toString(), "PAYMENT_RECEIVED"));
    }

    @Test
    void shouldDeliverDeferredNotificationForOfflineRecipient() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock sender = this.server.addPlayer("Sender");
        PlayerMock targetOnline = this.server.addPlayer("OfflineTarget");
        plugin.getEconomyManager().deposit(sender.getUniqueId(), 1000.0D).get(5, TimeUnit.SECONDS);
        targetOnline.disconnect();
        OfflinePlayer offlineTarget = this.server.getOfflinePlayer(targetOnline.getUniqueId());

        PaymentManager manager = new PaymentManager(
                plugin,
                plugin.getEconomyManager(),
                new PayQueueRepository(plugin.getDatabaseManager()),
                new TransactionRepository(plugin.getDatabaseManager()),
                Clock.fixed(Instant.parse("2026-06-05T00:00:00Z"), ZoneOffset.UTC),
                new ConcurrentHashMap<>());

        PaymentManager.QueueResult queueResult = manager.queuePayment(sender, offlineTarget, 250.0D)
                .get(5, TimeUnit.SECONDS);
        assertTrue(queueResult.isQueued());

        PaymentManager.ConfirmationResult confirmationResult = manager.confirmPayment(
                        sender,
                        queueResult.entry().token())
                .get(5, TimeUnit.SECONDS);
        assertEquals(PaymentManager.ConfirmationStatus.SUCCESS, confirmationResult.status());

        manager.deliverPendingNotifications(targetOnline);

        String message = targetOnline.nextMessage();
        assertNotNull(message);
        assertTrue(message.contains("Sender"));
        assertTrue(message.contains("250"));
        assertFalse(message.contains("失敗"));
    }

    @Test
    void shouldExpireOldQueuedPayment() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock sender = this.server.addPlayer("Sender");
        PlayerMock target = this.server.addPlayer("Target");
        plugin.getEconomyManager().deposit(sender.getUniqueId(), 1000.0D).get(5, TimeUnit.SECONDS);

        PaymentManager queueManager = new PaymentManager(
                plugin,
                plugin.getEconomyManager(),
                new PayQueueRepository(plugin.getDatabaseManager()),
                new TransactionRepository(plugin.getDatabaseManager()),
                Clock.fixed(Instant.parse("2026-06-05T00:00:00Z"), ZoneOffset.UTC),
                new ConcurrentHashMap<>());
        PaymentManager.QueueResult queueResult = queueManager.queuePayment(sender, target, 100.0D)
                .get(5, TimeUnit.SECONDS);

        PaymentManager confirmManager = new PaymentManager(
                plugin,
                plugin.getEconomyManager(),
                new PayQueueRepository(plugin.getDatabaseManager()),
                new TransactionRepository(plugin.getDatabaseManager()),
                Clock.fixed(Instant.parse("2026-06-05T00:00:31Z"), ZoneOffset.UTC),
                new ConcurrentHashMap<>());
        PaymentManager.ConfirmationResult confirmationResult = confirmManager.confirmPayment(
                        sender,
                        queueResult.entry().token())
                .get(5, TimeUnit.SECONDS);

        assertEquals(PaymentManager.ConfirmationStatus.EXPIRED, confirmationResult.status());
        assertEquals(1000.0D, plugin.getEconomyManager().getBalance(sender.getUniqueId()).get(5, TimeUnit.SECONDS));
        assertEquals(0, countTransactions(plugin, sender.getUniqueId().toString(), "PAYMENT_SENT"));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private int countTransactions(TNexus plugin, String playerUuid, String type) throws Exception {
        try (var connection = plugin.getDatabaseManager().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM tnexus_transactions WHERE player_uuid = ? AND type = ?")) {
            statement.setString(1, playerUuid);
            statement.setString(2, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
