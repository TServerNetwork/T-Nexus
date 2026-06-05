package network.tserver.tnexus.gui.shop;

import java.util.List;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.gui.BaseGui;
import network.tserver.tnexus.manager.SignShop;
import network.tserver.tnexus.manager.SignShopManager;
import network.tserver.tnexus.manager.TradeAction;
import network.tserver.tnexus.util.CurrencyFormatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Buyer-facing SignShop GUI.
 */
public final class SignShopBrowseGui extends BaseGui {

    private static final int ROWS = 4;
    private static final int PREVIEW_SLOT = 4;
    private static final int ONE_SLOT = 19;
    private static final int EIGHT_SLOT = 20;
    private static final int SIXTEEN_SLOT = 21;
    private static final int THIRTY_TWO_SLOT = 22;
    private static final int SIXTY_FOUR_SLOT = 23;
    private static final int MAX_SLOT = 24;
    private static final int CUSTOM_SLOT = 25;

    private final SignShopManager shopManager;
    private final Player player;
    private final SignShop shop;

    /**
     * Creates the browse GUI.
     *
     * @param plugin plugin instance
     * @param shopManager shop manager
     * @param player target player
     * @param shop viewed shop
     */
    public SignShopBrowseGui(TNexus plugin, SignShopManager shopManager, Player player, SignShop shop) {
        super(plugin, player, plugin.getMessageConfig().getMessage("shop.gui.browse.title"), ROWS);
        this.shopManager = shopManager;
        this.player = player;
        this.shop = shop;
    }

    @Override
    protected void buildContent() {
        setItem(PREVIEW_SLOT, createPreviewItem(), null);
        setAmountButton(ONE_SLOT, 1);
        setAmountButton(EIGHT_SLOT, 8);
        setAmountButton(SIXTEEN_SLOT, 16);
        setAmountButton(THIRTY_TWO_SLOT, 32);
        setAmountButton(SIXTY_FOUR_SLOT, 64);
        setMaxButton();
        setCustomButton();
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
                    getPlugin().getMessageConfig().getMessage(
                            "shop.gui.preview.buy",
                            this.shop.getBuyPrice() == null ? "-" : CurrencyFormatter.format(getPlugin(), this.shop.getBuyPrice())),
                    getPlugin().getMessageConfig().getMessage(
                            "shop.gui.preview.sell",
                            this.shop.getSellPrice() == null ? "-" : CurrencyFormatter.format(getPlugin(), this.shop.getSellPrice())),
                    getPlugin().getMessageConfig().getMessage("shop.gui.preview.hint")));
            preview.setItemMeta(meta);
        }
        return preview;
    }

    private void setAmountButton(int slot, int amount) {
        ItemStack icon = this.shop.getItemStack() == null
                ? new ItemStack(Material.PAPER)
                : this.shop.getItemStack().clone();
        icon.setAmount(Math.min(amount, icon.getMaxStackSize()));
        var meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(getPlugin().getMessageConfig().getMessage("shop.gui.amount.name", amount));
            meta.setLore(List.of(
                    getPlugin().getMessageConfig().getMessage(
                            "shop.gui.amount.buy",
                            amount,
                            formatTotal(this.shop.getBuyPrice(), amount)),
                    getPlugin().getMessageConfig().getMessage(
                            "shop.gui.amount.sell",
                            amount,
                            formatTotal(this.shop.getSellPrice(), amount)),
                    getPlugin().getMessageConfig().getMessage("shop.gui.amount.hint")));
            icon.setItemMeta(meta);
        }
        setItem(slot, icon, event -> {
            if (this.shopManager.isBuyClick(event.getClick())) {
                this.shopManager.executeTrade(this.player, this.shop, TradeAction.BUY, amount);
            } else if (this.shopManager.isSellClick(event.getClick())) {
                this.shopManager.executeTrade(this.player, this.shop, TradeAction.SELL, amount);
            }
        });
    }

    private void setMaxButton() {
        setItem(
                MAX_SLOT,
                createItem(
                        Material.CHEST,
                        getPlugin().getMessageConfig().getMessage("shop.gui.max.name"),
                        List.of(getPlugin().getMessageConfig().getMessage("shop.gui.max.lore"))),
                event -> {
                    if (this.shopManager.isBuyClick(event.getClick())) {
                        this.shopManager.executeTrade(
                                this.player,
                                this.shop,
                                TradeAction.BUY,
                                this.shopManager.computeMaxTradeAmount(this.player, this.shop, TradeAction.BUY));
                    } else if (this.shopManager.isSellClick(event.getClick())) {
                        this.shopManager.executeTrade(
                                this.player,
                                this.shop,
                                TradeAction.SELL,
                                this.shopManager.computeMaxTradeAmount(this.player, this.shop, TradeAction.SELL));
                    }
                });
    }

    private void setCustomButton() {
        setItem(
                CUSTOM_SLOT,
                createItem(
                        Material.NAME_TAG,
                        getPlugin().getMessageConfig().getMessage("shop.gui.custom.name"),
                        List.of(getPlugin().getMessageConfig().getMessage("shop.gui.custom.lore"))),
                event -> getPlugin().getAnvilGuiManager().openNumberInput(
                        this.player,
                        getPlugin().getMessageConfig().getMessage("shop.gui.custom.title"),
                        value -> this.shopManager.executeTrade(
                                this.player,
                                this.shop,
                                this.shopManager.isBuyClick(event.getClick()) ? TradeAction.BUY : TradeAction.SELL,
                                Math.max(0, (int) Math.floor(value)))));
    }

    private String formatTotal(Double unitPrice, int amount) {
        if (unitPrice == null) {
            return "-";
        }
        return CurrencyFormatter.format(getPlugin(), unitPrice * amount);
    }
}
