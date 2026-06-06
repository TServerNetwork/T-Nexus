package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Tracks player movement distance statistics.
 */
public final class PlayerMovementStatsListener implements Listener {

    private final PlayerStatsManager playerStatsManager;

    /**
     * Creates a new player movement stats listener.
     *
     * @param plugin plugin instance
     */
    public PlayerMovementStatsListener(TNexus plugin) {
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        this.playerStatsManager.recordMovement(event.getPlayer(), from, to);
    }
}
