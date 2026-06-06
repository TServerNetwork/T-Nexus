package network.tserver.tnexus.listener;

import java.util.logging.Level;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Tracks player join and quit events for session statistics.
 */
public final class PlayerSessionListener implements Listener {

    private final TNexus plugin;
    private final PlayerStatsManager playerStatsManager;

    /**
     * Creates a new player session listener.
     *
     * @param plugin plugin instance
     */
    public PlayerSessionListener(TNexus plugin) {
        this.plugin = plugin;
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.playerStatsManager.recordSessionStart(event.getPlayer())
                .exceptionally(throwable -> {
                    this.plugin.getLogger().log(
                            Level.SEVERE,
                            "Failed to record session start for " + event.getPlayer().getUniqueId(),
                            throwable);
                    return null;
                });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.playerStatsManager.recordSessionEnd(event.getPlayer())
                .exceptionally(throwable -> {
                    this.plugin.getLogger().log(
                            Level.SEVERE,
                            "Failed to persist session end for " + event.getPlayer().getUniqueId(),
                            throwable);
                    return null;
                });
    }
}
