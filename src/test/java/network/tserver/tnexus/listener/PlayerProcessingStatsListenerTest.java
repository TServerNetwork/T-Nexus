package network.tserver.tnexus.listener;

import java.util.List;
import java.util.Map;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.SimpleInventoryViewMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerProcessingStatsListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordSmeltAndBrewStatsForAttributedPlayer() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Processor");
        Block furnaceBlock = player.getLocation().add(1.0D, 0.0D, 0.0D).getBlock();
        furnaceBlock.setType(Material.FURNACE);
        Block brewingBlock = player.getLocation().add(2.0D, 0.0D, 0.0D).getBlock();
        brewingBlock.setType(Material.BREWING_STAND);

        plugin.getPlayerStatsManager().markProcessingStationInteraction(player, furnaceBlock);
        plugin.getPlayerStatsManager().markProcessingStationInteraction(player, brewingBlock);

        this.server.getPluginManager().callEvent(new FurnaceSmeltEvent(
                furnaceBlock,
                new ItemStack(Material.SAND),
                new ItemStack(Material.GLASS)));
        BrewingStand brewingStand = (BrewingStand) brewingBlock.getState();
        this.server.getPluginManager().callEvent(new BrewEvent(
                brewingBlock,
                brewingStand.getInventory(),
                List.of(
                        new ItemStack(Material.POTION),
                        new ItemStack(Material.POTION),
                        new ItemStack(Material.POTION)),
                20));
        plugin.getPlayerStatsManager().flushPendingProcessingStats().join();

        assertEquals(1, readPlayerStat(plugin, player, "brew_count"));
        assertEquals(1, readMaterialCount(plugin, player, "tnexus_smelt_stats", Material.GLASS));
    }

    @Test
    void shouldRecordEnchantStatsFromEvent() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Enchanter");
        Block enchantingBlock = player.getLocation().add(1.0D, 0.0D, 0.0D).getBlock();
        enchantingBlock.setType(Material.ENCHANTING_TABLE);

        this.server.getPluginManager().callEvent(createEnchantEvent(
                player,
                enchantingBlock,
                new ItemStack(Material.DIAMOND_PICKAXE),
                Map.of(Enchantment.EFFICIENCY, 4, Enchantment.UNBREAKING, 3)));
        plugin.getPlayerStatsManager().flushPendingProcessingStats().join();

        assertEquals(1, readEnchantCount(plugin, player, Enchantment.EFFICIENCY));
        assertEquals(1, readEnchantCount(plugin, player, Enchantment.UNBREAKING));
        assertEquals(1, readMaterialCount(plugin, player, "tnexus_enchant_item_stats", Material.DIAMOND_PICKAXE));
    }

    @Test
    void shouldIgnoreCancelledProcessingEvents() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("CancelledProcessor");
        Block furnaceBlock = player.getLocation().add(1.0D, 0.0D, 0.0D).getBlock();
        furnaceBlock.setType(Material.FURNACE);
        plugin.getPlayerStatsManager().markProcessingStationInteraction(player, furnaceBlock);

        FurnaceSmeltEvent smeltEvent = new FurnaceSmeltEvent(
                furnaceBlock,
                new ItemStack(Material.SAND),
                new ItemStack(Material.GLASS));
        smeltEvent.setCancelled(true);

        Block brewingBlock = player.getLocation().add(2.0D, 0.0D, 0.0D).getBlock();
        brewingBlock.setType(Material.BREWING_STAND);
        BrewingStand brewingStand = (BrewingStand) brewingBlock.getState();
        BrewEvent brewEvent = new BrewEvent(
                brewingBlock,
                brewingStand.getInventory(),
                List.of(new ItemStack(Material.POTION)),
                20);
        brewEvent.setCancelled(true);

        Block enchantingBlock = player.getLocation().add(3.0D, 0.0D, 0.0D).getBlock();
        enchantingBlock.setType(Material.ENCHANTING_TABLE);
        EnchantItemEvent enchantEvent = createEnchantEvent(
                player,
                enchantingBlock,
                new ItemStack(Material.DIAMOND_SWORD),
                Map.of(Enchantment.SHARPNESS, 4));
        enchantEvent.setCancelled(true);

        this.server.getPluginManager().callEvent(smeltEvent);
        this.server.getPluginManager().callEvent(brewEvent);
        this.server.getPluginManager().callEvent(enchantEvent);
        plugin.getPlayerStatsManager().flushPendingProcessingStats().join();

        assertEquals(0, readPlayerStat(plugin, player, "brew_count"));
        assertEquals(0, readMaterialCount(plugin, player, "tnexus_smelt_stats", Material.GLASS));
        assertEquals(0, readEnchantCount(plugin, player, Enchantment.SHARPNESS));
        assertEquals(0, readMaterialCount(plugin, player, "tnexus_enchant_item_stats", Material.DIAMOND_SWORD));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private EnchantItemEvent createEnchantEvent(
            PlayerMock player,
            Block enchantingBlock,
            ItemStack item,
            Map<Enchantment, Integer> enchantsToAdd) {
        SimpleInventoryViewMock view = new SimpleInventoryViewMock(
                player,
                this.server.createInventory(null, InventoryType.ENCHANTING),
                player.getInventory(),
                InventoryType.ENCHANTING);
        return new EnchantItemEvent(
                player,
                view,
                enchantingBlock,
                item,
                30,
                enchantsToAdd,
                enchantsToAdd.keySet().iterator().next(),
                enchantsToAdd.values().iterator().next(),
                2);
    }

    private int readPlayerStat(TNexus plugin, PlayerMock player, String columnName) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT " + columnName + " FROM tnexus_player_stats WHERE player_uuid = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(columnName) : 0;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
    }

    private int readMaterialCount(TNexus plugin, PlayerMock player, String tableName, Material material) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT count FROM " + tableName + " WHERE player_uuid = ? AND material = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, material.name());
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt("count") : 0;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
    }

    private int readEnchantCount(TNexus plugin, PlayerMock player, Enchantment enchantment) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT count FROM tnexus_enchant_stats WHERE player_uuid = ? AND enchantment = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, enchantment.getKey().getKey());
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt("count") : 0;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
    }
}
