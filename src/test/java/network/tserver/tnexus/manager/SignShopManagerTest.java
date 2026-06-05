package network.tserver.tnexus.manager;

import java.sql.ResultSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.util.BlockPosition;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        owner.addAttachment(plugin, "tnexus.shop.use", true);
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

}
