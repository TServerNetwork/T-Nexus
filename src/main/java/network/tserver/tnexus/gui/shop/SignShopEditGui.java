package network.tserver.tnexus.gui.shop;

import java.util.List;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.gui.BaseGui;
import network.tserver.tnexus.manager.ShopStatus;
import network.tserver.tnexus.manager.SignShop;
import network.tserver.tnexus.manager.SignShopManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Owner/admin SignShop edit GUI.
 */
public final class SignShopEditGui extends BaseGui {

    private static final int ROWS = 3;
    private static final int PREVIEW_SLOT = 4;
    private static final int PRICE_SLOT = 10;
    private static final int NOTE_SLOT = 12;
    private static final int TOGGLE_SLOT = 14;
    private static final int PREVIEW_MODE_SLOT = 16;
    private static final int DELETE_SLOT = 17;

    private final SignShopManager shopManager;
    private final Player player;
    private final SignShop shop;

    /**
     * Creates the edit GUI.
     *
     * @param plugin plugin instance
     * @param shopManager shop manager
     * @param player target player
     * @param shop edited shop
     */
    public SignShopEditGui(TNexus plugin, SignShopManager shopManager, Player player, SignShop shop) {
        super(plugin, player, plugin.getMessageConfig().getMessage("shop.gui.edit.title"), ROWS);
        this.shopManager = shopManager;
        this.player = player;
        this.shop = shop;
    }

    @Override
    protected void buildContent() {
        setItem(PREVIEW_SLOT, createPreviewItem(), null);
        setItem(
                PRICE_SLOT,
                createItem(
                        Material.GOLD_INGOT,
                        getPlugin().getMessageConfig().getMessage("shop.gui.edit.price.name"),
                        List.of(getPlugin().getMessageConfig().getMessage("shop.gui.edit.price.lore"))),
                event -> this.shopManager.openPriceFlow(this.player, this.shop));
        setItem(
                NOTE_SLOT,
                createItem(
                        Material.WRITABLE_BOOK,
                        getPlugin().getMessageConfig().getMessage("shop.gui.edit.note.name"),
                        List.of(getPlugin().getMessageConfig().getMessage("shop.gui.edit.note.lore"))),
                event -> this.shopManager.openNoteEditor(this.player, this.shop));
        setItem(
                TOGGLE_SLOT,
                createItem(
                        this.shop.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                        getPlugin().getMessageConfig().getMessage(
                                this.shop.isEnabled() ? "shop.gui.edit.toggle.enabled" : "shop.gui.edit.toggle.disabled"),
                        List.of(getPlugin().getMessageConfig().getMessage("shop.gui.edit.toggle.lore"))),
                event -> this.shopManager.toggleEnabled(this.player, this.shop));
        setItem(
                PREVIEW_MODE_SLOT,
                createItem(
                        Material.ENDER_EYE,
                        getPlugin().getMessageConfig().getMessage("shop.gui.edit.preview-mode.name"),
                        List.of(getPlugin().getMessageConfig().getMessage("shop.gui.edit.preview-mode.lore"))),
                event -> this.shopManager.openPreviewGui(this.player, this.shop));
        setItem(
                DELETE_SLOT,
                createItem(
                        Material.LAVA_BUCKET,
                        getPlugin().getMessageConfig().getMessage("shop.gui.edit.delete.name"),
                        List.of(getPlugin().getMessageConfig().getMessage("shop.gui.edit.delete.lore"))),
                event -> this.shopManager.openDeleteGui(this.player, this.shop));
    }

    private ItemStack createPreviewItem() {
        ItemStack itemStack = this.shop.getItemStack();
        if (itemStack == null) {
            return createItem(
                    Material.BARRIER,
                    getPlugin().getMessageConfig().getMessage("shop.gui.unlinked.name"),
                    List.of(getPlugin().getMessageConfig().getMessage("shop.gui.unlinked.lore")));
        }
        ItemStack preview = itemStack.clone();
        var meta = preview.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(getPlugin().getMessageConfig().getMessage("shop.gui.preview.name", this.shop.getItemName()));
            meta.setLore(List.of(
                    getPlugin().getMessageConfig().getMessage("shop.gui.edit.owner", this.shop.getOwnerName()),
                    getPlugin().getMessageConfig().getMessage(
                            "shop.gui.edit.status",
                            this.shop.isEnabled() ? ShopStatus.AVAILABLE.name() : ShopStatus.DISABLED.name())));
            preview.setItemMeta(meta);
        }
        return preview;
    }
}
