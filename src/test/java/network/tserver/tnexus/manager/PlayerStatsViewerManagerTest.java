package network.tserver.tnexus.manager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.BlockStatsDelta;
import network.tserver.tnexus.database.repository.EntityDamageDelta;
import network.tserver.tnexus.database.repository.ItemStatsDelta;
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
import network.tserver.tnexus.database.repository.TransactionRepository;
import network.tserver.tnexus.database.repository.TransactionRepository.AuditRecord;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStatsViewerManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldBuildStatsSnapshotAcrossCategories() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock viewer = this.server.addPlayer("Viewer");
        PlayerMock target = this.server.addPlayer("Target");

        seedStats(plugin, target);

        PlayerStatsViewerManager.PlayerStatsSnapshot snapshot = plugin.getPlayerStatsViewerManager()
                .loadSnapshot(
                        viewer.getUniqueId(),
                        target,
                        PlayerStatsViewerManager.StatsPeriodFilter.ALL_TIME)
                .get(5, TimeUnit.SECONDS);

        assertEquals(target.getUniqueId(), snapshot.targetId());
        assertEquals("Target", snapshot.targetName());
        assertFalse(snapshot.getEntries(PlayerStatsViewerManager.StatsCategory.GENERAL).isEmpty());
        assertFalse(snapshot.getEntries(PlayerStatsViewerManager.StatsCategory.ECONOMY).isEmpty());
        assertFalse(snapshot.getEntries(PlayerStatsViewerManager.StatsCategory.BLOCKS).isEmpty());
        assertFalse(snapshot.getEntries(PlayerStatsViewerManager.StatsCategory.COMBAT).isEmpty());
        assertFalse(snapshot.getEntries(PlayerStatsViewerManager.StatsCategory.ACTIVITY).isEmpty());
        assertNotNull(snapshot.getEntry("GENERAL_PLAY_TIME"));
        assertNotNull(snapshot.getEntry("BLOCK:STONE"));
        assertNotNull(snapshot.getEntry("COMBAT:ZOMBIE"));
        assertNotNull(snapshot.getEntry("ACTIVITY_CRAFT_TOTAL"));
    }

    @Test
    void shouldToggleFavoritesForViewer() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock viewer = this.server.addPlayer("Viewer");
        PlayerMock target = this.server.addPlayer("Target");

        seedStats(plugin, target);

        PlayerStatsViewerManager manager = plugin.getPlayerStatsViewerManager();
        PlayerStatsViewerManager.PlayerStatsSnapshot snapshot = manager
                .loadSnapshot(
                        viewer.getUniqueId(),
                        target,
                        PlayerStatsViewerManager.StatsPeriodFilter.ALL_TIME)
                .get(5, TimeUnit.SECONDS);
        PlayerStatsViewerManager.StatsEntry entry = snapshot.getEntry("BLOCK:STONE");
        assertNotNull(entry);

        PlayerStatsViewerManager.FavoriteToggleResult added = manager
                .toggleFavorite(viewer.getUniqueId(), snapshot, entry)
                .get(5, TimeUnit.SECONDS);
        assertEquals(PlayerStatsViewerManager.FavoriteToggleStatus.ADDED, added.status());
        assertTrue(snapshot.getFavorites().containsValue("BLOCK:STONE"));

        PlayerStatsViewerManager.FavoriteToggleResult removed = manager
                .toggleFavorite(viewer.getUniqueId(), snapshot, entry)
                .get(5, TimeUnit.SECONDS);
        assertEquals(PlayerStatsViewerManager.FavoriteToggleStatus.REMOVED, removed.status());
        assertFalse(snapshot.getFavorites().containsValue("BLOCK:STONE"));
    }

    @Test
    void shouldReportFullWhenFavoriteSlotsAreExhausted() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock viewer = this.server.addPlayer("Viewer");
        PlayerMock target = this.server.addPlayer("Target");

        seedStats(plugin, target);
        fillFavoriteSlots(plugin, viewer.getUniqueId());

        PlayerStatsViewerManager manager = plugin.getPlayerStatsViewerManager();
        PlayerStatsViewerManager.PlayerStatsSnapshot snapshot = manager
                .loadSnapshot(
                        viewer.getUniqueId(),
                        target,
                        PlayerStatsViewerManager.StatsPeriodFilter.ALL_TIME)
                .get(5, TimeUnit.SECONDS);
        PlayerStatsViewerManager.StatsEntry entry = snapshot.getEntry("BLOCK:STONE");
        assertNotNull(entry);

        PlayerStatsViewerManager.FavoriteToggleResult result = manager
                .toggleFavorite(viewer.getUniqueId(), snapshot, entry)
                .get(5, TimeUnit.SECONDS);

        assertEquals(PlayerStatsViewerManager.FavoriteToggleStatus.FULL, result.status());
        assertEquals(14, snapshot.getFavorites().size());
    }

    private void seedStats(TNexus plugin, PlayerMock target) throws Exception {
        PlayerStatsRepository repository = new PlayerStatsRepository(plugin.getDatabaseManager());
        repository.ensurePlayerExists(target.getUniqueId(), Instant.parse("2026-06-01T00:00:00Z")).get(5, TimeUnit.SECONDS);
        repository.addPlayTime(target.getUniqueId(), 7200L).get(5, TimeUnit.SECONDS);
        repository.incrementDeaths(target.getUniqueId()).get(5, TimeUnit.SECONDS);
        repository.incrementRespawns(target.getUniqueId()).get(5, TimeUnit.SECONDS);
        repository.incrementChatCount(target.getUniqueId()).get(5, TimeUnit.SECONDS);
        repository.incrementSleepCount(target.getUniqueId()).get(5, TimeUnit.SECONDS);
        repository.incrementPortalCount(target.getUniqueId()).get(5, TimeUnit.SECONDS);
        repository.addDistanceStats(
                        Map.of(target.getUniqueId(), 320.5D),
                        Map.of(target.getUniqueId(), Map.of("WALK", 320.5D)))
                .get(5, TimeUnit.SECONDS);
        repository.addBlockStats(
                        Map.of(target.getUniqueId(), 3),
                        Map.of(target.getUniqueId(), 5),
                        Map.of(target.getUniqueId(), Map.of("STONE", new BlockStatsDelta(3, 5))))
                .get(5, TimeUnit.SECONDS);
        repository.addEntityDamageStats(
                        Map.of(target.getUniqueId(), Map.of("ZOMBIE", new EntityDamageDelta(12.5D, 3.0D))))
                .get(5, TimeUnit.SECONDS);
        repository.addKillStats(Map.of(target.getUniqueId(), Map.of("ZOMBIE", 4))).get(5, TimeUnit.SECONDS);
        repository.addCraftStats(Map.of(target.getUniqueId(), Map.of(Material.CRAFTING_TABLE.name(), 2)))
                .get(5, TimeUnit.SECONDS);
        repository.addProcessingStats(
                        Map.of(target.getUniqueId(), 1),
                        Map.of(target.getUniqueId(), Map.of(Material.GLASS.name(), 2)),
                        Map.of(target.getUniqueId(), Map.of("efficiency", 1)),
                        Map.of(target.getUniqueId(), Map.of(Material.DIAMOND_PICKAXE.name(), 1)))
                .get(5, TimeUnit.SECONDS);
        repository.addFarmingStats(
                        Map.of(target.getUniqueId(), Map.of(Material.WHEAT.name(), 6)),
                        Map.of(target.getUniqueId(), Map.of("COW", 2)),
                        Map.of(target.getUniqueId(), Map.of(Material.COD.name(), 3)))
                .get(5, TimeUnit.SECONDS);
        repository.addItemStats(
                        Map.of(target.getUniqueId(), Map.of(Material.DIAMOND.name(), new ItemStatsDelta(7, 1))))
                .get(5, TimeUnit.SECONDS);
        repository.incrementProjectileCount(target.getUniqueId(), "ARROW").get(5, TimeUnit.SECONDS);

        plugin.getEconomyManager().deposit(target.getUniqueId(), 500.0D).get(5, TimeUnit.SECONDS);

        TransactionRepository transactionRepository = new TransactionRepository(plugin.getDatabaseManager());
        transactionRepository.insert(new AuditRecord(
                        target.getUniqueId(),
                        TransactionType.DEPOSIT,
                        200.0D,
                        200.0D,
                        "Deposit",
                        null))
                .get(5, TimeUnit.SECONDS);
        transactionRepository.insert(new AuditRecord(
                        target.getUniqueId(),
                        TransactionType.PAYMENT_SENT,
                        50.0D,
                        150.0D,
                        "Sent payment",
                        null))
                .get(5, TimeUnit.SECONDS);
        transactionRepository.insert(new AuditRecord(
                        target.getUniqueId(),
                        TransactionType.SHOP_BUY,
                        75.0D,
                        75.0D,
                        "Shop buy",
                        null))
                .get(5, TimeUnit.SECONDS);
    }

    private void fillFavoriteSlots(TNexus plugin, java.util.UUID viewerId) throws Exception {
        List<String> keys = List.of(
                "GENERAL_PLAY_TIME",
                "GENERAL_DISTANCE",
                "GENERAL_DEATHS",
                "GENERAL_RESPAWNS",
                "GENERAL_CHAT_COUNT",
                "GENERAL_SLEEP_COUNT",
                "GENERAL_PORTAL_COUNT",
                "GENERAL_FIRST_LOGIN",
                "ECONOMY_BALANCE",
                "ECONOMY_DEPOSIT_AMOUNT",
                "ECONOMY_WITHDRAW_AMOUNT",
                "ECONOMY_PAYMENT_RECEIVED_COUNT",
                "ECONOMY_PAYMENT_SENT_COUNT",
                "ECONOMY_TOTAL_VOLUME");
        List<Integer> slots = List.of(28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
        try (var connection = plugin.getDatabaseManager().getConnection()) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tnexus_stats_favorites (player_uuid, slot_position, stat_key) VALUES (?, ?, ?)")) {
                for (int index = 0; index < slots.size(); index++) {
                    statement.setString(1, viewerId.toString());
                    statement.setInt(2, slots.get(index));
                    statement.setString(3, keys.get(index));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }
}
