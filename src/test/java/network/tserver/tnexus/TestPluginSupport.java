package network.tserver.tnexus;

import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.ServicePriority;
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
              balance:
                description: test balance command
                usage: "/balance"
              pay:
                description: test pay command
                usage: "/pay <player>"
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
        registerEconomyProvider(server);
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
     * Registers a basic Vault economy provider for tests.
     *
     * @param server mocked server
     */
    public static void registerEconomyProvider(ServerMock server) {
        PluginMock providerPlugin = PluginMock.builder()
                .withPluginName("EssentialsX")
                .withPluginVersion("1.0.0")
                .build();
        server.getPluginManager().registerLoadedPlugin(providerPlugin);
        server.getPluginManager().enablePlugin(providerPlugin);
        server.getServicesManager().register(
                Economy.class,
                createEconomyProxy(),
                providerPlugin,
                ServicePriority.Normal);
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

    /**
     * Test plugin variant backed by an in-memory H2 database.
     */
    public static class H2TestTNexus extends TNexus {

        @Override
        protected DatabaseManager createDatabaseManager() {
            String databaseName = "tnexus_test_" + System.nanoTime();
            getConfigManager().getConfiguration().set(
                    "tnexus.database.jdbc-url",
                    "jdbc:h2:mem:%s;MODE=MySQL;DB_CLOSE_DELAY=-1".formatted(databaseName));
            getConfigManager().getConfiguration().set("tnexus.database.driver-class-name", "org.h2.Driver");
            getConfigManager().getConfiguration().set("tnexus.database.username", "sa");
            getConfigManager().getConfiguration().set("tnexus.database.password", "");
            getConfigManager().getConfiguration().set("tnexus.database.table-prefix", "tnexus_");
            getConfigManager().getConfiguration().set("tnexus.database.pool-size", 4);
            return new DatabaseManager(this, getConfigManager());
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

    private static Economy createEconomyProxy() {
        Map<UUID, Double> balances = new ConcurrentHashMap<>();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getBalance" -> balances.getOrDefault(getPlayerId(args), 0.0D);
            case "has" -> balances.getOrDefault(getPlayerId(args), 0.0D) >= getAmount(args);
            case "depositPlayer" -> applyDelta(balances, getPlayerId(args), getAmount(args));
            case "withdrawPlayer" -> withdraw(balances, getPlayerId(args), getAmount(args));
            case "currencyNamePlural" -> "Coins";
            case "currencyNameSingular" -> "Coin";
            case "isEnabled" -> true;
            case "hasBankSupport" -> false;
            case "fractionalDigits" -> 2;
            case "format" -> String.format("%.2f", ((Number) args[0]).doubleValue());
            case "getName" -> "MockEconomy";
            case "hasAccount", "createPlayerAccount" -> true;
            default -> defaultValue(method.getReturnType());
        };
        return (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(),
                new Class<?>[]{Economy.class},
                handler);
    }

    private static UUID getPlayerId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof OfflinePlayer player) {
                return player.getUniqueId();
            }
        }
        throw new IllegalArgumentException("OfflinePlayer argument is required");
    }

    private static double getAmount(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Number number) {
                return number.doubleValue();
            }
        }
        throw new IllegalArgumentException("Numeric amount argument is required");
    }

    private static EconomyResponse applyDelta(Map<UUID, Double> balances, UUID playerId, double amount) {
        double newBalance = balances.getOrDefault(playerId, 0.0D) + amount;
        balances.put(playerId, newBalance);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private static EconomyResponse withdraw(Map<UUID, Double> balances, UUID playerId, double amount) {
        double currentBalance = balances.getOrDefault(playerId, 0.0D);
        if (currentBalance < amount) {
            return new EconomyResponse(amount, currentBalance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }

        double newBalance = currentBalance - amount;
        balances.put(playerId, newBalance);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private static Object defaultValue(Class<?> returnType) {
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
