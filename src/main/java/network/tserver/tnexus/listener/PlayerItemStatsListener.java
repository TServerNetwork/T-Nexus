package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks player item pickup and drop statistics by material.
 */
public final class PlayerItemStatsListener implements Listener {

    private final PlayerStatsManager playerStatsManager;

    /**
     * Creates a new player item stats listener.
     *
     * @param plugin plugin instance
     */
    public PlayerItemStatsListener(TNexus plugin) {
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || shouldIgnore(player)) {
            return;
        }

        ItemStack itemStack = event.getItem().getItemStack();
        this.playerStatsManager.recordItemPickup(player, itemStack.getType(), itemStack.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (shouldIgnore(player)) {
            return;
        }

        ItemStack itemStack = event.getItemDrop().getItemStack();
        this.playerStatsManager.recordItemDrop(player, itemStack.getType(), itemStack.getAmount());
    }

    private boolean shouldIgnore(Player player) {
        return player.getGameMode() == GameMode.CREATIVE;
    }
}
