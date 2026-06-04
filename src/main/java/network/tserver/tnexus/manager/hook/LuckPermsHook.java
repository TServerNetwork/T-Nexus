package network.tserver.tnexus.manager.hook;

import org.bukkit.plugin.Plugin;

/**
 * Required hook for LuckPerms.
 */
public final class LuckPermsHook implements PluginHook<Plugin> {

    private Plugin plugin;

    @Override
    public String getPluginName() {
        return "LuckPerms";
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
