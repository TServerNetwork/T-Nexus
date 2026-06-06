package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerMovementStatsListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordDistanceFromPlayerMoveEvents() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Walker");
        Location from = new Location(player.getWorld(), 0.0D, 64.0D, 0.0D);
        Location to = new Location(player.getWorld(), 0.0D, 64.0D, 5.0D);

        this.server.getPluginManager().callEvent(new PlayerMoveEvent(player, from, to));
        plugin.getPlayerStatsManager().flushPendingDistanceStats().join();

        assertEquals(5.0D, readPlayerDistance(plugin, player));
        assertEquals(5.0D, readTravelDistance(plugin, player, "WALK"));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private double readPlayerDistance(TNexus plugin, PlayerMock player) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT distance FROM tnexus_player_stats WHERE player_uuid = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getDouble("distance") : 0.0D;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
    }

    private double readTravelDistance(TNexus plugin, PlayerMock player, String travelType) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT distance FROM tnexus_distance_stats WHERE player_uuid = ? AND travel_type = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, travelType);
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getDouble("distance") : 0.0D;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
    }
}
