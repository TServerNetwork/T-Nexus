package network.tserver.tnexus.manager.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Required hook for LuckPerms.
 */
public final class LuckPermsHook implements PluginHook<LuckPerms> {

    private LuckPerms api;

    @Override
    public String getPluginName() {
        return "LuckPerms";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public LuckPerms getApi() {
        return this.api;
    }

    @Override
    public boolean hook(Plugin plugin) {
        if (plugin == null || !getPluginName().equals(plugin.getName())) {
            return false;
        }
        try {
            this.api = LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            this.api = Bukkit.getServicesManager().load(LuckPerms.class);
        }
        return this.api != null;
    }
}
