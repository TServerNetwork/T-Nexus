package network.tserver.tnexus.manager.hook;

import org.bukkit.plugin.Plugin;

/**
 * Required hook for Multiverse-Core.
 */
public final class MultiverseHook implements PluginHook<Plugin> {

    private Plugin plugin;

    @Override
    public String getPluginName() {
        return "Multiverse-Core";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public Plugin getApi() {
        return this.plugin;
    }

    @Override
    public boolean hook(Plugin plugin) {
        if (plugin == null || !getPluginName().equals(plugin.getName())) {
            return false;
        }
        this.plugin = plugin;
        return true;
    }
}
