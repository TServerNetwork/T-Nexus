package network.tserver.tnexus.manager.hook;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.plugin.Plugin;

/**
 * Required hook for Multiverse-Core.
 */
public final class MultiverseHook implements PluginHook<MVWorldManager> {

    private MVWorldManager worldManager;

    @Override
    public String getPluginName() {
        return "Multiverse-Core";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public MVWorldManager getApi() {
        return this.worldManager;
    }

    @Override
    public boolean hook(Plugin plugin) {
        if (plugin == null || !getPluginName().equals(plugin.getName())) {
            return false;
        }
        try {
            Object api = plugin.getClass().getMethod("getMVWorldManager").invoke(plugin);
            if (!(api instanceof MVWorldManager multiverseWorldManager)) {
                return false;
            }
            this.worldManager = multiverseWorldManager;
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }
}
