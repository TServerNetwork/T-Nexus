package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.ResourceWorldManager;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Obfuscates {@code /seed} output for resource worlds.
 */
public final class ResourceWorldSeedCommandListener implements Listener {

    private static final String ADMIN_PERMISSION = "tnexus.admin";

    private final TNexus plugin;
    private final ResourceWorldManager resourceWorldManager;

    /**
     * Creates and registers the resource-world seed command listener.
     *
     * @param plugin plugin instance
     */
    public ResourceWorldSeedCommandListener(TNexus plugin) {
        this.plugin = plugin;
        this.resourceWorldManager = plugin.getResourceWorldManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!isSeedCommand(event.getMessage())) {
            return;
        }

        World world = event.getPlayer().getWorld();
        if (!this.resourceWorldManager.isResourceWorld(world.getName())) {
            return;
        }

        event.setCancelled(true);
        long displayedSeed = shouldShowRealSeed(event) ? world.getSeed() : this.resourceWorldManager.obfuscateSeed(world.getSeed());
        event.getPlayer().sendMessage(this.plugin.getMessageConfig().getMessage("resource-world.seed-value", displayedSeed));
    }

    private boolean isSeedCommand(String rawCommand) {
        String normalized = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        String command = normalized.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        return command.equals("seed") || command.equals("minecraft:seed") || command.equals("bukkit:seed");
    }

    private boolean shouldShowRealSeed(PlayerCommandPreprocessEvent event) {
        return this.resourceWorldManager.shouldShowRealSeedToAdmin()
                && (event.getPlayer().isOp() || event.getPlayer().hasPermission(ADMIN_PERMISSION));
    }
}
