package network.tserver.tnexus.manager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.PlayerStatsRankingRepository;
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
import network.tserver.tnexus.manager.PlayerStatsRankingManager.RankingSnapshot;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsPeriodFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlayerStatsRankingManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRankPlayersAcrossPeriods() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        UUID viewerId = UUID.randomUUID();
        UUID topPlayerId = UUID.randomUUID();
        UUID recentPlayerId = UUID.randomUUID();

        PlayerStatsRepository repository = new PlayerStatsRepository(plugin.getDatabaseManager());
        repository.addPlayTime(topPlayerId, 7200L).get(5, TimeUnit.SECONDS);
        repository.addPlayTime(recentPlayerId, 3600L).get(5, TimeUnit.SECONDS);
        insertSession(plugin, topPlayerId, "2026-06-01T08:00:00Z", "2026-06-01T10:00:00Z", 7200L);
        insertSession(plugin, recentPlayerId, "2026-06-11T00:30:00Z", "2026-06-11T01:30:00Z", 3600L);

        PlayerStatsRankingManager rankingManager = new PlayerStatsRankingManager(
                plugin,
                new PlayerStatsRankingRepository(plugin.getDatabaseManager()),
                plugin.getPlayerStatsManager(),
                Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneId.of("UTC")));

        RankingSnapshot allTime = rankingManager
                .loadRanking(viewerId, StatsPeriodFilter.ALL_TIME)
                .get(5, TimeUnit.SECONDS);
        RankingSnapshot today = rankingManager
                .loadRanking(viewerId, StatsPeriodFilter.TODAY)
                .get(5, TimeUnit.SECONDS);

        assertEquals(2, allTime.entries().size());
        assertEquals(topPlayerId, allTime.entries().getFirst().playerId());
        assertEquals(7200L, allTime.entries().getFirst().playTimeSeconds());

        assertEquals(1, today.entries().size());
        assertEquals(recentPlayerId, today.entries().getFirst().playerId());
        assertEquals(3600L, today.entries().getFirst().playTimeSeconds());
        assertNotNull(today.entries().getFirst());
    }

    private void insertSession(
            TNexus plugin,
            java.util.UUID playerId,
            String startIso,
            String endIso,
            long durationSeconds) throws Exception {
        try (var connection = plugin.getDatabaseManager().getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO tnexus_player_play_sessions "
                             + "(player_uuid, session_start, session_end, duration_seconds) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, playerId.toString());
            statement.setTimestamp(2, java.sql.Timestamp.from(Instant.parse(startIso)));
            statement.setTimestamp(3, java.sql.Timestamp.from(Instant.parse(endIso)));
            statement.setLong(4, durationSeconds);
            statement.executeUpdate();
        }
    }
}
