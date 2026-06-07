package network.tserver.tnexus.listener;

import java.util.UUID;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Furnace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Tracks smelting, brewing, and enchanting statistics.
 */
public final class PlayerProcessingStatsListener implements Listener {

    private final PlayerStatsManager playerStatsManager;

    /**
     * Creates a new player processing stats listener.
     *
     * @param plugin plugin instance
     */
    public PlayerProcessingStatsListener(TNexus plugin) {
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            markProcessingStationInteraction(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            markProcessingStationInteraction(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            markProcessingStationInteraction(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        UUID playerId = this.playerStatsManager.resolveProcessingStationPlayer(event.getBlock());
        if (playerId == null) {
            return;
        }

        Material resultType = event.getResult().getType();
        if (resultType == Material.AIR) {
            return;
        }
        this.playerStatsManager.recordSmelt(playerId, resultType);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        UUID playerId = this.playerStatsManager.resolveProcessingStationPlayer(event.getBlock());
        if (playerId == null) {
            return;
        }
        this.playerStatsManager.recordBrew(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (event.getEnchantsToAdd().isEmpty()) {
            return;
        }

        Player player = event.getEnchanter();
        for (Enchantment enchantment : event.getEnchantsToAdd().keySet()) {
            this.playerStatsManager.recordEnchantment(player, enchantment);
        }
        this.playerStatsManager.recordEnchantedItem(player, event.getItem().getType());
    }

    private void markProcessingStationInteraction(Player player, Inventory inventory) {
        Block block = resolveProcessingBlock(inventory);
        if (block == null) {
            return;
        }
        this.playerStatsManager.markProcessingStationInteraction(player, block);
    }

    private Block resolveProcessingBlock(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState blockState && isTrackedProcessingState(blockState)) {
            return blockState.getBlock();
        }
        return null;
    }

    private boolean isTrackedProcessingState(BlockState blockState) {
        return blockState instanceof Furnace || blockState instanceof BrewingStand;
    }
}
