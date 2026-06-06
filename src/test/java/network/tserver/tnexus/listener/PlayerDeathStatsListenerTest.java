package network.tserver.tnexus.listener;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDeathStatsListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordKillerPlayerNameAndRespawnCount() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock victim = this.server.addPlayer("Victim");
        PlayerMock killer = this.server.addPlayer("Killer");

        PlayerDeathEvent deathEvent = new PlayerDeathEvent(
                victim,
                DamageSource.builder(DamageType.PLAYER_ATTACK)
                        .withCausingEntity(killer)
                        .withDirectEntity(killer)
                        .build(),
                List.of(),
                0,
                Component.text("Victim was slain by Killer"),
                true);
        this.server.getPluginManager().callEvent(deathEvent);
        this.server.getPluginManager().callEvent(new PlayerRespawnEvent(
                victim,
                victim.getLocation(),
                false,
                false,
                false,
                PlayerRespawnEvent.RespawnReason.DEATH));

        waitUntil(() -> readCauseCount(plugin, victim, "Killer") == 1 && readPlayerStat(plugin, victim, "respawns") == 1);

        assertEquals(1, readPlayerStat(plugin, victim, "deaths"));
        assertEquals(1, readPlayerStat(plugin, victim, "respawns"));
        assertEquals(1, readCauseCount(plugin, victim, "Killer"));
    }

    @Test
    void shouldMapOutOfWorldDeathsToVoid() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock victim = this.server.addPlayer("VoidVictim");
        World world = victim.getWorld();
        Location respawnLocation = new Location(world, 0.0D, 64.0D, 0.0D);

        PlayerDeathEvent deathEvent = new PlayerDeathEvent(
                victim,
                DamageSource.builder(DamageType.OUT_OF_WORLD).build(),
                List.of(),
                0,
                Component.text("VoidVictim fell out of the world"),
                true);
        this.server.getPluginManager().callEvent(deathEvent);
        this.server.getPluginManager().callEvent(new PlayerRespawnEvent(
                victim,
                respawnLocation,
                false,
                false,
                false,
                PlayerRespawnEvent.RespawnReason.DEATH));

        waitUntil(() -> readCauseCount(plugin, victim, "VOID") == 1);

        assertEquals(1, readPlayerStat(plugin, victim, "deaths"));
        assertEquals(1, readPlayerStat(plugin, victim, "respawns"));
        assertEquals(1, readCauseCount(plugin, victim, "VOID"));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
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

    private int readCauseCount(TNexus plugin, PlayerMock player, String cause) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT count FROM tnexus_death_stats WHERE player_uuid = ? AND cause = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, cause);
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt("count") : 0;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
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
