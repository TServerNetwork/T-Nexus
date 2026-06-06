package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerBlockStatsListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordPlacedAndBrokenBlocksFromPlayerEvents() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Builder");
        Block placedBlock = player.getLocation().add(1.0D, 0.0D, 0.0D).getBlock();
        placedBlock.setType(Material.STONE);
        Block brokenBlock = player.getLocation().add(2.0D, 0.0D, 0.0D).getBlock();
        brokenBlock.setType(Material.DIRT);

        this.server.getPluginManager().callEvent(createPlaceEvent(placedBlock, player));
        this.server.getPluginManager().callEvent(new BlockBreakEvent(brokenBlock, player));
        plugin.getPlayerStatsManager().flushPendingBlockStats().join();

        assertEquals(1, readPlayerStat(plugin, player, "blocks_placed"));
        assertEquals(1, readPlayerStat(plugin, player, "blocks_broken"));
        assertEquals(1, readBlockMaterialStat(plugin, player, Material.STONE, "placed_count"));
        assertEquals(1, readBlockMaterialStat(plugin, player, Material.DIRT, "broken_count"));
    }

    @Test
    void shouldIgnoreCancelledEvents() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Cancelled");
        Block placedBlock = player.getLocation().add(1.0D, 0.0D, 0.0D).getBlock();
        placedBlock.setType(Material.STONE);
        Block brokenBlock = player.getLocation().add(2.0D, 0.0D, 0.0D).getBlock();
        brokenBlock.setType(Material.DIRT);
        BlockPlaceEvent placeEvent = createPlaceEvent(placedBlock, player);
        placeEvent.setCancelled(true);
        BlockBreakEvent breakEvent = new BlockBreakEvent(brokenBlock, player);
        breakEvent.setCancelled(true);

        this.server.getPluginManager().callEvent(placeEvent);
        this.server.getPluginManager().callEvent(breakEvent);
        plugin.getPlayerStatsManager().flushPendingBlockStats().join();

        assertEquals(0, readPlayerStat(plugin, player, "blocks_placed"));
        assertEquals(0, readPlayerStat(plugin, player, "blocks_broken"));
    }

    @Test
    void shouldIgnoreCreativeModeEvents() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Creative");
        player.setGameMode(GameMode.CREATIVE);
        Block placedBlock = player.getLocation().add(1.0D, 0.0D, 0.0D).getBlock();
        placedBlock.setType(Material.WATER);

        this.server.getPluginManager().callEvent(createPlaceEvent(placedBlock, player));
        plugin.getPlayerStatsManager().flushPendingBlockStats().join();

        assertEquals(0, readPlayerStat(plugin, player, "blocks_placed"));
        assertEquals(0, readBlockMaterialStat(plugin, player, Material.WATER, "placed_count"));
    }

    @Test
    void shouldIgnoreWorldEditSuppressedEvents() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Editor");
        Block placedBlock = player.getLocation().add(1.0D, 0.0D, 0.0D).getBlock();
        placedBlock.setType(Material.STONE);

        plugin.getPlayerStatsManager().markWorldEditOperation(player.getUniqueId());
        this.server.getPluginManager().callEvent(createPlaceEvent(placedBlock, player));
        plugin.getPlayerStatsManager().flushPendingBlockStats().join();

        assertEquals(0, readPlayerStat(plugin, player, "blocks_placed"));
        assertEquals(0, readBlockMaterialStat(plugin, player, Material.STONE, "placed_count"));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private BlockPlaceEvent createPlaceEvent(Block placedBlock, PlayerMock player) {
        Block replacedBlock = player.getLocation().getBlock();
        return new BlockPlaceEvent(
                placedBlock,
                replacedBlock.getState(),
                replacedBlock,
                new ItemStack(Material.WATER_BUCKET),
                player,
                true,
                EquipmentSlot.HAND);
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

    private int readBlockMaterialStat(TNexus plugin, PlayerMock player, Material material, String columnName) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT " + columnName + " FROM tnexus_block_stats WHERE player_uuid = ? AND material = ?")) {
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
