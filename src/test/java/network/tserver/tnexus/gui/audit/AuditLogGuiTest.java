package network.tserver.tnexus.gui.audit;

import java.util.concurrent.TimeUnit;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.TransactionRepository;
import network.tserver.tnexus.database.repository.TransactionRepository.AuditRecord;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogGuiTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldOpenFilterGuiAndApplySelectedFilterFromFirstPage() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock player = this.server.addPlayer("HistoryUser");
        player.addAttachment(plugin, "tnexus.use", true);

        TransactionRepository repository = new TransactionRepository(plugin.getDatabaseManager());
        for (int index = 0; index < 30; index++) {
            repository.insert(new AuditRecord(
                    player.getUniqueId(),
                    index < 29 ? TransactionType.DEPOSIT : TransactionType.WITHDRAW,
                    index + 1.0D,
                    index + 1.0D,
                    "Entry " + index,
                    null)).get(5, TimeUnit.SECONDS);
        }

        plugin.getAuditLogManager().openHistoryViewer(player, player, network.tserver.tnexus.manager.AuditLogFilter.ALL);
        waitUntil(() -> plugin.getGuiManager().hasOpenGui(player));
        assertTrue(plugin.getGuiManager().getOpenGui(player) instanceof AuditLogGui);
        AuditLogGui historyGui = (AuditLogGui) plugin.getGuiManager().getOpenGui(player);
        assertEquals(2, historyGui.getTotalPages());

        click(player, 53);
        assertEquals(1, historyGui.getCurrentPage());

        Thread.sleep(250L);
        click(player, 4);
        waitUntil(() -> plugin.getGuiManager().getOpenGui(player) instanceof AuditFilterGui);

        Thread.sleep(250L);
        click(player, 11);
        waitUntil(() -> plugin.getGuiManager().getOpenGui(player) instanceof AuditLogGui);

        AuditLogGui filteredGui = (AuditLogGui) plugin.getGuiManager().getOpenGui(player);
        assertEquals(0, filteredGui.getCurrentPage());
        assertEquals(1, filteredGui.getTotalPages());
    }

    private void click(PlayerMock player, int rawSlot) {
        InventoryType.SlotType slotType = rawSlot < player.getOpenInventory().getTopInventory().getSize()
                ? InventoryType.SlotType.CONTAINER
                : InventoryType.SlotType.QUICKBAR;
        InventoryClickEvent event = new InventoryClickEvent(
                player.getOpenInventory(),
                slotType,
                rawSlot,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL);
        this.server.getPluginManager().callEvent(event);
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            this.server.getScheduler().performOneTick();
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25L);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not met in time");
    }
}
