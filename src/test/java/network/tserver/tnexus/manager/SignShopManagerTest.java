package network.tserver.tnexus.manager;

import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.util.BlockPosition;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignShopManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldExecutePlayerShopBuyAndWriteAudit() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        PlayerMock buyer = this.server.addPlayer("Buyer");
        owner.addAttachment(plugin, "tnexus.shop.player", true);
        buyer.addAttachment(plugin, "tnexus.shop.use", true);
        plugin.getEconomyManager().deposit(buyer.getUniqueId(), 100.0D).get(5, TimeUnit.SECONDS);

        World world = owner.getWorld();
        Block chestBlock = world.getBlockAt(0, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 10));

        Block signBlock = world.getBlockAt(1, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = new SignShop(
                77L,
                ShopType.PLAYER,
                owner.getUniqueId(),
                owner.getName(),
                BlockPosition.from(signBlock),
                BlockPosition.from(chestBlock),
                new ItemStack(Material.DIAMOND),
                "Diamond",
                10.0D,
                5.0D,
                "Note",
                true);

        manager.executeTrade(buyer, shop, TradeAction.BUY, 4);

        waitUntil(() -> buyer.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 4));

        assertEquals(60.0D, plugin.getEconomyManager().getBalance(buyer.getUniqueId()).get(5, TimeUnit.SECONDS));
        assertEquals(40.0D, plugin.getEconomyManager().getBalance(owner.getUniqueId()).get(5, TimeUnit.SECONDS));
        assertEquals(6, countItems(((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory(), Material.DIAMOND));
        assertEquals(1, countTransactions(plugin, buyer, "SHOP_BUY"));
    }

    @Test
    void shouldCreateUnlinkedPlayerShopWithoutAdjacentChest() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock player = this.server.addPlayer("Owner");
        player.addAttachment(plugin, "tnexus.shop.player", true);

        World world = player.getWorld();
        Block signBlock = world.getBlockAt(5, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = manager.createShop(
                player,
                signBlock,
                ShopType.PLAYER,
                "",
                null,
                null);

        assertNotNull(shop);
        assertNull(shop.getLinkedChestPosition());
        assertNull(shop.getItemStack());
        waitUntil(() -> manager.getShop(signBlock) != null);
        assertEquals("§8[§6T-Nexus§8] §aSignShop を作成しました。", player.nextMessage());
        assertEquals("§8[§6T-Nexus§8] §eリンクツールを使ってこのショップをチェストに接続してください。", player.nextMessage());
    }

    @Test
    void shouldCancelCommandLinkModeAfterChestValidationError() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock player = this.server.addPlayer("Owner");
        player.addAttachment(plugin, "tnexus.shop.player", true);

        World world = player.getWorld();
        Block sourceChest = world.getBlockAt(15, 64, 0);
        sourceChest.setType(Material.CHEST);
        ((org.bukkit.block.Chest) sourceChest.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        Block signBlock = world.getBlockAt(16, 64, 0);
        signBlock.setType(Material.OAK_SIGN);
        Block emptyChest = world.getBlockAt(17, 64, 0);
        emptyChest.setType(Material.CHEST);

        SignShop shop = manager.createShop(
                player,
                signBlock,
                ShopType.PLAYER,
                "",
                sourceChest,
                new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        manager.beginLinkMode(player);
        assertTrue(manager.handleLinkInteraction(player, signBlock, false));
        assertTrue(manager.handleLinkInteraction(player, emptyChest, false));
        assertFalse(manager.handleLinkInteraction(player, emptyChest, false));
    }

    @Test
    void shouldEnforceLuckPermsPlayerShopLimit() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        owner.addAttachment(plugin, "tnexus.shop.player", true);
        TestPluginSupport.setLuckPermsMeta(owner.getUniqueId(), "tnexus.shop.limit", "1");

        World world = owner.getWorld();
        Block firstChest = world.getBlockAt(60, 64, 0);
        firstChest.setType(Material.CHEST);
        ((org.bukkit.block.Chest) firstChest.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        Block firstSign = world.getBlockAt(61, 64, 0);
        firstSign.setType(Material.OAK_SIGN);

        SignShop firstShop = manager.createShop(
                owner,
                firstSign,
                ShopType.PLAYER,
                "",
                firstChest,
                new ItemStack(Material.DIAMOND));
        assertNotNull(firstShop);

        Block secondChest = world.getBlockAt(62, 64, 0);
        secondChest.setType(Material.CHEST);
        ((org.bukkit.block.Chest) secondChest.getState()).getBlockInventory().addItem(new ItemStack(Material.GOLD_INGOT, 1));
        Block secondSign = world.getBlockAt(63, 64, 0);
        secondSign.setType(Material.OAK_SIGN);

        SignShop secondShop = manager.createShop(
                owner,
                secondSign,
                ShopType.PLAYER,
                "",
                secondChest,
                new ItemStack(Material.GOLD_INGOT));

        assertNull(secondShop);
        waitUntil(() -> manager.getShop(firstSign) != null);
        assertEquals(1, manager.getOwnedShops(owner.getUniqueId()).size());
    }

    @Test
    void shouldRejectServerShopCreationWithoutAdminPermission() {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock player = this.server.addPlayer("Builder");

        World world = player.getWorld();
        Block signBlock = world.getBlockAt(10, 64, 0);
        signBlock.setType(Material.OAK_SIGN);
        Block chestBlock = world.getBlockAt(11, 64, 0);
        chestBlock.setType(Material.CHEST);

        SignShop shop = manager.createShop(
                player,
                signBlock,
                ShopType.SERVER,
                "",
                chestBlock,
                new ItemStack(Material.DIAMOND));

        assertNull(shop);
    }

    @Test
    void shouldRejectBannedServerShopMaterialWithoutBypass() {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock admin = this.server.addPlayer("Admin");
        admin.addAttachment(plugin, "tnexus.shop.admin", true);

        World world = admin.getWorld();
        Block signBlock = world.getBlockAt(20, 64, 0);
        signBlock.setType(Material.OAK_SIGN);
        Block chestBlock = world.getBlockAt(21, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.BARRIER, 1));

        SignShop shop = manager.createShop(
                admin,
                signBlock,
                ShopType.SERVER,
                "",
                chestBlock,
                new ItemStack(Material.BARRIER));

        assertNull(shop);
    }

    @Test
    void shouldAllowBannedServerShopMaterialWithBypass() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock admin = this.server.addPlayer("Admin");
        admin.addAttachment(plugin, "tnexus.shop.admin", true);
        admin.addAttachment(plugin, "tnexus.shop.bypass.ban", true);

        World world = admin.getWorld();
        Block signBlock = world.getBlockAt(30, 64, 0);
        signBlock.setType(Material.OAK_SIGN);
        Block chestBlock = world.getBlockAt(31, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.BARRIER, 1));

        SignShop shop = manager.createShop(
                admin,
                signBlock,
                ShopType.SERVER,
                "",
                chestBlock,
                new ItemStack(Material.BARRIER));

        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);
    }

    @Test
    void shouldApplyLegacyColorCodesToRenderedSignText() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock admin = this.server.addPlayer("Admin");
        admin.addAttachment(plugin, "tnexus.shop.admin", true);

        World world = admin.getWorld();
        Block chestBlock = world.getBlockAt(35, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        Block signBlock = world.getBlockAt(36, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = manager.createShop(
                admin,
                signBlock,
                ShopType.SERVER,
                "Color",
                chestBlock,
                new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        Sign sign = (Sign) signBlock.getState();
        assertEquals("§c[ServerShop]", LegacyComponentSerializer.legacySection().serialize(sign.getSide(Side.FRONT).line(0)));
        assertTrue(LegacyComponentSerializer.legacySection().serialize(sign.getSide(Side.FRONT).line(2)).startsWith("§cB -"));
    }

    @Test
    void shouldKeepShopAvailableWhenOnlyBuySideIsUnavailable() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        owner.addAttachment(plugin, "tnexus.shop.player", true);
        plugin.getEconomyManager().deposit(owner.getUniqueId(), 100.0D).get(5, TimeUnit.SECONDS);

        World world = owner.getWorld();
        Block chestBlock = world.getBlockAt(90, 64, 0);
        chestBlock.setType(Material.CHEST);
        Block signBlock = world.getBlockAt(91, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(10.0D);
        liveShop.setSellPrice(5.0D);
        manager.refreshShopDisplay(liveShop);

        waitUntil(() -> signLineContains(signBlock, 0, ChatColor.COLOR_CHAR + "a[Shop]")
                && signLineContains(signBlock, 2, ChatColor.COLOR_CHAR + "cB 10")
                && signLineContains(signBlock, 2, ChatColor.COLOR_CHAR + "aS 5"));
    }

    @Test
    void shouldKeepShopAvailableWhenOnlySellSideIsUnavailable() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        owner.addAttachment(plugin, "tnexus.shop.player", true);

        World world = owner.getWorld();
        Block chestBlock = world.getBlockAt(92, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        Block signBlock = world.getBlockAt(93, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(10.0D);
        liveShop.setSellPrice(5.0D);
        manager.refreshShopDisplay(liveShop);

        waitUntil(() -> signLineContains(signBlock, 0, ChatColor.COLOR_CHAR + "a[Shop]")
                && signLineContains(signBlock, 2, ChatColor.COLOR_CHAR + "aB 10")
                && signLineContains(signBlock, 2, ChatColor.COLOR_CHAR + "cS 5"));
    }


    @Test
    void shouldGrayOutUnsupportedSellSideOnSign() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        owner.addAttachment(plugin, "tnexus.shop.player", true);

        World world = owner.getWorld();
        Block chestBlock = world.getBlockAt(94, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 8));
        Block signBlock = world.getBlockAt(95, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(10.0D);
        liveShop.setSellPrice(null);
        manager.refreshShopDisplay(liveShop);

        waitUntil(() -> signLineContains(signBlock, 2, ChatColor.COLOR_CHAR + "aB 10")
                && signLineContains(signBlock, 2, ChatColor.COLOR_CHAR + "8S -"));
    }

    @Test
    void shouldGrayOutUnsupportedBuySideOnSign() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        owner.addAttachment(plugin, "tnexus.shop.player", true);
        plugin.getEconomyManager().deposit(owner.getUniqueId(), 100.0D).get(5, TimeUnit.SECONDS);

        World world = owner.getWorld();
        Block chestBlock = world.getBlockAt(96, 64, 0);
        chestBlock.setType(Material.CHEST);
        Block signBlock = world.getBlockAt(97, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(null);
        liveShop.setSellPrice(5.0D);
        manager.refreshShopDisplay(liveShop);

        waitUntil(() -> signLineContains(signBlock, 2, ChatColor.COLOR_CHAR + "8B -")
                && signLineContains(signBlock, 2, ChatColor.COLOR_CHAR + "aS 5"));
    }
    @Test
    void shouldReleaseSignProtectionAfterDeletingShop() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock admin = this.server.addPlayer("Admin");
        admin.addAttachment(plugin, "tnexus.shop.admin", true);

        World world = admin.getWorld();
        Block chestBlock = world.getBlockAt(37, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        Block signBlock = world.getBlockAt(38, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = manager.createShop(
                admin,
                signBlock,
                ShopType.SERVER,
                "Delete",
                chestBlock,
                new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        manager.deleteShop(shop);

        Sign sign = (Sign) signBlock.getState();
        assertFalse(sign.isWaxed());
        assertEquals("", LegacyComponentSerializer.legacySection().serialize(sign.getSide(Side.FRONT).line(0)));
        assertNull(manager.getShop(signBlock));
    }

    @Test
    void shouldPersistServerShopItemAndAllowBuyingAfterChestRemoval() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock admin = this.server.addPlayer("Admin");
        PlayerMock buyer = this.server.addPlayer("Buyer");
        admin.addAttachment(plugin, "tnexus.shop.admin", true);
        buyer.addAttachment(plugin, "tnexus.shop.use", true);
        plugin.getEconomyManager().deposit(buyer.getUniqueId(), 100.0D).get(5, TimeUnit.SECONDS);

        World world = admin.getWorld();
        Block chestBlock = world.getBlockAt(40, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        Block signBlock = world.getBlockAt(41, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop created = manager.createShop(
                admin,
                signBlock,
                ShopType.SERVER,
                "Server stock",
                chestBlock,
                new ItemStack(Material.DIAMOND));
        assertNotNull(created);

        waitUntil(() -> manager.getShop(signBlock) != null
                && countServerShopRows(plugin, created.getOwnerUuid()) == 1
                && hasSerializedItem(plugin, created.getOwnerUuid()));

        chestBlock.setType(Material.AIR);
        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(10.0D);

        manager.executeTrade(buyer, liveShop, TradeAction.BUY, 5);

        waitUntil(() -> buyer.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 5));

        assertEquals(50.0D, plugin.getEconomyManager().getBalance(buyer.getUniqueId()).get(5, TimeUnit.SECONDS));
        assertEquals(1, countTransactions(plugin, buyer, "SHOP_BUY"));
    }

    @Test
    void shouldSnapshotServerShopItemFromChestAtCreation() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock admin = this.server.addPlayer("Admin");
        PlayerMock buyer = this.server.addPlayer("Buyer");
        admin.addAttachment(plugin, "tnexus.shop.admin", true);
        buyer.addAttachment(plugin, "tnexus.shop.use", true);
        plugin.getEconomyManager().deposit(buyer.getUniqueId(), 100.0D).get(5, TimeUnit.SECONDS);

        World world = admin.getWorld();
        Block chestBlock = world.getBlockAt(42, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        Block signBlock = world.getBlockAt(43, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop created = manager.createShop(
                admin,
                signBlock,
                ShopType.SERVER,
                "Snapshot",
                chestBlock,
                new ItemStack(Material.EMERALD));
        assertNotNull(created);

        waitUntil(() -> manager.getShop(signBlock) != null);

        chestBlock.setType(Material.AIR);
        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(10.0D);

        manager.executeTrade(buyer, liveShop, TradeAction.BUY, 1);

        waitUntil(() -> buyer.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 1));

        assertEquals(Material.DIAMOND, liveShop.getItemStack().getType());
        assertFalse(buyer.getInventory().contains(Material.EMERALD));
    }

    @Test
    void shouldAutoAdjustBuyAmountToPlayerBalance() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        PlayerMock buyer = this.server.addPlayer("Buyer");
        owner.addAttachment(plugin, "tnexus.shop.player", true);
        buyer.addAttachment(plugin, "tnexus.shop.use", true);
        plugin.getEconomyManager().deposit(buyer.getUniqueId(), 25.0D).get(5, TimeUnit.SECONDS);

        World world = owner.getWorld();
        Block chestBlock = world.getBlockAt(70, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 10));
        Block signBlock = world.getBlockAt(71, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(10.0D);

        manager.executeTrade(buyer, liveShop, TradeAction.BUY, 8);

        waitUntil(() -> buyer.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 2));

        assertEquals(5.0D, plugin.getEconomyManager().getBalance(buyer.getUniqueId()).get(5, TimeUnit.SECONDS));
        String adjustmentMessage = buyer.nextMessage();
        assertNotNull(adjustmentMessage);
        assertTrue(adjustmentMessage.contains("2"));
    }

    @Test
    void shouldExplainOutOfStockWhenBuyUnavailable() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        PlayerMock buyer = this.server.addPlayer("Buyer");
        owner.addAttachment(plugin, "tnexus.shop.player", true);
        buyer.addAttachment(plugin, "tnexus.shop.use", true);
        plugin.getEconomyManager().deposit(buyer.getUniqueId(), 100.0D).get(5, TimeUnit.SECONDS);

        World world = owner.getWorld();
        Block chestBlock = world.getBlockAt(72, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        Block signBlock = world.getBlockAt(73, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);
        liveShop.setBuyPrice(10.0D);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().clear();

        manager.executeTrade(buyer, liveShop, TradeAction.BUY, 1);

        waitUntil(() -> buyer.nextMessage() != null);
        String unavailableMessage = buyer.nextMessage();
        assertNotNull(unavailableMessage);
        assertTrue(unavailableMessage.contains("在庫"));
    }

    @Test
    void shouldPersistUpdatedNote() throws Exception {
        TNexus plugin = loadPlugin();
        SignShopManager manager = plugin.getSignShopManager();
        PlayerMock owner = this.server.addPlayer("Owner");
        owner.addAttachment(plugin, "tnexus.shop.player", true);

        World world = owner.getWorld();
        Block chestBlock = world.getBlockAt(74, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        Block signBlock = world.getBlockAt(75, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = manager.createShop(owner, signBlock, ShopType.PLAYER, "", chestBlock, new ItemStack(Material.DIAMOND));
        assertNotNull(shop);
        waitUntil(() -> manager.getShop(signBlock) != null);

        SignShop liveShop = manager.getShop(signBlock);
        assertNotNull(liveShop);

        manager.updateNote(owner, liveShop, "  New Note  ");

        waitUntil(() -> "New Note".equals(liveShop.getNote()));
        Sign sign = (Sign) signBlock.getState();
        assertTrue(LegacyComponentSerializer.legacySection().serialize(sign.getSide(Side.FRONT).line(3)).contains("New Note"));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        assertNotNull(plugin.getSignShopManager());
        return plugin;
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

    private int countItems(org.bukkit.inventory.Inventory inventory, Material material) {
        int total = 0;
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && itemStack.getType() == material) {
                total += itemStack.getAmount();
            }
        }
        return total;
    }

    private int countTransactions(TNexus plugin, Player player, String type) throws Exception {
        try (var connection = plugin.getDatabaseManager().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM tnexus_transactions WHERE player_uuid = ? AND type = ?")) {
            statement.setString(1, player.getUniqueId().toString());
            statement.setString(2, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private boolean signLineContains(Block signBlock, int lineIndex, String expectedFragment) {
        Sign sign = (Sign) signBlock.getState();
        String serialized = LegacyComponentSerializer.legacySection().serialize(sign.getSide(Side.FRONT).line(lineIndex));
        return serialized.contains(expectedFragment);
    }

    private int countServerShopRows(TNexus plugin, UUID ownerUuid) {
        try (var connection = plugin.getDatabaseManager().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM tnexus_shops WHERE owner_uuid = ? AND shop_type = ?")) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, ShopType.SERVER.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (Exception exception) {
            return 0;
        }
    }

    private boolean hasSerializedItem(TNexus plugin, UUID ownerUuid) {
        try (var connection = plugin.getDatabaseManager().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT item_stack FROM tnexus_shops WHERE owner_uuid = ? AND shop_type = ?")) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, ShopType.SERVER.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }
                String serialized = resultSet.getString(1);
                return serialized != null && !serialized.isBlank();
            }
        } catch (Exception exception) {
            return false;
        }
    }

}
