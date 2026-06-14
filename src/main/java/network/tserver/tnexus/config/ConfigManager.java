package network.tserver.tnexus.config;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import network.tserver.tnexus.gui.PagerTexture;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages plugin configuration access and lifecycle.
 */
public final class ConfigManager {

    private static final String DATABASE_PATH = "tnexus.database";
    private static final String GUI_PATH = "tnexus.gui";
    private static final String AFK_PATH = "tnexus.afk";
    private static final String RESOURCE_WORLD_PATH = "resource-world";

    private final JavaPlugin plugin;
    private FileConfiguration configuration;

    /**
     * Creates a new config manager and loads the plugin configuration.
     *
     * @param plugin plugin instance
     */
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Loads the configuration, creating the default file when needed.
     */
    public void load() {
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
        this.configuration = this.plugin.getConfig();
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reload() {
        load();
    }

    /**
     * Saves the current configuration to disk.
     *
     * @throws IllegalStateException when saving fails
     */
    public void save() {
        File file = new File(this.plugin.getDataFolder(), "config.yml");
        try {
            this.configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save config.yml", exception);
        }
    }

    /**
     * Returns the raw file configuration.
     *
     * @return file configuration
     */
    public FileConfiguration getConfiguration() {
        return this.configuration;
    }

    /**
     * Returns a string value.
     *
     * @param path config path
     * @return string value or null
     */
    public String getString(String path) {
        return this.configuration.getString(path);
    }

    /**
     * Returns a string value with fallback.
     *
     * @param path config path
     * @param defaultValue fallback value
     * @return string value
     */
    public String getString(String path, String defaultValue) {
        return this.configuration.getString(path, defaultValue);
    }

    /**
     * Returns an integer value.
     *
     * @param path config path
     * @return integer value
     */
    public int getInt(String path) {
        return this.configuration.getInt(path);
    }

    /**
     * Returns an integer value with fallback.
     *
     * @param path config path
     * @param defaultValue fallback value
     * @return integer value
     */
    public int getInt(String path, int defaultValue) {
        return this.configuration.getInt(path, defaultValue);
    }

    /**
     * Returns a double value.
     *
     * @param path config path
     * @return double value
     */
    public double getDouble(String path) {
        return this.configuration.getDouble(path);
    }

    /**
     * Returns a double value with fallback.
     *
     * @param path config path
     * @param defaultValue fallback value
     * @return double value
     */
    public double getDouble(String path, double defaultValue) {
        return this.configuration.getDouble(path, defaultValue);
    }

    /**
     * Returns a boolean value.
     *
     * @param path config path
     * @return boolean value
     */
    public boolean getBoolean(String path) {
        return this.configuration.getBoolean(path);
    }

    /**
     * Returns a boolean value with fallback.
     *
     * @param path config path
     * @param defaultValue fallback value
     * @return boolean value
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return this.configuration.getBoolean(path, defaultValue);
    }

    /**
     * Returns a configuration section.
     *
     * @param path config path
     * @return section or null
     */
    public ConfigurationSection getSection(String path) {
        return this.configuration.getConfigurationSection(path);
    }

    /**
     * Returns the database connection settings.
     *
     * @return database settings
     */
    public DatabaseSettings getDatabaseSettings() {
        ConfigurationSection section = getSection(DATABASE_PATH);
        if (section == null) {
            throw new IllegalStateException("Missing tnexus.database configuration section");
        }

        return new DatabaseSettings(
                section.getString("host", "localhost"),
                section.getInt("port", 3306),
                section.getString("name", "tnexus"),
                section.getString("username", "root"),
                section.getString("password", ""),
                section.getString("table-prefix", "tnexus_"),
                section.getInt("pool-size", 10),
                section.getString("jdbc-url"),
                section.getString("driver-class-name")
        );
    }

    /**
     * Returns the GUI framework settings.
     *
     * @return GUI settings
     */
    public GuiSettings getGuiSettings() {
        ConfigurationSection section = getSection(GUI_PATH);
        if (section == null) {
            throw new IllegalStateException("Missing tnexus.gui configuration section");
        }

        ConfigurationSection pagerSection = section.getConfigurationSection("pager-textures");
        if (pagerSection == null) {
            throw new IllegalStateException("Missing tnexus.gui.pager-textures configuration section");
        }

        return new GuiSettings(
                section.getString("main-menu-title", "&6&lT-Nexus Menu"),
                section.getInt("items-per-page", 45),
                section.getString("header-item", "BLACK_STAINED_GLASS_PANE"),
                section.getString("border-item", "GRAY_STAINED_GLASS_PANE"),
                section.getInt("click-cooldown-millis", 200),
                section.getInt("prev-page-slot", 45),
                section.getInt("back-button-slot", 48),
                section.getInt("close-button-slot", 49),
                section.getInt("current-location-slot", 50),
                section.getInt("next-page-slot", 53),
                new PagerSettings(
                        getPagerTexture(pagerSection, "previous"),
                        getPagerTexture(pagerSection, "next")
                )
        );
    }

    /**
     * Returns AFK detection settings.
     *
     * @return AFK settings
     */
    public AfkSettings getAfkSettings() {
        ConfigurationSection section = getSection(AFK_PATH);
        if (section == null) {
            throw new IllegalStateException("Missing tnexus.afk configuration section");
        }

        ConfigurationSection scoreSection = section.getConfigurationSection("score");
        if (scoreSection == null) {
            throw new IllegalStateException("Missing tnexus.afk.score configuration section");
        }

        return new AfkSettings(
                scoreSection.getInt("threshold", 100),
                scoreSection.getInt("max", 100),
                scoreSection.getInt("decay-per-second", 2),
                section.getInt("timeout-seconds", 300));
    }

    private PagerTexture getPagerTexture(ConfigurationSection section, String key) {
        ConfigurationSection pagerSection = section.getConfigurationSection(key);
        if (pagerSection == null) {
            throw new IllegalStateException("Missing tnexus.gui.pager-textures." + key + " configuration section");
        }

        return new PagerTexture(
                pagerSection.getString("enabled", ""),
                pagerSection.getString("disabled", "")
        );
    }

    /**
     * Returns the resource world manager settings.
     *
     * @return resource world settings
     */
    public ResourceWorldSettings getResourceWorldSettings() {
        ConfigurationSection section = getSection(RESOURCE_WORLD_PATH);
        if (section == null) {
            throw new IllegalStateException("Missing resource-world configuration section");
        }

        List<ResourceWorldDefinition> worlds = new ArrayList<>();
        List<Map<?, ?>> worldMaps = section.getMapList("worlds");
        for (Map<?, ?> worldMap : worldMaps) {
            String name = requireString(worldMap, "name");
            String dimensionName = requireString(worldMap, "dimension");
            String resetStartDate = requireString(worldMap, "reset-start-date");
            int resetIntervalDays = requireInt(worldMap, "reset-interval-days");
            worlds.add(new ResourceWorldDefinition(
                    name,
                    World.Environment.valueOf(dimensionName),
                    resetIntervalDays,
                    LocalDateTime.parse(resetStartDate)));
        }

        return new ResourceWorldSettings(
                section.getString("backup-path", "plugins/T-Nexus/backups"),
                section.getInt("backup-generations", 3),
                section.getString("fallback-world", "lobby"),
                section.getLong("seed-obfuscation-key", 1234567890L),
                section.getBoolean("show-real-seed-to-admin", false),
                List.copyOf(worlds));
    }

    private String requireString(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        throw new IllegalStateException("Missing resource-world.worlds." + key + " configuration value");
    }

    private int requireInt(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        throw new IllegalStateException("Missing resource-world.worlds." + key + " configuration value");
    }

    /**
     * Immutable database configuration values.
     *
     * @param host database host
     * @param port database port
     * @param name database name
     * @param username database username
     * @param password database password
     * @param tablePrefix table prefix
     * @param poolSize connection pool size
     * @param jdbcUrl optional JDBC URL override
     * @param driverClassName optional JDBC driver class name
     */
    public record DatabaseSettings(
            String host,
            int port,
            String name,
            String username,
            String password,
            String tablePrefix,
            int poolSize,
            String jdbcUrl,
            String driverClassName) {
    }

    /**
     * Immutable GUI configuration values.
     *
     * @param mainMenuTitle default main menu title
     * @param itemsPerPage configured maximum page size
     * @param headerItem header border material name
     * @param borderItem shared border material name
     * @param clickCooldownMillis click cooldown duration
     * @param prevPageSlot normalized previous-page slot
     * @param backButtonSlot normalized back-button slot
     * @param closeButtonSlot normalized close-button slot
     * @param currentLocationSlot normalized current-location slot
     * @param nextPageSlot normalized next-page slot
     * @param pagerSettings pager head texture settings
     */
    public record GuiSettings(
            String mainMenuTitle,
            int itemsPerPage,
            String headerItem,
            String borderItem,
            int clickCooldownMillis,
            int prevPageSlot,
            int backButtonSlot,
            int closeButtonSlot,
            int currentLocationSlot,
            int nextPageSlot,
            PagerSettings pagerSettings) {
    }

    /**
     * Immutable pager texture configuration values.
     *
     * @param previous previous-page head textures
     * @param next next-page head textures
     */
    public record PagerSettings(PagerTexture previous, PagerTexture next) {
    }

    /**
     * Immutable AFK detection configuration values.
     *
     * @param scoreThreshold activity score threshold required to refresh last-active time
     * @param scoreMax maximum score cap
     * @param decayPerSecond per-second score decay
     * @param timeoutSeconds AFK timeout from last active timestamp
     */
    public record AfkSettings(
            int scoreThreshold,
            int scoreMax,
            int decayPerSecond,
            int timeoutSeconds) {
    }

    /**
     * Immutable resource world configuration values.
     *
     * @param backupPath backup directory path
     * @param backupGenerations number of backups to retain
     * @param fallbackWorld fallback world name
     * @param seedObfuscationKey seed obfuscation key
     * @param showRealSeedToAdmin whether admins should see the real seed
     * @param worlds configured resource worlds
     */
    public record ResourceWorldSettings(
            String backupPath,
            int backupGenerations,
            String fallbackWorld,
            long seedObfuscationKey,
            boolean showRealSeedToAdmin,
            List<ResourceWorldDefinition> worlds) {
    }

    /**
     * Immutable resource world definition.
     *
     * @param name world name
     * @param dimension world environment
     * @param resetIntervalDays reset interval in days
     * @param resetStartDate reset schedule anchor
     */
    public record ResourceWorldDefinition(
            String name,
            World.Environment dimension,
            int resetIntervalDays,
            LocalDateTime resetStartDate) {
    }
}
