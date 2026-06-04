package network.tserver.tnexus;

import java.util.logging.Logger;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.config.MessageConfig;
import network.tserver.tnexus.database.DatabaseManager;
import network.tserver.tnexus.gui.GuiManager;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin entry point for T-Nexus.
 */
public class TNexus extends JavaPlugin {

    private ConfigManager configManager;
    private MessageConfig messageConfig;
    private DatabaseManager databaseManager;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.messageConfig = new MessageConfig(this, this.configManager);
        this.databaseManager = new DatabaseManager(this, this.configManager);
        this.guiManager = new GuiManager(this);
        this.databaseManager.initialize();
        logMessage(this.messageConfig.getMessage("general.plugin-enabled"));
    }

    @Override
    public void onDisable() {
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        if (this.messageConfig != null) {
            logMessage(this.messageConfig.getMessage("general.plugin-disabled"));
        }
        this.databaseManager = null;
        this.messageConfig = null;
        this.configManager = null;
        this.guiManager = null;
    }

    /**
     * Returns the config manager instance.
     *
     * @return config manager
     */
    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    /**
     * Returns the message config instance.
     *
     * @return message config
     */
    public MessageConfig getMessageConfig() {
        return this.messageConfig;
    }

    /**
     * Returns the database manager instance.
     *
     * @return database manager
     */
    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    /**
     * Returns the GUI manager instance.
     *
     * @return GUI manager
     */
    public GuiManager getGuiManager() {
        return this.guiManager;
    }

    private void logMessage(String message) {
        Logger logger = getLogger();
        logger.info(ChatColor.stripColor(message));
    }
}
