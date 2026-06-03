package network.tserver.tnexus.config;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages plugin configuration access and lifecycle.
 */
public final class ConfigManager {

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
}
