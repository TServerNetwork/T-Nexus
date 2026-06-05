package network.tserver.tnexus;

import java.util.logging.Logger;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import network.tserver.tnexus.command.CommandManager;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.config.MessageConfig;
import network.tserver.tnexus.database.DatabaseManager;
import network.tserver.tnexus.database.repository.PayQueueRepository;
import network.tserver.tnexus.database.repository.TransactionRepository;
import network.tserver.tnexus.gui.AnvilGuiManager;
import network.tserver.tnexus.gui.GuiManager;
import network.tserver.tnexus.manager.EconomyManager;
import network.tserver.tnexus.manager.PaymentManager;
import network.tserver.tnexus.manager.PluginHookManager;
import network.tserver.tnexus.manager.hook.FaweHook;
import network.tserver.tnexus.manager.hook.LuckPermsHook;
import network.tserver.tnexus.manager.hook.MultiverseHook;
import network.tserver.tnexus.manager.hook.VaultHook;
import network.tserver.tnexus.listener.PaymentNotificationListener;
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
    private AnvilGuiManager anvilGuiManager;
    private CommandManager commandManager;
    private PluginHookManager pluginHookManager;
    private EconomyManager economyManager;
    private PaymentManager paymentManager;
    private PaymentNotificationListener paymentNotificationListener;

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

        try {
            this.economyManager = createEconomyManager();
        } catch (IllegalStateException exception) {
            logSevere(this.messageConfig.getMessage("hook.failed", "Vault Economy provider"));
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
        this.anvilGuiManager = new AnvilGuiManager(this);
        this.paymentManager = createPaymentManager();
        this.paymentNotificationListener = new PaymentNotificationListener(this);
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
        this.anvilGuiManager = null;
        this.commandManager = null;
        this.pluginHookManager = null;
        this.economyManager = null;
        this.paymentManager = null;
        this.paymentNotificationListener = null;
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
     * Returns the anvil GUI manager instance.
     *
     * @return anvil GUI manager
     */
    public AnvilGuiManager getAnvilGuiManager() {
        return this.anvilGuiManager;
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
     * Returns the economy manager instance.
     *
     * @return economy manager
     */
    public EconomyManager getEconomyManager() {
        return this.economyManager;
    }

    /**
     * Returns the payment manager instance.
     *
     * @return payment manager
     */
    public PaymentManager getPaymentManager() {
        return this.paymentManager;
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
     * Creates the economy manager used during startup.
     *
     * @return economy manager
     */
    protected EconomyManager createEconomyManager() {
        return new EconomyManager(this);
    }

    /**
     * Creates the payment manager used during startup.
     *
     * @return payment manager
     */
    protected PaymentManager createPaymentManager() {
        return new PaymentManager(
                this,
                this.economyManager,
                new PayQueueRepository(this.databaseManager),
                new TransactionRepository(this.databaseManager));
    }

    /**
     * Registers the plugin command handlers.
     */
    protected void registerCommands() {
        this.commandManager = new CommandManager(this);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            this.commandManager.registerCommands(event.registrar());
        });
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
