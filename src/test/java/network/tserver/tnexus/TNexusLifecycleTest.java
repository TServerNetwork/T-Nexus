package network.tserver.tnexus;

import java.io.StringReader;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.database.DatabaseManager;
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginManagerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TNexusLifecycleTest {

    private static final String TEST_PLUGIN_YAML = """
            name: TestTNexus
            version: 0.1.0
            main: network.tserver.tnexus.TNexusLifecycleTest$LifecycleTNexus
            api-version: '26.1.2'
            commands:
              tnexus:
                description: test command
                usage: "/tnexus"
              shop:
                description: test shop command
                usage: "/shop link"
            """;

    private ServerMock server;

    @AfterEach
    void tearDown() {
        LifecycleTNexus.databaseInitializationShouldSucceed = true;
        MockBukkit.unmock();
    }

    @Test
    void shouldStartWhenAllRequiredPluginsArePresent() throws Exception {
        this.server = MockBukkit.mock();
        registerRequiredPlugins();

        LifecycleTNexus.databaseInitializationShouldSucceed = true;
        LifecycleTNexus plugin = loadPlugin();

        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getPluginHookManager());
        assertTrue(plugin.getPluginHookManager().isFullyReady());
    }

    @Test
    void shouldDisableItselfWhenARequiredPluginIsMissing() throws Exception {
        this.server = MockBukkit.mock();
        registerPlugin("Vault");
        registerPlugin("LuckPerms");
        registerPlugin("FastAsyncWorldEdit");

        LifecycleTNexus plugin = loadPlugin();

        assertFalse(plugin.isEnabled());
    }

    @Test
    void shouldDisableItselfWhenDatabaseInitializationFails() throws Exception {
        this.server = MockBukkit.mock();
        registerRequiredPlugins();

        LifecycleTNexus.databaseInitializationShouldSucceed = false;
        LifecycleTNexus plugin = loadPlugin();

        assertFalse(plugin.isEnabled());
    }

    @Test
    void shouldFlushPlayerSessionsBeforeDatabaseShutdown() throws Exception {
        this.server = MockBukkit.mock();
        registerRequiredPlugins();

        TrackingLifecycleTNexus plugin = loadTrackingPlugin();

        this.server.getPluginManager().disablePlugin(plugin);

        assertTrue(plugin.trackingPlayerStatsManager.flushCalled);
        assertTrue(plugin.trackingDatabaseManager.shutdownCalled);
        assertTrue(plugin.flushCompletedBeforeShutdown);
    }

    private LifecycleTNexus loadPlugin() throws Exception {
        PluginDescriptionFile description = new PluginDescriptionFile(new StringReader(TEST_PLUGIN_YAML));
        PluginManagerMock pluginManager = (PluginManagerMock) this.server.getPluginManager();
        LifecycleTNexus plugin = (LifecycleTNexus) pluginManager.loadPlugin(
                LifecycleTNexus.class,
                description,
                new Object[0]);
        pluginManager.enablePlugin(plugin);
        return plugin;
    }

    private TrackingLifecycleTNexus loadTrackingPlugin() throws Exception {
        PluginDescriptionFile description = new PluginDescriptionFile(new StringReader(TEST_PLUGIN_YAML));
        PluginManagerMock pluginManager = (PluginManagerMock) this.server.getPluginManager();
        TrackingLifecycleTNexus plugin = (TrackingLifecycleTNexus) pluginManager.loadPlugin(
                TrackingLifecycleTNexus.class,
                description,
                new Object[0]);
        pluginManager.enablePlugin(plugin);
        return plugin;
    }

    private void registerRequiredPlugins() {
        registerPlugin("Vault");
        registerPlugin("LuckPerms");
        registerPlugin("Multiverse-Core");
        registerPlugin("FastAsyncWorldEdit");
        TestPluginSupport.registerEconomyProvider(this.server);
        TestPluginSupport.registerLuckPermsProvider(this.server);
    }

    private void registerPlugin(String pluginName) {
        PluginMock dependencyPlugin = PluginMock.builder()
                .withPluginName(pluginName)
                .withPluginVersion("1.0.0")
                .build();
        this.server.getPluginManager().registerLoadedPlugin(dependencyPlugin);
        this.server.getPluginManager().enablePlugin(dependencyPlugin);
    }

    public static class LifecycleTNexus extends TNexus {

        private static boolean databaseInitializationShouldSucceed = true;

        @Override
        protected DatabaseManager createDatabaseManager() {
            return new TestDatabaseManager(
                    this,
                    getConfigManager(),
                    databaseInitializationShouldSucceed);
        }
    }

    public static class TrackingLifecycleTNexus extends TNexus {

        private TrackingDatabaseManager trackingDatabaseManager;
        private TrackingPlayerStatsManager trackingPlayerStatsManager;
        private boolean flushCompletedBeforeShutdown;

        @Override
        protected DatabaseManager createDatabaseManager() {
            this.trackingDatabaseManager = new TrackingDatabaseManager(this, getConfigManager());
            return this.trackingDatabaseManager;
        }

        @Override
        protected PlayerStatsManager createPlayerStatsManager() {
            this.trackingPlayerStatsManager = new TrackingPlayerStatsManager(
                    this,
                    new PlayerStatsRepository(getDatabaseManager()));
            return this.trackingPlayerStatsManager;
        }
    }

    private static final class TestDatabaseManager extends DatabaseManager {

        private final boolean initializeResult;

        private TestDatabaseManager(TNexus plugin, ConfigManager configManager, boolean initializeResult) {
            super(plugin, configManager);
            this.initializeResult = initializeResult;
        }

        @Override
        public synchronized boolean initialize() {
            return this.initializeResult;
        }
    }

    private static final class TrackingDatabaseManager extends DatabaseManager {

        private final TrackingLifecycleTNexus plugin;
        private boolean shutdownCalled;

        private TrackingDatabaseManager(TrackingLifecycleTNexus plugin, ConfigManager configManager) {
            super(plugin, configManager);
            this.plugin = plugin;
        }

        @Override
        public synchronized boolean initialize() {
            return true;
        }

        @Override
        public synchronized void shutdown() {
            this.shutdownCalled = true;
            this.plugin.flushCompletedBeforeShutdown = this.plugin.trackingPlayerStatsManager != null
                    && this.plugin.trackingPlayerStatsManager.flushCalled;
        }
    }

    private static final class TrackingPlayerStatsManager extends PlayerStatsManager {

        private boolean flushCalled;

        private TrackingPlayerStatsManager(TNexus plugin, PlayerStatsRepository repository) {
            super(plugin, repository);
        }

        @Override
        public CompletableFuture<Void> flushOnlineSessions(Collection<? extends Player> onlinePlayers) {
            this.flushCalled = true;
            return CompletableFuture.completedFuture(null);
        }
    }
}
