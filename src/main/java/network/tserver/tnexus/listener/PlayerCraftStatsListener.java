package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks player craft statistics by crafted result material.
 */
public final class PlayerCraftStatsListener implements Listener {

    private final PlayerStatsManager playerStatsManager;

    /**
     * Creates a new player craft stats listener.
     *
     * @param plugin plugin instance
     */
    public PlayerCraftStatsListener(TNexus plugin) {
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        this.playerStatsManager.recordCraft(player, result.getType(), result.getAmount());
    }
}
