package network.tserver.tnexus;

import java.util.logging.Logger;
import network.tserver.tnexus.command.CommandManager;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.config.MessageConfig;
import network.tserver.tnexus.database.DatabaseManager;
import network.tserver.tnexus.gui.GuiManager;
import network.tserver.tnexus.manager.PluginHookManager;
import network.tserver.tnexus.manager.hook.FaweHook;
import network.tserver.tnexus.manager.hook.LuckPermsHook;
import network.tserver.tnexus.manager.hook.MultiverseHook;
import network.tserver.tnexus.manager.hook.VaultHook;
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
    private CommandManager commandManager;
    private PluginHookManager pluginHookManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.messageConfig = new MessageConfig(this, this.configManager);
        this.pluginHookManager = createPluginHookManager();
        registerPluginHooks(this.pluginHookManager);

        if (!this.pluginHookManager.hookAll()) {
            logSevere(this.messageConfig.getMessage("general.required-plugin-missing"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseManager = createDatabaseManager();
        if (!this.databaseManager.initialize()) {
            logSevere(this.messageConfig.getMessage("general.database-initialization-failed"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.guiManager = new GuiManager(this);
        registerCommands();
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
        this.commandManager = null;
        this.pluginHookManager = null;
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

    /**
     * Returns the command manager instance.
     *
     * @return command manager
     */
    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    /**
     * Returns the plugin hook manager instance.
     *
     * @return plugin hook manager
     */
    public PluginHookManager getPluginHookManager() {
        return this.pluginHookManager;
    }

    /**
     * Creates the plugin hook manager used during startup.
     *
     * @return plugin hook manager
     */
    protected PluginHookManager createPluginHookManager() {
        return new PluginHookManager(this, this.messageConfig);
    }

    /**
     * Registers the built-in required plugin hooks.
     *
     * @param hookManager hook manager
     */
    protected void registerPluginHooks(PluginHookManager hookManager) {
        hookManager.register(new VaultHook());
        hookManager.register(new LuckPermsHook());
        hookManager.register(new MultiverseHook());
        hookManager.register(new FaweHook());
    }

    /**
     * Creates the database manager used during startup.
     *
     * @return database manager
     */
    protected DatabaseManager createDatabaseManager() {
        return new DatabaseManager(this, this.configManager);
    }

    /**
     * Registers the plugin command handlers.
     */
    protected void registerCommands() {
        this.commandManager = new CommandManager(this);
        this.commandManager.register();
    }

    private void logMessage(String message) {
        Logger logger = getLogger();
        logger.info(ChatColor.stripColor(message));
    }

    private void logSevere(String message) {
        Logger logger = getLogger();
        logger.severe(ChatColor.stripColor(message));
    }
}
