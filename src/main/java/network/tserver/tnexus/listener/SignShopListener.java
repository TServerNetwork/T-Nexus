package network.tserver.tnexus.listener;

import java.util.List;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.ShopType;
import network.tserver.tnexus.manager.SignShop;
import network.tserver.tnexus.manager.SignShopManager;
import network.tserver.tnexus.util.BlockPosition;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/**
 * Handles SignShop sign placement, interaction, protection, and status refreshes.
 */
public final class SignShopListener implements Listener {

    private final TNexus plugin;
    private final SignShopManager shopManager;

    /**
     * Creates and registers the SignShop listener.
     *
     * @param plugin plugin instance
     * @param shopManager shop manager
     */
    public SignShopListener(TNexus plugin, SignShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (event.getSide() != org.bukkit.block.sign.Side.FRONT) {
            return;
        }

        ShopType shopType = this.shopManager.resolveShopType(event.getLine(0));
        if (shopType != null) {
            Block signBlock = event.getBlock();
            Block adjacentChest = this.shopManager.findAdjacentChest(signBlock);
            var templateItem = adjacentChest == null ? null : this.shopManager.findFirstTemplateItem(adjacentChest);
            String note = sanitizeLine(event.getLine(3));
            SignShop shop = this.shopManager.createShop(
                    event.getPlayer(),
                    signBlock,
                    shopType,
                    note,
                    adjacentChest,
                    templateItem);
            if (shop == null) {
                event.setCancelled(true);
                return;
            }

            String label = shopType == ShopType.SERVER ? "[ServerShop]" : "[Shop]";
            event.setLine(0, "&c" + label);
            event.setLine(1, templateItem == null ? "Unlinked" : ChatColor.stripColor(shop.getItemName()));
            event.setLine(2, "B - | S -");
            event.setLine(3, note);
            return;
        }

        if (this.shopManager.getShop(event.getBlock()) != null) {
            event.setCancelled(true);
            this.plugin.getMessageConfig().sendMessage(event.getPlayer(), "shop.sign.direct-edit-blocked");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.hasBlock()) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        boolean usingLinkTool = this.shopManager.isLinkTool(event.getItem());
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && this.shopManager.handleLinkInteraction(event.getPlayer(), clickedBlock, usingLinkTool)) {
            event.setCancelled(true);
            return;
        }

        SignShop shop = this.shopManager.getShop(clickedBlock);
        if (shop == null) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            this.shopManager.openBrowseGui(event.getPlayer(), shop);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (this.shopManager.canModify(event.getPlayer(), shop)) {
                this.shopManager.openEditGui(event.getPlayer(), shop);
            } else {
                this.plugin.getMessageConfig().sendMessage(event.getPlayer(), "shop.edit.not-owner");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        SignShop shop = this.shopManager.getShop(event.getBlock());
        if (shop != null) {
            if (!this.shopManager.canModify(event.getPlayer(), shop)) {
                event.setCancelled(true);
                this.plugin.getMessageConfig().sendMessage(event.getPlayer(), "shop.break.denied");
                return;
            }
            this.shopManager.deleteShop(shop);
            this.plugin.getMessageConfig().sendMessage(event.getPlayer(), "shop.delete.success");
            return;
        }

        if (isChest(event.getBlock())) {
            this.shopManager.refreshByChest(BlockPosition.from(event.getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        for (BlockPosition blockPosition : this.shopManager.getChestPositions(event.getInventory())) {
            this.shopManager.refreshByChest(blockPosition);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        for (var inventory : List.of(event.getSource(), event.getDestination())) {
            for (BlockPosition blockPosition : this.shopManager.getChestPositions(inventory)) {
                this.shopManager.refreshByChest(blockPosition);
            }
        }
    }

    private boolean isChest(Block block) {
        return block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST;
    }

    private String sanitizeLine(@Nullable String line) {
        return line == null ? "" : ChatColor.stripColor(line).trim();
    }
}
