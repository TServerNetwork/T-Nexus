package network.tserver.tnexus.listener;

import java.util.Map;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.ResourceWorldManager;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Blocks teleports into resource worlds while a reset is in progress.
 */
public final class ResourceWorldTeleportListener implements Listener {

    private final TNexus plugin;

    /**
     * Creates and registers the resource-world teleport listener.
     *
     * @param plugin plugin instance
     */
    public ResourceWorldTeleportListener(TNexus plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        World destinationWorld = resolveDestinationWorld(event);
        if (destinationWorld == null) {
            return;
        }

        String worldName = destinationWorld.getName();
        ResourceWorldManager resourceWorldManager = getResourceWorldManager();
        if (!resourceWorldManager.isResetting(worldName)) {
            return;
        }

        event.setCancelled(true);
        this.plugin.getMessageConfig().sendMessage(
                event.getPlayer(),
                "resource-world.tp-blocked",
                Map.of(
                        "display_name", resourceWorldManager.getDisplayName(worldName),
                        "world", worldName));
    }

    @Nullable
    private World resolveDestinationWorld(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return null;
        }
        return event.getTo().getWorld();
    }

    private ResourceWorldManager getResourceWorldManager() {
        return this.plugin.getResourceWorldManager();
    }
}
