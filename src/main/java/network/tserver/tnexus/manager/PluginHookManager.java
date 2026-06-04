package network.tserver.tnexus.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import network.tserver.tnexus.config.MessageConfig;
import network.tserver.tnexus.manager.hook.PluginHook;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Manages required and optional backend plugin hooks.
 */
public final class PluginHookManager {

    private final PluginManager pluginManager;
    private final Logger logger;
    private final MessageConfig messageConfig;
    private final List<HookRegistration> registrations;

    /**
     * Creates a new plugin hook manager.
     *
     * @param plugin owner plugin
     * @param messageConfig message config
     */
    public PluginHookManager(Plugin plugin, MessageConfig messageConfig) {
        this.pluginManager = Objects.requireNonNull(plugin, "plugin").getServer().getPluginManager();
        this.logger = plugin.getLogger();
        this.messageConfig = Objects.requireNonNull(messageConfig, "messageConfig");
        this.registrations = new ArrayList<>();
    }

    /**
     * Registers a plugin hook for later startup processing.
     *
     * @param hook hook to register
     */
    public void register(PluginHook<?> hook) {
        this.registrations.add(new HookRegistration(Objects.requireNonNull(hook, "hook")));
    }

    /**
     * Executes all registered hooks.
     *
     * @return {@code true} when all required hooks succeed
     */
    public boolean hookAll() {
        boolean allRequiredHooksSucceeded = true;
        for (HookRegistration registration : this.registrations) {
            PluginHook<?> hook = registration.hook();
            Plugin plugin = this.pluginManager.getPlugin(hook.getPluginName());
            if (plugin == null) {
                registration.setSuccessful(false);
                if (hook.isRequired()) {
                    allRequiredHooksSucceeded = false;
                    logSevere(this.messageConfig.getMessage("hook.missing", hook.getPluginName()));
                }
                continue;
            }

            boolean hooked = hook.hook(plugin);
            registration.setSuccessful(hooked);
            if (hooked) {
                logInfo(this.messageConfig.getMessage("hook.success", hook.getPluginName()));
            } else if (hook.isRequired()) {
                allRequiredHooksSucceeded = false;
                logSevere(this.messageConfig.getMessage("hook.failed", hook.getPluginName()));
            }
        }
        return allRequiredHooksSucceeded;
    }

    /**
     * Returns the first hooked API assignable to the requested type.
     *
     * @param apiType requested API type
     * @param <T> API type
     * @return hooked API or {@code null} when unavailable
     */
    public <T> T getApi(Class<T> apiType) {
        Objects.requireNonNull(apiType, "apiType");
        for (HookRegistration registration : this.registrations) {
            if (!registration.successful()) {
                continue;
            }
            Object api = registration.hook().getApi();
            if (apiType.isInstance(api)) {
                return apiType.cast(api);
            }
        }
        return null;
    }

    /**
     * Returns whether every required hook has completed successfully.
     *
     * @return {@code true} when fully ready
     */
    public boolean isFullyReady() {
        for (HookRegistration registration : this.registrations) {
            if (registration.hook().isRequired() && !registration.successful()) {
                return false;
            }
        }
        return true;
    }

    private void logInfo(String message) {
        this.logger.info(ChatColor.stripColor(message));
    }

    private void logSevere(String message) {
        this.logger.severe(ChatColor.stripColor(message));
    }

    private static final class HookRegistration {

        private final PluginHook<?> hook;
        private boolean successful;

        private HookRegistration(PluginHook<?> hook) {
            this.hook = hook;
            this.successful = false;
        }

        private PluginHook<?> hook() {
            return this.hook;
        }

        private boolean successful() {
            return this.successful;
        }

        private void setSuccessful(boolean successful) {
            this.successful = successful;
        }
    }
}
