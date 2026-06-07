package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks farming-related player statistics.
 */
public final class PlayerFarmingStatsListener implements Listener {

    private final PlayerStatsManager playerStatsManager;

    /**
     * Creates a new player farming stats listener.
     *
     * @param plugin plugin instance
     */
    public PlayerFarmingStatsListener(TNexus plugin) {
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (shouldIgnore(player)) {
            return;
        }

        for (ItemStack itemStack : event.getItemsHarvested()) {
            Material material = itemStack.getType();
            if (material == Material.AIR) {
                continue;
            }
            this.playerStatsManager.recordHarvest(player, material, itemStack.getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player) || shouldIgnore(player)) {
            return;
        }
        this.playerStatsManager.recordBreed(player, event.getMother().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        if (shouldIgnore(player)) {
            return;
        }
        if (!(event.getCaught() instanceof Item item)) {
            return;
        }

        Material material = item.getItemStack().getType();
        if (material == Material.AIR) {
            return;
        }
        this.playerStatsManager.recordFish(player, material);
    }

    private boolean shouldIgnore(Player player) {
        return player.getGameMode() == GameMode.CREATIVE;
    }
}
