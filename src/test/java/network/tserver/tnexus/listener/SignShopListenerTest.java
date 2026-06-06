package network.tserver.tnexus.listener;

import java.util.function.BooleanSupplier;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.manager.ShopType;
import network.tserver.tnexus.manager.SignShop;
import network.tserver.tnexus.manager.SignShopManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignShopListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldBlockUnauthorizedPlayersFromOpeningLinkedShopChest() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        PlayerMock intruder = this.server.addPlayer("Intruder");
        owner.addAttachment(plugin, "tnexus.shop.player", true);

        World world = owner.getWorld();
        Block chestBlock = createChestWithItem(world, 0, Material.DIAMOND, 4);
        Block signBlock = createSign(world, 1);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        PlayerInteractEvent event = new PlayerInteractEvent(
                intruder,
                Action.RIGHT_CLICK_BLOCK,
                null,
                chestBlock,
                org.bukkit.block.BlockFace.UP,
                EquipmentSlot.HAND);

        this.server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertTrue(waitForNextMessage(intruder).contains("ショップチェスト"));
    }

    @Test
    void shouldAllowOwnersToOpenLinkedShopChest() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        owner.addAttachment(plugin, "tnexus.shop.player", true);

        World world = owner.getWorld();
        Block chestBlock = createChestWithItem(world, 10, Material.DIAMOND, 4);
        Block signBlock = createSign(world, 11);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        PlayerInteractEvent event = new PlayerInteractEvent(
                owner,
                Action.RIGHT_CLICK_BLOCK,
                null,
                chestBlock,
                org.bukkit.block.BlockFace.UP,
                EquipmentSlot.HAND);

        this.server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void shouldSkipBrowseGuiAndShowMessageWhenUnavailableShopIsClicked() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        PlayerMock viewer = this.server.addPlayer("Viewer");
        owner.addAttachment(plugin, "tnexus.shop.player", true);
        viewer.addAttachment(plugin, "tnexus.shop.use", true);

        World world = owner.getWorld();
        Block chestBlock = createChestWithItem(world, 20, Material.DIAMOND, 0);
        Block signBlock = createSign(world, 21);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(10.0D);
        liveShop.setSellPrice(null);
        manager.refreshShopDisplay(liveShop);

        PlayerInteractEvent event = new PlayerInteractEvent(
                viewer,
                Action.RIGHT_CLICK_BLOCK,
                null,
                signBlock,
                org.bukkit.block.BlockFace.UP,
                EquipmentSlot.HAND);

        this.server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertFalse(plugin.getGuiManager().hasOpenGui(viewer));
        assertTrue(waitForNextMessage(viewer).contains("在庫"));
    }

    @Test
    void shouldStillOpenEditGuiForOwnerWhenUnavailableShopIsClicked() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        owner.addAttachment(plugin, "tnexus.shop.player", true);

        World world = owner.getWorld();
        Block chestBlock = createChestWithItem(world, 30, Material.DIAMOND, 0);
        Block signBlock = createSign(world, 31);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(10.0D);
        liveShop.setSellPrice(null);
        manager.refreshShopDisplay(liveShop);

        PlayerInteractEvent event = new PlayerInteractEvent(
                owner,
                Action.RIGHT_CLICK_BLOCK,
                null,
                signBlock,
                org.bukkit.block.BlockFace.UP,
                EquipmentSlot.HAND);

        this.server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertTrue(plugin.getGuiManager().hasOpenGui(owner));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
    }

    private Block createChestWithItem(World world, int x, Material material, int amount) {
        Block chestBlock = world.getBlockAt(x, 64, 0);
        chestBlock.setType(Material.CHEST);
        if (amount > 0) {
            ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(material, amount));
        }
        return chestBlock;
    }

    private Block createSign(World world, int x) {
        Block signBlock = world.getBlockAt(x, 64, 0);
        signBlock.setType(Material.OAK_SIGN);
        return signBlock;
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

    private String waitForNextMessage(PlayerMock player) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            this.server.getScheduler().performOneTick();
            String message = player.nextMessage();
            if (message != null) {
                return message;
            }
            Thread.sleep(25L);
        }
        return "";
    }
}
