package network.tserver.tnexus.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    private ServerMock server;
    private DatabaseManager databaseManager;

    @AfterEach
    void tearDown() {
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        MockBukkit.unmock();
    }

    @Test
    void shouldInitializePoolAndApplyMigrations() throws SQLException {
        TNexus plugin = loadPlugin();
        this.databaseManager = createH2DatabaseManager(plugin, "database_init");

        assertTrue(this.databaseManager.initialize());
        assertTrue(this.databaseManager.isInitialized());

        try (var connection = this.databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT description FROM tnexus_schema_version WHERE version = ?")) {
            statement.setInt(1, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("initial setup", resultSet.getString("description"));
            }
        }

        assertMigrationRecorded(2, "economy schema");
        assertMigrationRecorded(3, "pay queue");
        assertMigrationRecorded(4, "sign shop schema");
        assertMigrationRecorded(5, "shop chest links");
        assertMigrationRecorded(6, "player stats");
        assertMigrationRecorded(7, "block stats");
        assertMigrationRecorded(8, "entity damage stats");
        assertMigrationRecorded(9, "death stats");
        assertMigrationRecorded(10, "distance stats");
        assertMigrationRecorded(11, "craft stats");
        assertMigrationRecorded(12, "processing stats");
        assertMigrationRecorded(13, "farming stats");
        assertMigrationRecorded(14, "item stats");
        assertTableExists("tnexus_transactions");
        assertTableExists("tnexus_shops");
        assertTableExists("tnexus_shop_chests");
        assertTableExists("tnexus_pay_queue");
        assertTableExists("tnexus_player_stats");
        assertTableExists("tnexus_block_stats");
        assertTableExists("tnexus_entity_damage_stats");
        assertTableExists("tnexus_death_stats");
        assertTableExists("tnexus_distance_stats");
        assertTableExists("tnexus_craft_stats");
        assertTableExists("tnexus_smelt_stats");
        assertTableExists("tnexus_enchant_stats");
        assertTableExists("tnexus_enchant_item_stats");
        assertTableExists("tnexus_harvest_stats");
        assertTableExists("tnexus_breed_stats");
        assertTableExists("tnexus_fish_stats");
        assertTableExists("tnexus_item_stats");
    }

    @Test
    void shouldRunQueriesOffTheMainThread()
            throws InterruptedException, ExecutionException, TimeoutException {
        TNexus plugin = loadPlugin();
        this.databaseManager = createH2DatabaseManager(plugin, "database_async");
        assertTrue(this.databaseManager.initialize());

        Boolean ranOnPrimaryThread = this.databaseManager.queryAsync(Bukkit::isPrimaryThread)
                .get(5, TimeUnit.SECONDS);
        Integer migrationCount = this.databaseManager.queryAsync(() -> {
            try (var connection = this.databaseManager.getConnection();
                 var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("SELECT COUNT(*) FROM tnexus_schema_version")) {
                resultSet.next();
                return resultSet.getInt(1);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }).get(5, TimeUnit.SECONDS);

        assertFalse(ranOnPrimaryThread);
        assertEquals(14, migrationCount);
    }

    @Test
    void shouldSkipAlreadyAppliedMigrations() throws SQLException {
        TNexus plugin = loadPlugin();
        this.databaseManager = createH2DatabaseManager(plugin, "database_reinitialize");
        assertTrue(this.databaseManager.initialize());
        this.databaseManager.shutdown();

        this.databaseManager = createH2DatabaseManager(plugin, "database_reinitialize");
        assertTrue(this.databaseManager.initialize());

        try (var connection = this.databaseManager.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM tnexus_schema_version")) {
            resultSet.next();
            assertEquals(14, resultSet.getInt(1));
        }
    }

    @Test
    void shouldShutdownPoolSafely() throws SQLException {
        TNexus plugin = loadPlugin();
        this.databaseManager = createH2DatabaseManager(plugin, "database_shutdown");
        assertTrue(this.databaseManager.initialize());

        this.databaseManager.shutdown();
        this.databaseManager.shutdown();

        assertFalse(this.databaseManager.isInitialized());
        assertThrows(SQLException.class, () -> this.databaseManager.getConnection());
    }

    @Test
    void shouldReturnFalseWhenConnectionInitializationFails() {
        TNexus plugin = loadPlugin();
        FileConfiguration configuration = plugin.getConfigManager().getConfiguration();
        configuration.set("tnexus.database.jdbc-url", "jdbc:invalid://missing");
        configuration.set("tnexus.database.driver-class-name", "org.h2.Driver");

        this.databaseManager = new DatabaseManager(plugin, plugin.getConfigManager());

        assertFalse(this.databaseManager.initialize());
        assertFalse(this.databaseManager.isInitialized());
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
    }

    private DatabaseManager createH2DatabaseManager(TNexus plugin, String databaseName) {
        FileConfiguration configuration = plugin.getConfigManager().getConfiguration();
        configuration.set("tnexus.database.jdbc-url",
                "jdbc:h2:mem:%s;MODE=MySQL;DB_CLOSE_DELAY=-1".formatted(databaseName));
        configuration.set("tnexus.database.driver-class-name", "org.h2.Driver");
        configuration.set("tnexus.database.username", "sa");
        configuration.set("tnexus.database.password", "");
        configuration.set("tnexus.database.table-prefix", "tnexus_");
        configuration.set("tnexus.database.pool-size", 4);
        return new DatabaseManager(plugin, plugin.getConfigManager());
    }

    private void assertMigrationRecorded(int version, String description) throws SQLException {
        try (var connection = this.databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT description FROM tnexus_schema_version WHERE version = ?")) {
            statement.setInt(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(description, resultSet.getString("description"));
            }
        }
    }

    private void assertTableExists(String tableName) throws SQLException {
        try (var connection = this.databaseManager.getConnection();
             var resultSet = connection.getMetaData().getTables(null, null, tableName.toUpperCase(), null)) {
            assertTrue(resultSet.next());
        }
    }
}
