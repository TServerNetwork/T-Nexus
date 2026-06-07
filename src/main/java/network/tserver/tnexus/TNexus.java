package network.tserver.tnexus;

import java.util.logging.Logger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import network.tserver.tnexus.command.CommandManager;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.config.MessageConfig;
import network.tserver.tnexus.database.DatabaseManager;
import network.tserver.tnexus.database.repository.PayQueueRepository;
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
import network.tserver.tnexus.database.repository.TransactionRepository;
import network.tserver.tnexus.gui.AnvilGuiManager;
import network.tserver.tnexus.gui.GuiManager;
import network.tserver.tnexus.manager.AuditLogManager;
import network.tserver.tnexus.manager.EconomyManager;
import network.tserver.tnexus.manager.PaymentManager;
import network.tserver.tnexus.manager.PlayerStatsManager;
import network.tserver.tnexus.manager.PluginHookManager;
import network.tserver.tnexus.manager.SignShopManager;
import network.tserver.tnexus.manager.hook.FaweHook;
import network.tserver.tnexus.manager.hook.LuckPermsHook;
import network.tserver.tnexus.manager.hook.MultiverseHook;
import network.tserver.tnexus.manager.hook.VaultHook;
import network.tserver.tnexus.listener.PaymentNotificationListener;
import network.tserver.tnexus.listener.PlayerBlockStatsListener;
import network.tserver.tnexus.listener.PlayerCraftStatsListener;
import network.tserver.tnexus.listener.PlayerDeathStatsListener;
import network.tserver.tnexus.listener.PlayerEntityDamageStatsListener;
import network.tserver.tnexus.listener.PlayerFarmingStatsListener;
import network.tserver.tnexus.listener.PlayerItemStatsListener;
import network.tserver.tnexus.listener.PlayerMiscStatsListener;
import network.tserver.tnexus.listener.PlayerMovementStatsListener;
import network.tserver.tnexus.listener.PlayerProcessingStatsListener;
import network.tserver.tnexus.listener.PlayerSessionListener;
import network.tserver.tnexus.listener.SignShopListener;
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
    private AuditLogManager auditLogManager;
    private EconomyManager economyManager;
    private PaymentManager paymentManager;
    private PlayerStatsManager playerStatsManager;
    private PaymentNotificationListener paymentNotificationListener;
    private PlayerSessionListener playerSessionListener;
    private PlayerDeathStatsListener playerDeathStatsListener;
    private PlayerEntityDamageStatsListener playerEntityDamageStatsListener;
    private PlayerMovementStatsListener playerMovementStatsListener;
    private PlayerBlockStatsListener playerBlockStatsListener;
    private PlayerCraftStatsListener playerCraftStatsListener;
    private PlayerProcessingStatsListener playerProcessingStatsListener;
    private PlayerFarmingStatsListener playerFarmingStatsListener;
    private PlayerItemStatsListener playerItemStatsListener;
    private PlayerMiscStatsListener playerMiscStatsListener;
    private AutoCloseable worldEditStatsListener;
    private SignShopManager signShopManager;
    private SignShopListener signShopListener;

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
        this.auditLogManager = new AuditLogManager(this);
        this.paymentManager = createPaymentManager();
        this.playerStatsManager = createPlayerStatsManager();
        this.paymentNotificationListener = new PaymentNotificationListener(this);
        this.playerSessionListener = new PlayerSessionListener(this);
        this.playerDeathStatsListener = new PlayerDeathStatsListener(this);
        this.playerEntityDamageStatsListener = new PlayerEntityDamageStatsListener(this);
        this.playerMovementStatsListener = new PlayerMovementStatsListener(this);
        this.playerBlockStatsListener = new PlayerBlockStatsListener(this);
        this.playerCraftStatsListener = new PlayerCraftStatsListener(this);
        this.playerProcessingStatsListener = new PlayerProcessingStatsListener(this);
        this.playerFarmingStatsListener = new PlayerFarmingStatsListener(this);
        this.playerItemStatsListener = new PlayerItemStatsListener(this);
        this.playerMiscStatsListener = new PlayerMiscStatsListener(this);
        this.worldEditStatsListener = createWorldEditStatsListener();
        this.signShopManager = new SignShopManager(this);
        this.signShopListener = new SignShopListener(this, this.signShopManager);
        this.signShopManager.initialize();
        registerCommands();
        logMessage(this.messageConfig.getMessage("general.plugin-enabled"));
    }

    @Override
    public void onDisable() {
        flushPlayerSessions();
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        if (this.worldEditStatsListener != null) {
            try {
                this.worldEditStatsListener.close();
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Failed to close WorldEdit stats listener cleanly.", exception);
            }
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
        this.auditLogManager = null;
        this.economyManager = null;
        this.paymentManager = null;
        this.playerStatsManager = null;
        this.paymentNotificationListener = null;
        this.playerSessionListener = null;
        this.playerDeathStatsListener = null;
        this.playerEntityDamageStatsListener = null;
        this.playerMovementStatsListener = null;
        this.playerBlockStatsListener = null;
        this.playerCraftStatsListener = null;
        this.playerProcessingStatsListener = null;
        this.playerFarmingStatsListener = null;
        this.playerItemStatsListener = null;
        this.playerMiscStatsListener = null;
        this.worldEditStatsListener = null;
        this.signShopManager = null;
        this.signShopListener = null;
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
     * Returns the audit-log manager instance.
     *
     * @return audit-log manager
     */
    public AuditLogManager getAuditLogManager() {
        return this.auditLogManager;
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
     * Returns the player stats manager instance.
     *
     * @return player stats manager
     */
    public PlayerStatsManager getPlayerStatsManager() {
        return this.playerStatsManager;
    }

    /**
     * Returns the SignShop manager instance.
     *
     * @return SignShop manager
     */
    public SignShopManager getSignShopManager() {
        return this.signShopManager;
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
     * Creates the player stats manager used during startup.
     *
     * @return player stats manager
     */
    protected PlayerStatsManager createPlayerStatsManager() {
        return new PlayerStatsManager(this, new PlayerStatsRepository(this.databaseManager));
    }

    /**
     * Creates the optional WorldEdit stats suppression listener when the API is available.
     *
     * @return closeable listener handle or {@code null} when unavailable
     */
    protected AutoCloseable createWorldEditStatsListener() {
        if (!isWorldEditApiAvailable()) {
            return null;
        }

        try {
            Class<?> listenerClass = Class.forName("network.tserver.tnexus.listener.WorldEditStatsListener");
            return (AutoCloseable) listenerClass.getConstructor(TNexus.class).newInstance(this);
        } catch (ReflectiveOperationException | LinkageError exception) {
            getLogger().log(Level.WARNING, "Failed to initialize WorldEdit stats listener.", exception);
            return null;
        }
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

    private boolean isWorldEditApiAvailable() {
        try {
            Class.forName("com.sk89q.worldedit.WorldEdit", false, TNexus.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError | RuntimeException exception) {
            return false;
        }
    }

    private void logMessage(String message) {
        Logger logger = getLogger();
        logger.info(ChatColor.stripColor(message));
    }

    private void logSevere(String message) {
        Logger logger = getLogger();
        logger.severe(ChatColor.stripColor(message));
    }

    private void flushPlayerSessions() {
        if (this.playerStatsManager == null) {
            return;
        }

        try {
            this.playerStatsManager.shutdown(getServer().getOnlinePlayers()).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            getLogger().log(Level.SEVERE, "Interrupted while flushing player stats during shutdown.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            getLogger().log(Level.SEVERE, "Failed to flush player stats during shutdown.", exception);
        }
    }
}
