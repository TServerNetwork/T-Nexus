package network.tserver.tnexus.listener;

import java.util.Locale;
import java.util.logging.Level;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Tracks player death and respawn statistics.
 */
public final class PlayerDeathStatsListener implements Listener {

    private final TNexus plugin;
    private final PlayerStatsManager playerStatsManager;

    /**
     * Creates a new player death stats listener.
     *
     * @param plugin plugin instance
     */
    public PlayerDeathStatsListener(TNexus plugin) {
        this.plugin = plugin;
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        String cause = resolveDeathCause(event);
        this.playerStatsManager.recordDeath(event.getPlayer(), cause)
                .exceptionally(throwable -> {
                    this.plugin.getLogger().log(
                            Level.SEVERE,
                            "Failed to record death stats for " + event.getPlayer().getUniqueId(),
                            throwable);
                    return null;
                });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        this.playerStatsManager.recordRespawn(event.getPlayer())
                .exceptionally(throwable -> {
                    this.plugin.getLogger().log(
                            Level.SEVERE,
                            "Failed to record respawn stats for " + event.getPlayer().getUniqueId(),
                            throwable);
                    return null;
                });
    }

    private String resolveDeathCause(PlayerDeathEvent event) {
        DamageSource damageSource = event.getDamageSource();
        Entity causingEntity = damageSource.getCausingEntity();
        if (causingEntity instanceof Player killer) {
            return killer.getName();
        }
        if (causingEntity != null) {
            return causingEntity.getType().name();
        }

        return mapEnvironmentCause(damageSource.getDamageType());
    }

    private String mapEnvironmentCause(DamageType damageType) {
        String key = damageType.key().value();
        if ("out_of_world".equals(key)) {
            return "VOID";
        }
        return key.toUpperCase(Locale.ROOT);
    }
}
