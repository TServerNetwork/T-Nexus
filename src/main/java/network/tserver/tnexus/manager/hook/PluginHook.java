package network.tserver.tnexus.manager.hook;

import org.bukkit.plugin.Plugin;

/**
 * Represents a pluggable backend hook managed by T-Nexus.
 *
 * @param <T> exposed API type
 */
public interface PluginHook<T> {

    /**
     * Returns the target plugin name used for lookup.
     *
     * @return plugin name
     */
    String getPluginName();

    /**
     * Returns whether the plugin is required for startup.
     *
     * @return {@code true} when required
     */
    boolean isRequired();

    /**
     * Returns the exposed API object after a successful hook.
     *
     * @return hooked API, or {@code null} when unavailable
     */
    T getApi();

    /**
     * Attempts to hook the provided plugin instance.
     *
     * @param plugin target plugin
     * @return {@code true} when hook succeeds
     */
    boolean hook(Plugin plugin);
}
