package network.tserver.tnexus.listener;

import java.util.List;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerFarmingStatsListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordHarvestBreedAndFishStats() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Farmer");
        var world = player.getWorld();

        this.server.getPluginManager().callEvent(new PlayerHarvestBlockEvent(
                player,
                player.getLocation().getBlock(),
                List.of(new ItemStack(Material.WHEAT, 3), new ItemStack(Material.WHEAT_SEEDS, 2))));

        Cow mother = world.spawn(new Location(world, 4.0D, 64.0D, 4.0D), Cow.class);
        Cow father = world.spawn(new Location(world, 5.0D, 64.0D, 5.0D), Cow.class);
        Cow child = world.spawn(new Location(world, 6.0D, 64.0D, 6.0D), Cow.class);
        this.server.getPluginManager().callEvent(new EntityBreedEvent(
                child,
                mother,
                father,
                player,
                new ItemStack(Material.WHEAT),
                1));

        Item caughtItem = world.dropItem(new Location(world, 7.0D, 64.0D, 7.0D), new ItemStack(Material.COD));
        FishHook hook = world.spawn(new Location(world, 7.0D, 64.0D, 8.0D), FishHook.class);
        this.server.getPluginManager().callEvent(new PlayerFishEvent(
                player,
                caughtItem,
                hook,
                PlayerFishEvent.State.CAUGHT_FISH));

        plugin.getPlayerStatsManager().flushPendingFarmingStats().join();

        assertEquals(3, readCount(plugin, "tnexus_harvest_stats", "material", player, Material.WHEAT.name()));
        assertEquals(2, readCount(plugin, "tnexus_harvest_stats", "material", player, Material.WHEAT_SEEDS.name()));
        assertEquals(1, readCount(plugin, "tnexus_breed_stats", "entity_type", player, "COW"));
        assertEquals(1, readCount(plugin, "tnexus_fish_stats", "material", player, Material.COD.name()));
    }

    @Test
    void shouldIgnoreCancelledCreativeAndNonCatchEvents() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("CreativeFarmer");
        player.setGameMode(GameMode.CREATIVE);
        var world = player.getWorld();

        PlayerHarvestBlockEvent harvestEvent = new PlayerHarvestBlockEvent(
                player,
                player.getLocation().getBlock(),
                List.of(new ItemStack(Material.CARROT, 4)));
        harvestEvent.setCancelled(true);

        Cow mother = world.spawn(new Location(world, 9.0D, 64.0D, 9.0D), Cow.class);
        Cow father = world.spawn(new Location(world, 10.0D, 64.0D, 10.0D), Cow.class);
        Cow child = world.spawn(new Location(world, 11.0D, 64.0D, 11.0D), Cow.class);
        EntityBreedEvent breedEvent = new EntityBreedEvent(
                child,
                mother,
                father,
                player,
                new ItemStack(Material.WHEAT),
                1);
        breedEvent.setCancelled(true);

        FishHook hook = world.spawn(new Location(world, 12.0D, 64.0D, 12.0D), FishHook.class);
        PlayerFishEvent fishEvent = new PlayerFishEvent(
                player,
                null,
                hook,
                PlayerFishEvent.State.FISHING);

        this.server.getPluginManager().callEvent(harvestEvent);
        this.server.getPluginManager().callEvent(breedEvent);
        this.server.getPluginManager().callEvent(fishEvent);
        plugin.getPlayerStatsManager().flushPendingFarmingStats().join();

        assertEquals(0, readCount(plugin, "tnexus_harvest_stats", "material", player, Material.CARROT.name()));
        assertEquals(0, readCount(plugin, "tnexus_breed_stats", "entity_type", player, "COW"));
        assertEquals(0, readCount(plugin, "tnexus_fish_stats", "material", player, Material.COD.name()));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private int readCount(TNexus plugin, String tableName, String keyColumn, PlayerMock player, String keyValue) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT count FROM " + tableName + " WHERE player_uuid = ? AND " + keyColumn + " = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, keyValue);
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt("count") : 0;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
    }
}
