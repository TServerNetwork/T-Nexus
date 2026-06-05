package network.tserver.tnexus.manager;

import java.util.List;
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

class AuditLogManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldLoadAuditEntriesInDescendingOrder() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("HistoryUser");
        TransactionRepository repository = new TransactionRepository(plugin.getDatabaseManager());

        repository.insert(new AuditRecord(
                player.getUniqueId(),
                TransactionType.DEPOSIT,
                100.0D,
                100.0D,
                "First deposit",
                null)).get(5, TimeUnit.SECONDS);
        repository.insert(new AuditRecord(
                player.getUniqueId(),
                TransactionType.WITHDRAW,
                25.0D,
                75.0D,
                "ATM withdraw",
                null)).get(5, TimeUnit.SECONDS);

        List<TransactionRepository.AuditEntry> entries = plugin.getAuditLogManager()
                .getHistory(player.getUniqueId(), AuditLogFilter.ALL)
                .get(5, TimeUnit.SECONDS);

        assertEquals(2, entries.size());
        assertEquals(TransactionType.WITHDRAW, entries.get(0).type());
        assertEquals(TransactionType.DEPOSIT, entries.get(1).type());
    }

    @Test
    void shouldFilterAuditEntriesByRequestedType() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("HistoryUser");
        TransactionRepository repository = new TransactionRepository(plugin.getDatabaseManager());

        repository.insert(new AuditRecord(
                player.getUniqueId(),
                TransactionType.PAYMENT_RECEIVED,
                50.0D,
                50.0D,
                "Payment received",
                null)).get(5, TimeUnit.SECONDS);
        repository.insert(new AuditRecord(
                player.getUniqueId(),
                TransactionType.SHOP_BUY,
                10.0D,
                40.0D,
                "Bought from shop",
                null)).get(5, TimeUnit.SECONDS);

        List<TransactionRepository.AuditEntry> entries = plugin.getAuditLogManager()
                .getHistory(player.getUniqueId(), AuditLogFilter.PAYMENT_RECEIVED)
                .get(5, TimeUnit.SECONDS);

        assertEquals(1, entries.size());
        assertEquals(TransactionType.PAYMENT_RECEIVED, entries.getFirst().type());
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }
}
