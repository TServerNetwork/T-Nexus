package network.tserver.tnexus.manager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.ResourceWorldResetRepository;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceWorldManagerTest {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldLoadConfiguredWorldsAndPersistNextResetTimes() throws Exception {
        TNexus plugin = loadPlugin();
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        ResourceWorldManager manager = new ResourceWorldManager(
                plugin,
                repository,
                createTrackingWorldManager(new TrackingWorldManagerState()),
                Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), TOKYO),
                new TrackingFileManager(plugin),
                new TrackingEditService(),
                () -> 100L);

        manager.onEnable().get(5, TimeUnit.SECONDS);

        assertEquals(3, manager.getSettings().worlds().size());
        assertTrue(manager.getWorldDefinition("resource").isPresent());
        assertTrue(manager.getWorldDefinition("resource_nether").isPresent());
        assertTrue(manager.getWorldDefinition("resource_end").isPresent());

        LocalDateTime expected = manager.calculateNextResetTime(
                manager.getWorldDefinition("resource").orElseThrow(),
                LocalDateTime.ofInstant(Instant.parse("2026-06-14T00:00:00Z"), TOKYO));
        assertEquals(expected, manager.getNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow());
    }

    @Test
    void shouldTrackResettingStateAndDelegateWorldOperations() {
        TNexus plugin = loadPlugin();
        TrackingWorldManagerState state = new TrackingWorldManagerState();
        ResourceWorldManager manager = new ResourceWorldManager(
                plugin,
                new ResourceWorldResetRepository(plugin.getDatabaseManager()),
                createTrackingWorldManager(state),
                Clock.systemDefaultZone(),
                new TrackingFileManager(plugin),
                new TrackingEditService(),
                () -> 100L);

        assertFalse(manager.isResetting("resource"));
        manager.markResetting("resource");
        assertTrue(manager.isResetting("resource"));
        manager.clearResetting("resource");
        assertFalse(manager.isResetting("resource"));

        assertTrue(manager.unloadWorld("resource"));
        assertTrue(manager.regenerateWorld("resource", "12345"));
        assertTrue(manager.loadWorld("resource"));

        assertEquals("resource", state.unloadWorldCall.get());
        assertEquals("resource", state.loadWorldCall.get());
        assertEquals("resource|12345", state.regenWorldCall.get());
    }

    @Test
    void shouldExecuteResetFlowAndPasteSchematicWhenPresent() throws Exception {
        TNexus plugin = loadPlugin();
        configureSingleWorld(plugin);
        this.server.addSimpleWorld("lobby");
        World resourceWorld = this.server.addSimpleWorld("resource");
        PlayerMock player = this.server.addPlayer();
        player.teleport(resourceWorld.getSpawnLocation());

        TrackingWorldManagerState worldState = new TrackingWorldManagerState();
        TrackingFileManager fileManager = new TrackingFileManager(plugin);
        File expectedWorldFolder = new File(plugin.getDataFolder(), "test-worlds/resource");
        fileManager.loadedWorldFolder = expectedWorldFolder;
        Path schematicPath = plugin.getDataFolder().toPath().resolve("schematics").resolve("resource").resolve("spawn.schem");
        Files.createDirectories(schematicPath.getParent());
        Files.writeString(schematicPath, "test");
        fileManager.schematicPath = schematicPath;

        TrackingEditService editService = new TrackingEditService();
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        ResourceWorldManager manager = new ResourceWorldManager(
                plugin,
                repository,
                createTrackingWorldManager(worldState),
                Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), TOKYO),
                fileManager,
                editService,
                () -> 777L);

        manager.onEnable().get(5, TimeUnit.SECONDS);
        LocalDateTime currentReset = manager.getNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow();
        CompletableFuture<LocalDateTime> resetFuture = manager.executeReset("resource");
        waitFor(resetFuture);
        LocalDateTime nextReset = resetFuture.get(5, TimeUnit.SECONDS);

        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 0, 10), nextReset);
        assertEquals(nextReset, repository.findNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow());
        assertTrue(fileManager.backupCalled.get());
        assertEquals(expectedWorldFolder, fileManager.backupWorldFolder.get());
        assertTrue(editService.flattenCalled.get());
        assertTrue(editService.pasteCalled.get());
        assertEquals("resource", editService.flattenWorldName.get());
        assertEquals("resource", editService.pasteWorldName.get());
        assertEquals("resource|123456", worldState.regenWorldCall.get());
        assertSame(this.server.getWorld("lobby"), player.getWorld());
        assertFalse(manager.isResetting("resource"));

        String startMessage = player.nextMessage();
        String teleportMessage = player.nextMessage();
        String completeMessage = player.nextMessage();
        assertNotNull(startMessage);
        assertNotNull(teleportMessage);
        assertNotNull(completeMessage);
        assertTrue(startMessage.contains("resource"));
        assertTrue(teleportMessage.contains("テレポート"));
        assertTrue(completeMessage.contains("resource"));
        ResourceWorldResetRepository.ResourceWorldResetEntry completedEntry = repository
                .findByWorldNameAndNextResetAt("resource", currentReset)
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(ResourceWorldResetRepository.ResetStatus.COMPLETED, completedEntry.status());
        assertEquals(123456L, completedEntry.seed());

        ResourceWorldResetRepository.ResourceWorldResetEntry scheduledEntry = repository
                .findByWorldNameAndNextResetAt("resource", nextReset)
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(ResourceWorldResetRepository.ResetStatus.SCHEDULED, scheduledEntry.status());
        assertNull(scheduledEntry.seed());
    }

    @Test
    void shouldRestoreBackupAndNotifyAdminsWhenResetFails() throws Exception {
        TNexus plugin = loadPlugin();
        configureSingleWorld(plugin);
        this.server.addSimpleWorld("lobby");
        this.server.addSimpleWorld("resource");
        PlayerMock admin = this.server.addPlayer();
        admin.addAttachment(plugin, "tnexus.admin", true);

        TrackingWorldManagerState worldState = new TrackingWorldManagerState();
        worldState.regenShouldSucceed = false;
        TrackingFileManager fileManager = new TrackingFileManager(plugin);
        TrackingEditService editService = new TrackingEditService();
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        ResourceWorldManager manager = new ResourceWorldManager(
                plugin,
                repository,
                createTrackingWorldManager(worldState),
                Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), TOKYO),
                fileManager,
                editService,
                () -> 999L);

        manager.onEnable().get(5, TimeUnit.SECONDS);
        LocalDateTime currentReset = manager.getNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow();
        CompletableFuture<LocalDateTime> resetFuture = manager.executeReset("resource");
        waitFor(resetFuture);

        assertThrows(Exception.class, () -> resetFuture.get(5, TimeUnit.SECONDS));
        assertTrue(fileManager.restoreCalled.get());
        ResourceWorldResetRepository.ResourceWorldResetEntry failedEntry = repository
                .findByWorldNameAndNextResetAt("resource", currentReset)
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(ResourceWorldResetRepository.ResetStatus.FAILED, failedEntry.status());
        assertTrue(failedEntry.errorMessage().contains("Failed to regenerate world resource"));

        String startMessage = admin.nextMessage();
        String adminMessage = admin.nextMessage();
        String failedBroadcast = admin.nextMessage();
        assertNotNull(startMessage);
        assertNotNull(adminMessage);
        assertNotNull(failedBroadcast);
        assertTrue(adminMessage.contains("resource"));
        assertTrue(adminMessage.contains("Failed to regenerate world resource"));
        assertTrue(failedBroadcast.contains("resource"));
    }

    @Test
    void shouldPersistFailedStatusSynchronouslyOnDisable() throws Exception {
        TNexus plugin = loadPlugin();
        configureSingleWorld(plugin);
        this.server.addSimpleWorld("lobby");
        this.server.addSimpleWorld("resource");

        CountDownLatch backupStarted = new CountDownLatch(1);
        CountDownLatch continueBackup = new CountDownLatch(1);
        BlockingFileManager fileManager = new BlockingFileManager(plugin, backupStarted, continueBackup);
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        ResourceWorldManager manager = new ResourceWorldManager(
                plugin,
                repository,
                createTrackingWorldManager(new TrackingWorldManagerState()),
                Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), TOKYO),
                fileManager,
                new TrackingEditService(),
                () -> 321L);

        manager.onEnable().get(5, TimeUnit.SECONDS);
        LocalDateTime currentReset = manager.getNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow();
        CompletableFuture<LocalDateTime> resetFuture = manager.executeReset("resource");

        while (!backupStarted.await(50, TimeUnit.MILLISECONDS)) {
            this.server.getScheduler().performOneTick();
        }

        manager.onDisable();
        ResourceWorldResetRepository.ResourceWorldResetEntry failedEntry = repository
                .findByWorldNameAndNextResetAt("resource", currentReset)
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(ResourceWorldResetRepository.ResetStatus.FAILED, failedEntry.status());
        assertEquals("Plugin disabled during reset", failedEntry.errorMessage());

        continueBackup.countDown();
        waitFor(resetFuture);
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2DatabaseOnlyTNexus.class);
    }

    private void configureSingleWorld(TNexus plugin) {
        plugin.getConfigManager().getConfiguration().set("resource-world.worlds", List.of(Map.of(
                "name", "resource",
                "dimension", "NORMAL",
                "reset-interval-days", 1,
                "reset-start-date", "2026-06-14T09:00:10")));
    }

    private void waitFor(CompletableFuture<?> future) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!future.isDone() && System.currentTimeMillis() < deadline) {
            this.server.getScheduler().performOneTick();
            Thread.sleep(10L);
        }
        if (!future.isDone()) {
            throw new AssertionError("Future did not complete before timeout");
        }
    }

    private MultiverseWorldService createTrackingWorldManager(TrackingWorldManagerState state) {
        return new MultiverseWorldService() {
            @Override
            public boolean unloadWorld(String worldName) {
                state.unloadWorldCall.set(worldName);
                return true;
            }

            @Override
            public boolean regenerateWorld(String worldName, String seed) {
                state.regenWorldCall.set(worldName + "|" + seed);
                return state.regenShouldSucceed;
            }

            @Override
            public boolean loadWorld(String worldName) {
                state.loadWorldCall.set(worldName);
                if (server.getWorld(worldName) == null) {
                    server.addSimpleWorld(worldName);
                }
                return true;
            }
        };
    }

    private static final class TrackingWorldManagerState {
        private final AtomicReference<String> unloadWorldCall = new AtomicReference<>();
        private final AtomicReference<String> loadWorldCall = new AtomicReference<>();
        private final AtomicReference<String> regenWorldCall = new AtomicReference<>();
        private boolean regenShouldSucceed = true;
    }

    private static class TrackingFileManager extends ResourceWorldFileManager {
        protected final AtomicBoolean backupCalled = new AtomicBoolean();
        protected final AtomicBoolean restoreCalled = new AtomicBoolean();
        protected final AtomicReference<File> backupWorldFolder = new AtomicReference<>();
        protected File loadedWorldFolder;
        private Path schematicPath;

        private TrackingFileManager(TNexus plugin) {
            super(plugin);
        }

        @Override
        public void backupWorld(File worldFolder) {
            this.backupCalled.set(true);
            this.backupWorldFolder.set(worldFolder);
        }

        @Override
        File getLoadedWorldFolder(String worldName) {
            return this.loadedWorldFolder == null ? new File(worldName) : this.loadedWorldFolder;
        }

        @Override
        public void restoreLatestBackup(String worldName) {
            this.restoreCalled.set(true);
        }

        @Override
        public boolean hasLatestBackup(String worldName) {
            return true;
        }

        @Override
        public long randomizeStructureSeeds(String worldName) {
            return 123456L;
        }

        @Override
        public Path getSpawnSchematicPath(String worldName) {
            return this.schematicPath == null ? Path.of("missing.schem") : this.schematicPath;
        }
    }

    private static final class BlockingFileManager extends TrackingFileManager {
        private final CountDownLatch backupStarted;
        private final CountDownLatch continueBackup;

        private BlockingFileManager(TNexus plugin, CountDownLatch backupStarted, CountDownLatch continueBackup) {
            super(plugin);
            this.backupStarted = backupStarted;
            this.continueBackup = continueBackup;
        }

        @Override
        public void backupWorld(File worldFolder) {
            this.backupCalled.set(true);
            this.backupWorldFolder.set(worldFolder);
            this.backupStarted.countDown();
            try {
                this.continueBackup.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class TrackingEditService implements ResourceWorldEditService {
        private final AtomicBoolean flattenCalled = new AtomicBoolean();
        private final AtomicBoolean pasteCalled = new AtomicBoolean();
        private final AtomicReference<String> flattenWorldName = new AtomicReference<>();
        private final AtomicReference<String> pasteWorldName = new AtomicReference<>();

        @Override
        public void flattenArea(World world, int radius, int surfaceY) {
            this.flattenCalled.set(true);
            this.flattenWorldName.set(world.getName());
            assertEquals(32, radius);
        }

        @Override
        public void pasteSchematic(World world, Path schematicPath, int x, int y, int z) {
            this.pasteCalled.set(true);
            this.pasteWorldName.set(world.getName());
            assertTrue(Files.exists(schematicPath));
            assertEquals(0, x);
            assertEquals(0, z);
        }
    }
}
