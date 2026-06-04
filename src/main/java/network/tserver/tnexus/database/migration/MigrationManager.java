package network.tserver.tnexus.database.migration;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.util.ThrowingSupplier;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Applies versioned SQL migrations from the plugin resources.
 */
public final class MigrationManager {

    private static final Pattern MIGRATION_NAME_PATTERN = Pattern.compile("V(\\d+)__([A-Za-z0-9_]+)\\.sql");
    private static final String MIGRATION_DIRECTORY = "db/migrations";

    private final JavaPlugin plugin;
    private final ThrowingSupplier<Connection, SQLException> connectionSupplier;
    private final ConfigManager configManager;
    private final Logger logger;

    /**
     * Creates a new migration manager.
     *
     * @param plugin plugin instance
     * @param connectionSupplier connection supplier
     * @param configManager config manager
     */
    public MigrationManager(
            JavaPlugin plugin,
            ThrowingSupplier<Connection, SQLException> connectionSupplier,
            ConfigManager configManager) {
        this.plugin = plugin;
        this.connectionSupplier = connectionSupplier;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Applies every migration that has not yet been recorded in the schema history table.
     *
     * @throws SQLException when a database operation fails
     * @throws IOException when migration resources cannot be read
     */
    public void migrate() throws SQLException, IOException {
        try (Connection connection = this.connectionSupplier.get()) {
            createSchemaVersionTableIfNeeded(connection);
            Set<Integer> appliedVersions = loadAppliedVersions(connection);
            List<MigrationScript> scripts = discoverMigrationScripts();

            for (MigrationScript script : scripts) {
                if (appliedVersions.contains(script.version())) {
                    continue;
                }
                applyMigration(connection, script);
            }
        }
    }

    private void applyMigration(Connection connection, MigrationScript script) throws SQLException, IOException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            executeSqlStatements(connection, script.readSql(this.plugin));
            recordAppliedVersion(connection, script);
            connection.commit();
            this.logger.info("Applied database migration " + script.fileName());
        } catch (SQLException | IOException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void createSchemaVersionTableIfNeeded(Connection connection) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS %sschema_version (
                    version INT NOT NULL,
                    description VARCHAR(255) NOT NULL,
                    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (version)
                )
                """.formatted(getTablePrefix());
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Set<Integer> loadAppliedVersions(Connection connection) throws SQLException {
        Set<Integer> appliedVersions = new HashSet<>();
        String sql = "SELECT version FROM %sschema_version".formatted(getTablePrefix());
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                appliedVersions.add(resultSet.getInt("version"));
            }
        }
        return appliedVersions;
    }

    private void recordAppliedVersion(Connection connection, MigrationScript script) throws SQLException {
        String sql = "INSERT INTO %sschema_version (version, description) VALUES (?, ?)".formatted(getTablePrefix());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, script.version());
            statement.setString(2, script.description());
            statement.executeUpdate();
        }
    }

    private void executeSqlStatements(Connection connection, String sql) throws SQLException {
        for (String statementSql : splitStatements(sql)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(statementSql);
            }
        }
    }

    private List<String> splitStatements(String sql) {
        String normalized = sql.replace("${table_prefix}", getTablePrefix());
        String[] parts = normalized.split(";\\s*(?:\\R|$)");
        List<String> statements = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                statements.add(trimmed);
            }
        }
        return statements;
    }

    private List<MigrationScript> discoverMigrationScripts() throws IOException {
        TreeSet<MigrationScript> scripts = new TreeSet<>();
        Enumeration<URL> resources = this.plugin.getClass().getClassLoader().getResources(MIGRATION_DIRECTORY);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String protocol = resource.getProtocol();
            if ("file".equals(protocol)) {
                loadFileSystemScripts(resource, scripts);
            } else if ("jar".equals(protocol)) {
                loadJarScripts(resource, scripts);
            }
        }

        return new ArrayList<>(scripts);
    }

    private void loadFileSystemScripts(URL resource, Set<MigrationScript> scripts) throws IOException {
        try {
            Path directory = Path.of(resource.toURI());
            try (var paths = Files.list(directory)) {
                paths.filter(Files::isRegularFile)
                        .forEach(path -> addScript(path.getFileName().toString(), MIGRATION_DIRECTORY + "/" + path.getFileName(), scripts));
            }
        } catch (URISyntaxException exception) {
            throw new IOException("Failed to resolve migration directory URI.", exception);
        }
    }

    private void loadJarScripts(URL resource, Set<MigrationScript> scripts) throws IOException {
        JarURLConnection connection = (JarURLConnection) resource.openConnection();
        try (ZipFile zipFile = new ZipFile(Path.of(connection.getJarFileURL().toURI()).toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entry.isDirectory() || !entryName.startsWith(MIGRATION_DIRECTORY + "/")) {
                    continue;
                }
                String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                addScript(fileName, entryName, scripts);
            }
        } catch (URISyntaxException exception) {
            throw new IOException("Failed to resolve migration jar URI.", exception);
        }
    }

    private void addScript(String fileName, String resourcePath, Set<MigrationScript> scripts) {
        Matcher matcher = MIGRATION_NAME_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return;
        }

        int version = Integer.parseInt(matcher.group(1));
        String description = matcher.group(2).replace('_', ' ');
        scripts.add(new MigrationScript(version, description, fileName, resourcePath));
    }

    private String getTablePrefix() {
        return this.configManager.getDatabaseSettings().tablePrefix();
    }

    private record MigrationScript(int version, String description, String fileName, String resourcePath)
            implements Comparable<MigrationScript> {

        @Override
        public int compareTo(MigrationScript other) {
            return Integer.compare(this.version, other.version);
        }

        private String readSql(JavaPlugin plugin) throws IOException {
            try (InputStream inputStream = plugin.getResource(this.resourcePath)) {
                if (inputStream == null) {
                    throw new IOException("Missing migration resource: " + this.resourcePath);
                }
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
