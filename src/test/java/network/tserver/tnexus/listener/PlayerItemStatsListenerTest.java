package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerItemStatsListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordPickupAndDropItemAmounts() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Collector");
        var world = player.getWorld();

        Item pickedUpItem = world.dropItem(new Location(world, 3.0D, 64.0D, 3.0D), new ItemStack(Material.STONE, 16));
        Item droppedItem = world.dropItem(new Location(world, 4.0D, 64.0D, 4.0D), new ItemStack(Material.DIRT, 6));

        this.server.getPluginManager().callEvent(new EntityPickupItemEvent(player, pickedUpItem, 0));
        this.server.getPluginManager().callEvent(new PlayerDropItemEvent(player, droppedItem));
        plugin.getPlayerStatsManager().flushPendingItemStats().join();

        assertEquals(16, readCount(plugin, "pickup_count", player, Material.STONE));
        assertEquals(6, readCount(plugin, "drop_count", player, Material.DIRT));
    }

    @Test
    void shouldIgnoreCancelledAndCreativeEvents() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("CreativeCollector");
        player.setGameMode(GameMode.CREATIVE);
        var world = player.getWorld();

        Item pickupItem = world.dropItem(new Location(world, 5.0D, 64.0D, 5.0D), new ItemStack(Material.COBBLESTONE, 12));
        EntityPickupItemEvent pickupEvent = new EntityPickupItemEvent(player, pickupItem, 0);
        pickupEvent.setCancelled(true);

        Item dropItem = world.dropItem(new Location(world, 6.0D, 64.0D, 6.0D), new ItemStack(Material.SAND, 3));
        PlayerDropItemEvent dropEvent = new PlayerDropItemEvent(player, dropItem);
        dropEvent.setCancelled(true);

        this.server.getPluginManager().callEvent(pickupEvent);
        this.server.getPluginManager().callEvent(dropEvent);
        plugin.getPlayerStatsManager().flushPendingItemStats().join();

        assertEquals(0, readCount(plugin, "pickup_count", player, Material.COBBLESTONE));
        assertEquals(0, readCount(plugin, "drop_count", player, Material.SAND));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private int readCount(TNexus plugin, String columnName, PlayerMock player, Material material) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT " + columnName + " FROM tnexus_item_stats WHERE player_uuid = ? AND material = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, material.name());
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(columnName) : 0;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
    }
}
