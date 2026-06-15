package network.tserver.tnexus.manager;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.ResourceWorldResetRepository;
import network.tserver.tnexus.manager.MultiverseWorldService;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetSchedulerTest {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldScheduleOnlyFutureCountdownPoints() throws Exception {
        TNexus plugin = loadPlugin();
        configureSingleWorld(plugin, "2026-06-14T09:00:12");
        Clock clock = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), TOKYO);
        ResourceWorldManager manager = createManager(plugin, clock);
        manager.onEnable().get(5, TimeUnit.SECONDS);

        ResetScheduler scheduler = new ResetScheduler(plugin, manager, clock);
        scheduler.scheduleAll().get(5, TimeUnit.SECONDS);

        PlayerMock player = (PlayerMock) this.server.getPlayerExact("Player0");
        this.server.getScheduler().performTicks(239);

        int countdownMessages = drainMessages(player);
        assertEquals(10, countdownMessages);
        assertTrue(scheduler.getScheduledTaskCount("resource") >= 1);
    }

    @Test
    void shouldRescheduleNextResetAfterExecutionCompletes() throws Exception {
        TNexus plugin = loadPlugin();
        configureSingleWorld(plugin, "2026-06-14T09:00:01");
        Clock clock = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), TOKYO);
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        ResourceWorldManager manager = createManager(plugin, repository, clock);
        manager.onEnable().get(5, TimeUnit.SECONDS);

        ResetScheduler scheduler = new ResetScheduler(plugin, manager, clock);
        scheduler.scheduleAll().get(5, TimeUnit.SECONDS);
        this.server.getScheduler().performTicks(25);
        waitFor(() -> {
            this.server.getScheduler().performOneTick();
            return scheduler.getScheduledTaskCount("resource") > 1;
        });

        LocalDateTime nextReset = repository.findNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow();
        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 0, 1), nextReset);
        assertTrue(scheduler.getScheduledTaskCount("resource") > 1);
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        this.server.addSimpleWorld("lobby");
        this.server.addSimpleWorld("resource");
        this.server.addPlayer();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2DatabaseOnlyTNexus.class);
    }

    private void configureSingleWorld(TNexus plugin, String resetStartDate) {
        plugin.getConfigManager().getConfiguration().set("resource-world.worlds", List.of(Map.of(
                "name", "resource",
                "dimension", "NORMAL",
                "reset-interval-days", 1,
                "reset-start-date", resetStartDate)));
    }

    private ResourceWorldManager createManager(TNexus plugin, Clock clock) {
        return createManager(plugin, new ResourceWorldResetRepository(plugin.getDatabaseManager()), clock);
    }

    private ResourceWorldManager createManager(
            TNexus plugin,
            ResourceWorldResetRepository repository,
            Clock clock) {
        return new ResourceWorldManager(
                plugin,
                repository,
                createWorldManagerProxy(),
                clock,
                new NoOpFileManager(plugin),
                new NoOpEditService(),
                () -> 555L);
    }

    private int drainMessages(PlayerMock player) {
        int messages = 0;
        while (player.nextMessage() != null) {
            messages++;
        }
        return messages;
    }

    private void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Condition was not met before timeout");
    }

    private MultiverseWorldService createWorldManagerProxy() {
        return new MultiverseWorldService() {
            @Override
            public boolean unloadWorld(String worldName) {
                return true;
            }

            @Override
            public boolean removeWorld(String worldName) {
                return true;
            }

            @Override
            public boolean importWorld(String worldName, World.Environment environment) {
                return true;
            }

            @Override
            public boolean regenerateWorld(String worldName, String seed) {
                return true;
            }

            @Override
            public boolean loadWorld(String worldName) {
                return true;
            }
        };
    }

    private static final class NoOpFileManager extends ResourceWorldFileManager {
        private NoOpFileManager(TNexus plugin) {
            super(plugin);
        }

        @Override
        public void backupWorld(File worldFolder) {
        }

        @Override
        File getLoadedWorldFolder(String worldName) {
            return new File(worldName);
        }

        @Override
        public void restoreLatestBackup(String worldName) {
        }

        @Override
        public void deleteWorldFolder(File worldFolder) {
        }

        @Override
        public boolean hasLatestBackup(String worldName) {
            return true;
        }

        @Override
        public long randomizeStructureSeeds(String worldName) {
            return 111L;
        }

        @Override
        public Path getSpawnSchematicPath(String worldName) {
            return Path.of("missing.schem");
        }
    }

    private static final class NoOpEditService implements ResourceWorldEditService {
        @Override
        public void prepareSpawnArea(World world, Path schematicPath, int fallbackRadius, int surfaceY) {
        }

        @Override
        public void pasteSchematic(World world, Path schematicPath, int x, int y, int z) {
        }
    }
}
