package network.tserver.tnexus.gui.shop;

import java.util.List;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.gui.BaseGui;
import network.tserver.tnexus.manager.SignShop;
import network.tserver.tnexus.manager.SignShopManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Delete confirmation GUI for SignShops.
 */
public final class SignShopDeleteGui extends BaseGui {

    private static final int ROWS = 3;
    private static final int PREVIEW_SLOT = 4;
    private static final int CONFIRM_SLOT = 12;
    private static final int CANCEL_SLOT = 14;

    private final SignShopManager shopManager;
    private final Player player;
    private final SignShop shop;

    /**
     * Creates the delete confirmation GUI.
     *
     * @param plugin plugin instance
     * @param shopManager shop manager
     * @param player target player
     * @param shop target shop
     */
    public SignShopDeleteGui(TNexus plugin, SignShopManager shopManager, Player player, SignShop shop) {
        super(plugin, player, plugin.getMessageConfig().getMessage("shop.gui.delete.title"), ROWS);
        this.shopManager = shopManager;
        this.player = player;
        this.shop = shop;
    }

    @Override
    protected void buildContent() {
        setItem(
                PREVIEW_SLOT,
                createItem(
                        Material.BARRIER,
                        getPlugin().getMessageConfig().getMessage("shop.gui.delete.warning.name"),
                        List.of(getPlugin().getMessageConfig().getMessage("shop.gui.delete.warning.lore"))),
                null);
        setItem(
                CONFIRM_SLOT,
                createItem(
                        Material.LIME_WOOL,
                        getPlugin().getMessageConfig().getMessage("shop.gui.delete.confirm.name"),
                        List.of(getPlugin().getMessageConfig().getMessage("shop.gui.delete.confirm.lore"))),
                event -> {
                    this.shopManager.deleteShop(this.shop);
                    getPlugin().getMessageConfig().sendMessage(this.player, "shop.delete.success");
                    this.player.closeInventory();
                });
        setItem(
                CANCEL_SLOT,
                createItem(
                        Material.RED_WOOL,
                        getPlugin().getMessageConfig().getMessage("shop.gui.delete.cancel.name"),
                        List.of(getPlugin().getMessageConfig().getMessage("shop.gui.delete.cancel.lore"))),
                event -> this.shopManager.openEditGui(this.player, this.shop));
    }
}
