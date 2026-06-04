package network.tserver.tnexus;

import java.io.StringReader;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.database.DatabaseManager;
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

    private void registerRequiredPlugins() {
        registerPlugin("Vault");
        registerPlugin("LuckPerms");
        registerPlugin("Multiverse-Core");
        registerPlugin("FastAsyncWorldEdit");
        TestPluginSupport.registerEconomyProvider(this.server);
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
}
