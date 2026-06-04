package network.tserver.tnexus;

import java.io.StringReader;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.database.DatabaseManager;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Assertions;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginManagerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

/**
 * Shared helpers for loading test plugin instances with mocked dependencies.
 */
public final class TestPluginSupport {

    private static final String TEST_PLUGIN_YAML = """
            name: TestTNexus
            version: 0.1.0
            main: %s
            api-version: '26.1.2'
            commands:
              tnexus:
                description: test command
                usage: "/tnexus"
            """;

    private TestPluginSupport() {
    }

    /**
     * Creates a new mocked server and registers required dependency plugins.
     *
     * @return mocked server
     */
    public static ServerMock mockServerWithRequiredPlugins() {
        ServerMock server = MockBukkit.mock();
        registerRequiredPlugins(server);
        return server;
    }

    /**
     * Registers all required dependency plugin mocks.
     *
     * @param server mocked server
     */
    public static void registerRequiredPlugins(ServerMock server) {
        registerPlugin(server, "Vault");
        registerPlugin(server, "LuckPerms");
        registerPlugin(server, "Multiverse-Core");
        registerPlugin(server, "FastAsyncWorldEdit");
    }

    /**
     * Registers a named dependency plugin mock.
     *
     * @param server mocked server
     * @param pluginName plugin name
     */
    public static void registerPlugin(ServerMock server, String pluginName) {
        PluginMock dependencyPlugin = PluginMock.builder()
                .withPluginName(pluginName)
                .withPluginVersion("1.0.0")
                .build();
        server.getPluginManager().registerLoadedPlugin(dependencyPlugin);
        server.getPluginManager().enablePlugin(dependencyPlugin);
    }

    /**
     * Loads and enables a TNexus-based test plugin class.
     *
     * @param server mocked server
     * @param pluginClass plugin class
     * @param <T> plugin type
     * @return enabled plugin instance
     */
    public static <T extends TNexus> T loadPlugin(ServerMock server, Class<T> pluginClass) {
        try {
            PluginDescriptionFile description = new PluginDescriptionFile(
                    new StringReader(TEST_PLUGIN_YAML.formatted(pluginClass.getName())));
            PluginManagerMock pluginManager = (PluginManagerMock) server.getPluginManager();
            T plugin = pluginClass.cast(pluginManager.loadPlugin(pluginClass, description, new Object[0]));
            pluginManager.enablePlugin(plugin);
            return plugin;
        } catch (Exception exception) {
            Assertions.fail("Failed to load test plugin " + pluginClass.getName(), exception);
            return null;
        }
    }

    /**
     * Test plugin variant that bypasses real database initialization.
     */
    public static class TestTNexus extends TNexus {

        @Override
        protected DatabaseManager createDatabaseManager() {
            return new ReadyDatabaseManager(this, getConfigManager());
        }
    }

    private static final class ReadyDatabaseManager extends DatabaseManager {

        private ReadyDatabaseManager(TNexus plugin, ConfigManager configManager) {
            super(plugin, configManager);
        }

        @Override
        public synchronized boolean initialize() {
            return true;
        }
    }
}
