package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Tracks dealt and taken damage by entity identifier.
 */
public final class PlayerEntityDamageStatsListener implements Listener {

    private final PlayerStatsManager playerStatsManager;

    /**
     * Creates a new player entity damage stats listener.
     *
     * @param plugin plugin instance
     */
    public PlayerEntityDamageStatsListener(TNexus plugin) {
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0.0D) {
            return;
        }

        DamageAttribution attribution = resolveAttribution(event.getDamager());
        if (attribution == null) {
            return;
        }

        Entity target = event.getEntity();
        if (attribution.playerOrigin() && target instanceof Player damagedPlayer) {
            this.playerStatsManager.recordDamageDealt(
                    attribution.player(),
                    damagedPlayer.getUniqueId().toString(),
                    finalDamage);
        } else if (attribution.playerOrigin()) {
            this.playerStatsManager.recordDamageDealt(
                    attribution.player(),
                    target.getType().name(),
                    finalDamage);
        }

        if (target instanceof Player damagedPlayer) {
            this.playerStatsManager.recordDamageTaken(
                    damagedPlayer,
                    attribution.identifier(),
                    finalDamage);
        }
    }

    private DamageAttribution resolveAttribution(Entity damager) {
        if (damager instanceof Player player) {
            return new DamageAttribution(true, player, player.getUniqueId().toString());
        }
        if (damager instanceof Projectile projectile) {
            Object shooter = projectile.getShooter();
            if (shooter instanceof Player shooterPlayer) {
                return new DamageAttribution(true, shooterPlayer, shooterPlayer.getUniqueId().toString());
            }
            if (shooter instanceof Entity shooterEntity && !isExcludedTameable(shooterEntity)) {
                return new DamageAttribution(false, null, shooterEntity.getType().name());
            }
            return null;
        }
        if (damager instanceof TNTPrimed tntPrimed) {
            Entity source = tntPrimed.getSource();
            if (source instanceof Player player) {
                return new DamageAttribution(true, player, player.getUniqueId().toString());
            }
            if (source != null && !isExcludedTameable(source)) {
                return new DamageAttribution(false, null, source.getType().name());
            }
            return new DamageAttribution(false, null, damager.getType().name());
        }
        if (isExcludedTameable(damager)) {
            return null;
        }
        return new DamageAttribution(false, null, damager.getType().name());
    }

    private boolean isExcludedTameable(Entity entity) {
        if (!(entity instanceof Tameable tameable)) {
            return false;
        }
        return tameable.isTamed() && tameable.getOwnerUniqueId() != null;
    }

    private record DamageAttribution(boolean playerOrigin, Player player, String identifier) {
    }
}
