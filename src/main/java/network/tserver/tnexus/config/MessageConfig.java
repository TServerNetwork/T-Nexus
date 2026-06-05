package network.tserver.tnexus.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages i18n message loading and delivery.
 */
public final class MessageConfig {

    private static final String DEFAULT_LOCALE = "ja_JP";
    private static final String PREFIX_KEY = "prefix";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private YamlConfiguration messages;
    private String locale;

    /**
     * Creates a new message config and loads the active locale.
     *
     * @param plugin plugin instance
     * @param configManager config manager
     */
    public MessageConfig(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        reload();
    }

    /**
     * Reloads the active locale file based on config.yml.
     */
    public void reload() {
        this.locale = this.configManager.getString("tnexus.locale", DEFAULT_LOCALE);
        String resourcePath = "lang/" + this.locale + ".yml";
        if (this.plugin.getResource(resourcePath) == null) {
            this.locale = DEFAULT_LOCALE;
            resourcePath = "lang/" + DEFAULT_LOCALE + ".yml";
        }

        saveResourceIfMissing(resourcePath);
        File file = new File(this.plugin.getDataFolder(), resourcePath);
        YamlConfiguration loadedMessages = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaultMessages = loadDefaultMessages(resourcePath);
        if (defaultMessages != null) {
            loadedMessages.setDefaults(defaultMessages);
            loadedMessages.options().copyDefaults(true);
        }
        this.messages = loadedMessages;
    }

    /**
     * Returns the active locale identifier.
     *
     * @return active locale
     */
    public String getLocale() {
        return this.locale;
    }

    /**
     * Resolves a translated message, replacing indexed placeholders.
     *
     * @param key message key
     * @param placeholders placeholder values
     * @return translated message or the key when missing
     */
    public String getMessage(String key, Object... placeholders) {
        String value = this.messages.getString(key);
        if (value == null) {
            return key;
        }

        String formatted = value;
        for (int index = 0; index < placeholders.length; index++) {
            formatted = formatted.replace("{" + index + "}", String.valueOf(placeholders[index]));
        }
        return ChatColor.translateAlternateColorCodes('&', formatted);
    }

    /**
     * Sends a prefixed translated message to a player.
     *
     * @param player target player
     * @param key message key
     * @param placeholders placeholder values
     */
    public void sendMessage(Player player, String key, Object... placeholders) {
        player.sendMessage(getPrefix() + getMessage(key, placeholders));
    }

    /**
     * Sends a prefixed translated message to any command sender.
     *
     * @param sender target sender
     * @param key message key
     * @param placeholders placeholder values
     */
    public void sendMessage(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(getPrefix() + getMessage(key, placeholders));
    }

    private String getPrefix() {
        return getMessage(PREFIX_KEY);
    }

    private void saveResourceIfMissing(String resourcePath) {
        File file = new File(this.plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            this.plugin.saveResource(resourcePath, false);
        }
    }

    private YamlConfiguration loadDefaultMessages(String resourcePath) {
        InputStream inputStream = this.plugin.getResource(resourcePath);
        if (inputStream == null) {
            return null;
        }
        try (InputStream stream = inputStream;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load bundled locale resource: " + resourcePath, exception);
        }
    }
}
