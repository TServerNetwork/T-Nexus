package network.tserver.tnexus.gui.shop;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.manager.ShopType;
import network.tserver.tnexus.manager.SignShop;
import network.tserver.tnexus.manager.SignShopManager;
import network.tserver.tnexus.util.BlockPosition;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
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

class SignShopBrowseGuiTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRenderShowcaseHeaderAndPreservePreviewMeta() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        PlayerMock viewer = this.server.addPlayer("Viewer");
        owner.addAttachment(plugin, "tnexus.shop.player", true);

        ItemStack templateItem = createNamedItem(Material.DIAMOND_SWORD, "&dEpic Blade", List.of("&7Sharp and shiny"));
        Block chestBlock = createChestWithItem(owner.getWorld(), 0, templateItem, 12);
        Block signBlock = createSign(owner.getWorld(), 1);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, templateItem);
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(25.0D);
        liveShop.setSellPrice(10.0D);

        manager.openBrowseGui(viewer, liveShop);

        ItemStack infoItem = viewer.getOpenInventory().getTopInventory().getItem(3);
        assertNotNull(infoItem);
        assertEquals(Material.NAME_TAG, infoItem.getType());
        ItemMeta infoMeta = infoItem.getItemMeta();
        assertNotNull(infoMeta);
        assertTrue(infoMeta.getLore().stream().anyMatch(line -> line.contains("購入単価:")));
        assertTrue(infoMeta.getLore().stream().anyMatch(line -> line.contains("買取空き容量:")));

        ItemStack previewItem = viewer.getOpenInventory().getTopInventory().getItem(4);
        assertNotNull(previewItem);
        ItemMeta previewMeta = previewItem.getItemMeta();
        assertNotNull(previewMeta);
        ItemMeta expectedMeta = templateItem.getItemMeta();
        assertNotNull(expectedMeta);
        assertEquals(expectedMeta.getDisplayName(), previewMeta.getDisplayName());
        assertEquals(expectedMeta.getLore(), previewMeta.getLore());
    }

    @Test
    void shouldRefreshHeaderAndButtonsAfterPlayerShopTrade() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        PlayerMock buyer = this.server.addPlayer("Buyer");
        owner.addAttachment(plugin, "tnexus.shop.player", true);
        buyer.addAttachment(plugin, "tnexus.shop.use", true);
        plugin.getEconomyManager().deposit(buyer.getUniqueId(), 200.0D).get(5, TimeUnit.SECONDS);

        ItemStack templateItem = new ItemStack(Material.DIAMOND);
        Block chestBlock = createChestWithItem(owner.getWorld(), 10, templateItem, 8);
        Block signBlock = createSign(owner.getWorld(), 11);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, templateItem);
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(10.0D);
        liveShop.setSellPrice(null);

        manager.openBrowseGui(buyer, liveShop);

        this.server.getPluginManager().callEvent(createClickEvent(buyer, 20, ClickType.LEFT));

        waitUntil(() -> buyer.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 8));
        waitUntil(() -> hasLoreLine(buyer, 3, "現在の在庫: §f0"));

        assertTrue(plugin.getGuiManager().hasOpenGui(buyer));
        assertEquals("§81x", getDisplayName(buyer, 19));
        assertFalse(hasLoreLine(buyer, 3, "買取空き容量:"));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private Block createChestWithItem(World world, int x, ItemStack itemStack, int amount) {
        Block chestBlock = world.getBlockAt(x, 64, 0);
        chestBlock.setType(Material.CHEST);
        ItemStack stored = itemStack.clone();
        stored.setAmount(amount);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(stored);
        return chestBlock;
    }

    private Block createSign(World world, int x) {
        Block signBlock = world.getBlockAt(x, 64, 0);
        signBlock.setType(Material.OAK_SIGN);
        return signBlock;
    }

    private ItemStack createNamedItem(Material material, String displayName, List<String> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        assertNotNull(meta);
        meta.setDisplayName(displayName.replace('&', '§'));
        meta.setLore(lore.stream().map(line -> line.replace('&', '§')).toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private InventoryClickEvent createClickEvent(PlayerMock player, int rawSlot, ClickType clickType) {
        return new InventoryClickEvent(
                player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                rawSlot,
                clickType,
                InventoryAction.PICKUP_ALL);
    }

    private String getDisplayName(PlayerMock player, int slot) {
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        assertNotNull(item);
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        return meta.getDisplayName();
    }

    private boolean hasLoreLine(PlayerMock player, int slot, String fragment) {
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return false;
        }
        return meta.getLore().stream().anyMatch(line -> line.contains(fragment));
    }

    private void waitUntil(BooleanSupplier condition) throws Exception {
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
