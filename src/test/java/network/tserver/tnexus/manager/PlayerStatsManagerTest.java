package network.tserver.tnexus.manager;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerStatsManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordFirstLoginOnceAndAccumulatePlayTimeAcrossSessions() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("StatsPlayer");
        MutableClock clock = new MutableClock(Instant.parse("2026-06-07T00:00:00Z"));
        PlayerStatsManager manager = new PlayerStatsManager(
                plugin,
                new PlayerStatsRepository(plugin.getDatabaseManager()),
                clock,
                new ConcurrentHashMap<>());

        manager.recordSessionStart(player).get(5, TimeUnit.SECONDS);
        clock.setInstant(Instant.parse("2026-06-07T00:01:30Z"));
        manager.recordSessionEnd(player).get(5, TimeUnit.SECONDS);

        Timestamp firstLogin = readFirstLogin(plugin, player);
        assertEquals(90L, readPlayTime(plugin, player));

        clock.setInstant(Instant.parse("2026-06-07T00:05:00Z"));
        manager.recordSessionStart(player).get(5, TimeUnit.SECONDS);
        clock.setInstant(Instant.parse("2026-06-07T00:06:00Z"));
        manager.recordSessionEnd(player).get(5, TimeUnit.SECONDS);

        assertEquals(150L, readPlayTime(plugin, player));
        assertEquals(firstLogin, readFirstLogin(plugin, player));
    }

    @Test
    void shouldFlushOnlineSessions() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock first = this.server.addPlayer("First");
        PlayerMock second = this.server.addPlayer("Second");
        MutableClock clock = new MutableClock(Instant.parse("2026-06-07T01:00:00Z"));
        PlayerStatsManager manager = new PlayerStatsManager(
                plugin,
                new PlayerStatsRepository(plugin.getDatabaseManager()),
                clock,
                new ConcurrentHashMap<>());

        manager.recordSessionStart(first).get(5, TimeUnit.SECONDS);
        manager.recordSessionStart(second).get(5, TimeUnit.SECONDS);

        clock.setInstant(Instant.parse("2026-06-07T01:02:00Z"));
        manager.flushOnlineSessions(this.server.getOnlinePlayers()).get(5, TimeUnit.SECONDS);

        assertEquals(120L, readPlayTime(plugin, first));
        assertEquals(120L, readPlayTime(plugin, second));
    }

    @Test
    void shouldRecordDeathsRespawnsAndDeathCauses() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("DeathsPlayer");
        PlayerStatsManager manager = new PlayerStatsManager(
                plugin,
                new PlayerStatsRepository(plugin.getDatabaseManager()),
                new MutableClock(Instant.parse("2026-06-07T02:00:00Z")),
                new ConcurrentHashMap<>());

        manager.recordDeath(player, "ZOMBIE").get(5, TimeUnit.SECONDS);
        manager.recordDeath(player, "ZOMBIE").get(5, TimeUnit.SECONDS);
        manager.recordDeath(player, "VOID").get(5, TimeUnit.SECONDS);
        manager.recordRespawn(player).get(5, TimeUnit.SECONDS);

        assertEquals(3, readIntStat(plugin, player, "deaths"));
        assertEquals(1, readIntStat(plugin, player, "respawns"));
        assertEquals(2, readDeathCauseCounts(plugin, player).get("ZOMBIE"));
        assertEquals(1, readDeathCauseCounts(plugin, player).get("VOID"));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private long readPlayTime(TNexus plugin, PlayerMock player) throws Exception {
        try (var connection = plugin.getDatabaseManager().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT play_time FROM tnexus_player_stats WHERE player_uuid = ?")) {
            statement.setString(1, player.getUniqueId().toString());
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("play_time");
            }
        }
    }

    private Timestamp readFirstLogin(TNexus plugin, PlayerMock player) throws Exception {
        try (var connection = plugin.getDatabaseManager().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT first_login FROM tnexus_player_stats WHERE player_uuid = ?")) {
            statement.setString(1, player.getUniqueId().toString());
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getTimestamp("first_login");
            }
        }
    }

    private int readIntStat(TNexus plugin, PlayerMock player, String columnName) throws Exception {
        try (var connection = plugin.getDatabaseManager().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT " + columnName + " FROM tnexus_player_stats WHERE player_uuid = ?")) {
            statement.setString(1, player.getUniqueId().toString());
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(columnName);
            }
        }
    }

    private Map<String, Integer> readDeathCauseCounts(TNexus plugin, PlayerMock player) throws Exception {
        Map<String, Integer> counts = new java.util.HashMap<>();
        try (var connection = plugin.getDatabaseManager().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT cause, count FROM tnexus_death_stats WHERE player_uuid = ?")) {
            statement.setString(1, player.getUniqueId().toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    counts.put(resultSet.getString("cause"), resultSet.getInt("count"));
                }
            }
        }
        return counts;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }
    }
}
