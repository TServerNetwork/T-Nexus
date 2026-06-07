package network.tserver.tnexus.listener;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerKillStatsListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordMobAndPlayerKillsForPlayerKillers() {
        TNexus plugin = loadPlugin();
        PlayerMock killer = this.server.addPlayer("Hunter");
        PlayerMock victim = this.server.addPlayer("Victim");
        PlayerKillStatsListener listener = new PlayerKillStatsListener(plugin);

        listener.onEntityDeath(createMobDeathEvent(killer, EntityType.ZOMBIE));
        listener.onEntityDeath(createPlayerDeathEvent(killer, victim.getUniqueId()));

        plugin.getPlayerStatsManager().flushPendingKillStats().join();

        assertEquals(1, readKillCount(plugin, killer, "ZOMBIE"));
        assertEquals(1, readKillCount(plugin, killer, victim.getUniqueId().toString()));
    }

    @Test
    void shouldIgnoreDeathsWithoutPlayerKillers() {
        TNexus plugin = loadPlugin();
        PlayerMock killer = this.server.addPlayer("Hunter");
        PlayerKillStatsListener listener = new PlayerKillStatsListener(plugin);

        listener.onEntityDeath(createMobDeathEvent(null, EntityType.ZOMBIE));
        plugin.getPlayerStatsManager().flushPendingKillStats().join();

        assertEquals(0, readKillCount(plugin, killer, "ZOMBIE"));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private EntityDeathEvent createMobDeathEvent(Player killer, EntityType entityType) {
        LivingEntity entity = (LivingEntity) createLivingEntityProxy(killer, entityType, null);
        return new EntityDeathEvent(
                entity,
                DamageSource.builder(killer == null ? DamageType.MOB_ATTACK : DamageType.PLAYER_ATTACK).build(),
                List.of(),
                0);
    }

    private EntityDeathEvent createPlayerDeathEvent(Player killer, UUID playerId) {
        Player entity = (Player) createLivingEntityProxy(killer, EntityType.PLAYER, playerId);
        return new EntityDeathEvent(
                entity,
                DamageSource.builder(DamageType.PLAYER_ATTACK).build(),
                List.of(),
                0);
    }

    private Object createLivingEntityProxy(Player killer, EntityType entityType, UUID playerId) {
        Class<?>[] interfaces = playerId == null
                ? new Class<?>[]{LivingEntity.class}
                : new Class<?>[]{Player.class};
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getKiller" -> killer;
            case "getType" -> entityType;
            case "getUniqueId" -> playerId;
            case "isDead" -> true;
            case "getHealth" -> 0.0D;
            case "toString" -> entityType.name();
            default -> defaultValue(method.getReturnType());
        };
        return Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                interfaces,
                handler);
    }

    private int readKillCount(TNexus plugin, Player player, String target) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT count FROM tnexus_kill_stats WHERE player_uuid = ? AND target = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, target);
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt("count") : 0;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        return null;
    }
}
