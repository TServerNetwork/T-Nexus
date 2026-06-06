package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Tracks player block placement and break statistics.
 */
public final class PlayerBlockStatsListener implements Listener {

    private final PlayerStatsManager playerStatsManager;

    /**
     * Creates a new player block stats listener.
     *
     * @param plugin plugin instance
     */
    public PlayerBlockStatsListener(TNexus plugin) {
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (shouldIgnore(player)) {
            return;
        }
        this.playerStatsManager.recordBlockPlacement(player, event.getBlockPlaced().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (shouldIgnore(player)) {
            return;
        }
        this.playerStatsManager.recordBlockBreak(player, event.getBlock().getType());
    }

    private boolean shouldIgnore(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
                || this.playerStatsManager.isWorldEditOperationSuppressed(player.getUniqueId());
    }
}
