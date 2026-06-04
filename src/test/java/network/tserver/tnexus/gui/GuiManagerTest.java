package network.tserver.tnexus.gui;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldTrackOpenGuiCancelEventsAndReleaseOnClose() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        TestGui gui = new TestGui(plugin, player, 12);

        gui.open();

        assertTrue(plugin.getGuiManager().hasOpenGui(player));
        assertEquals(gui, plugin.getGuiManager().getOpenGui(player));

        InventoryClickEvent contentClick = createClickEvent(player, 10);
        this.server.getPluginManager().callEvent(contentClick);
        assertTrue(contentClick.isCancelled());
        assertEquals(1, gui.contentClicks.get());

        InventoryClickEvent playerInventoryClick = createClickEvent(player, player.getOpenInventory().getTopInventory().getSize());
        this.server.getPluginManager().callEvent(playerInventoryClick);
        assertTrue(playerInventoryClick.isCancelled());
        assertEquals(1, gui.contentClicks.get());

        InventoryDragEvent dragEvent = new InventoryDragEvent(
                player.getOpenInventory(),
                null,
                new ItemStack(Material.STONE),
                false,
                Map.of(10, new ItemStack(Material.STONE)));
        this.server.getPluginManager().callEvent(dragEvent);
        assertTrue(dragEvent.isCancelled());

        this.server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));

        assertFalse(plugin.getGuiManager().hasOpenGui(player));
        assertTrue(gui.closed);
    }

    @Test
    void shouldPaginateAndThrottleRapidNavigationClicks() throws InterruptedException {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        TestGui gui = new TestGui(plugin, player, 60);

        gui.open();

        assertEquals(3, gui.getTotalPages());
        assertEquals("§8前のページ", getDisplayName(player, 45));
        assertEquals("§e次のページ", getDisplayName(player, 53));

        InventoryClickEvent firstNextClick = createClickEvent(player, 53);
        this.server.getPluginManager().callEvent(firstNextClick);
        assertTrue(firstNextClick.isCancelled());
        assertEquals(1, gui.getCurrentPage());

        InventoryClickEvent throttledNextClick = createClickEvent(player, 53);
        this.server.getPluginManager().callEvent(throttledNextClick);
        assertEquals(1, gui.getCurrentPage());

        Thread.sleep(250L);
        this.server.getPluginManager().callEvent(createClickEvent(player, 53));
        assertEquals(2, gui.getCurrentPage());
        assertEquals("§8次のページ", getDisplayName(player, 53));

        Thread.sleep(250L);
        this.server.getPluginManager().callEvent(createClickEvent(player, 45));
        assertEquals(1, gui.getCurrentPage());

        Thread.sleep(250L);
        this.server.getPluginManager().callEvent(createClickEvent(player, 48));
        assertEquals(1, gui.backClicks.get());
    }

    private InventoryClickEvent createClickEvent(PlayerMock player, int rawSlot) {
        InventoryType.SlotType slotType = rawSlot < player.getOpenInventory().getTopInventory().getSize()
                ? InventoryType.SlotType.CONTAINER
                : InventoryType.SlotType.QUICKBAR;
        return new InventoryClickEvent(
                player.getOpenInventory(),
                slotType,
                rawSlot,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL);
    }

    private String getDisplayName(PlayerMock player, int slot) {
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        assertNotNull(item);
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        return meta.getDisplayName();
    }

    private static final class TestGui extends BaseGui {

        private final int itemCount;
        private final AtomicInteger contentClicks;
        private final AtomicInteger backClicks;
        private boolean closed;

        private TestGui(TNexus plugin, PlayerMock player, int itemCount) {
            super(plugin, player, "&6&lテストGUI", 6);
            this.itemCount = itemCount;
            this.contentClicks = new AtomicInteger();
            this.backClicks = new AtomicInteger();
            this.closed = false;
        }

        @Override
        protected void buildContent() {
            setBackHandler(event -> this.backClicks.incrementAndGet());
            for (int index = 0; index < this.itemCount; index++) {
                addPaginatedItem(
                        createItem(Material.STONE, "&fItem " + index, List.of("&7Page item")),
                        event -> this.contentClicks.incrementAndGet());
            }
        }

        @Override
        protected void onClose() {
            this.closed = true;
        }
    }
}
