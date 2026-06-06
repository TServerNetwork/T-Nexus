package network.tserver.tnexus.gui.shop;

import java.util.ArrayList;
import java.util.List;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.gui.BaseGui;
import network.tserver.tnexus.manager.SignShop;
import network.tserver.tnexus.manager.SignShopManager;
import network.tserver.tnexus.manager.ShopType;
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
    private static final int INFO_SLOT = 3;
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
        setItem(INFO_SLOT, createInfoItem(), null);
        setItem(PREVIEW_SLOT, createPreviewItem(), null);
        setAmountButton(ONE_SLOT, 1);
        setAmountButton(EIGHT_SLOT, 8);
        setAmountButton(SIXTEEN_SLOT, 16);
        setAmountButton(THIRTY_TWO_SLOT, 32);
        setAmountButton(SIXTY_FOUR_SLOT, 64);
        setMaxButton();
        setCustomButton();
    }

    private ItemStack createInfoItem() {
        boolean canBuy = this.shop.getBuyPrice() != null;
        boolean canSell = this.shop.getSellPrice() != null;
        List<String> lore = new ArrayList<>();
        lore.add(getPlugin().getMessageConfig().getMessage("shop.gui.showcase.separator"));
        lore.add(getPlugin().getMessageConfig().getMessage(
                canBuy ? "shop.gui.showcase.price.buy" : "shop.gui.showcase.price.buy-disabled",
                formatPrice(this.shop.getBuyPrice())));
        lore.add(getPlugin().getMessageConfig().getMessage(
                canSell ? "shop.gui.showcase.price.sell" : "shop.gui.showcase.price.sell-disabled",
                formatPrice(this.shop.getSellPrice())));
        lore.add(getPlugin().getMessageConfig().getMessage("shop.gui.showcase.separator"));

        if (canBuy) {
            lore.add(getPlugin().getMessageConfig().getMessage(
                    this.shop.getType() == ShopType.SERVER
                            ? "shop.gui.showcase.stock.server"
                            : "shop.gui.showcase.stock.player",
                    this.shopManager.getCurrentStock(this.shop)));
        }
        if (canSell) {
            lore.add(getPlugin().getMessageConfig().getMessage(
                    this.shop.getType() == ShopType.SERVER
                            ? "shop.gui.showcase.capacity.server"
                            : "shop.gui.showcase.capacity.player",
                    this.shopManager.getCurrentCapacity(this.shop)));
        }

        lore.add(getPlugin().getMessageConfig().getMessage("shop.gui.showcase.separator"));
        lore.add(getPlugin().getMessageConfig().getMessage("shop.gui.showcase.hint"));
        return createItem(
                Material.NAME_TAG,
                getPlugin().getMessageConfig().getMessage("shop.gui.showcase.name"),
                lore);
    }

    private ItemStack createPreviewItem() {
        ItemStack itemStack = this.shop.getItemStack();
        if (itemStack == null) {
            return createItem(
                    Material.BARRIER,
                    getPlugin().getMessageConfig().getMessage("shop.gui.unlinked.name"),
                    List.of(getPlugin().getMessageConfig().getMessage("shop.gui.unlinked.lore")));
        }

        return itemStack.clone();
    }

    private void setAmountButton(int slot, int amount) {
        ItemStack icon = this.shop.getItemStack() == null
                ? new ItemStack(Material.PAPER)
                : this.shop.getItemStack().clone();
        icon.setAmount(Math.min(amount, icon.getMaxStackSize()));
        int maxBuyAmount = this.shopManager.computeMaxTradeAmount(this.player, this.shop, TradeAction.BUY);
        int maxSellAmount = this.shopManager.computeMaxTradeAmount(this.player, this.shop, TradeAction.SELL);
        boolean buyEnabled = this.shop.getBuyPrice() != null;
        boolean sellEnabled = this.shop.getSellPrice() != null;
        var meta = icon.getItemMeta();
        if (meta != null) {
            String nameKey = buyEnabled || sellEnabled ? "shop.gui.amount.name" : "shop.gui.amount.disabled-name";
            meta.setDisplayName(getPlugin().getMessageConfig().getMessage(nameKey, amount));
            meta.setLore(List.of(
                    getPlugin().getMessageConfig().getMessage(
                            buyEnabled ? "shop.gui.amount.buy" : "shop.gui.amount.buy-disabled",
                            amount,
                            formatTotal(this.shop.getBuyPrice(), amount)),
                    getPlugin().getMessageConfig().getMessage(
                            sellEnabled ? "shop.gui.amount.sell" : "shop.gui.amount.sell-disabled",
                            amount,
                            formatTotal(this.shop.getSellPrice(), amount)),
                    getPlugin().getMessageConfig().getMessage(
                            buyEnabled || sellEnabled ? "shop.gui.amount.hint" : "shop.gui.amount.disabled-hint")));
            icon.setItemMeta(meta);
        }
        if (!buyEnabled && !sellEnabled) {
            setItem(slot, icon, null);
            return;
        }
        setItem(slot, icon, event -> {
            if (buyEnabled && this.shopManager.isBuyClick(event.getClick())) {
                this.shopManager.executeTrade(this.player, this.shop, TradeAction.BUY, amount, this::refreshAfterTrade);
            } else if (sellEnabled && this.shopManager.isSellClick(event.getClick())) {
                this.shopManager.executeTrade(this.player, this.shop, TradeAction.SELL, amount, this::refreshAfterTrade);
            }
        });
    }

    private void setMaxButton() {
        int maxBuyAmount = this.shopManager.computeMaxTradeAmount(this.player, this.shop, TradeAction.BUY);
        int maxSellAmount = this.shopManager.computeMaxTradeAmount(this.player, this.shop, TradeAction.SELL);
        boolean buyEnabled = maxBuyAmount > 0;
        boolean sellEnabled = maxSellAmount > 0;
        setItem(
                MAX_SLOT,
                createItem(
                        Material.CHEST,
                        getPlugin().getMessageConfig().getMessage(
                                buyEnabled || sellEnabled ? "shop.gui.max.name" : "shop.gui.max.disabled-name"),
                        List.of(getPlugin().getMessageConfig().getMessage(
                                buyEnabled || sellEnabled ? "shop.gui.max.lore" : "shop.gui.max.disabled-lore"))),
                buyEnabled || sellEnabled ? event -> {
                    if (buyEnabled && this.shopManager.isBuyClick(event.getClick())) {
                        this.shopManager.executeTrade(
                                this.player,
                                this.shop,
                                TradeAction.BUY,
                                maxBuyAmount,
                                this::refreshAfterTrade);
                    } else if (sellEnabled && this.shopManager.isSellClick(event.getClick())) {
                        this.shopManager.executeTrade(
                                this.player,
                                this.shop,
                                TradeAction.SELL,
                                maxSellAmount,
                                this::refreshAfterTrade);
                    }
                } : null);
    }

    private void setCustomButton() {
        int maxBuyAmount = this.shopManager.computeMaxTradeAmount(this.player, this.shop, TradeAction.BUY);
        int maxSellAmount = this.shopManager.computeMaxTradeAmount(this.player, this.shop, TradeAction.SELL);
        boolean buyEnabled = maxBuyAmount > 0;
        boolean sellEnabled = maxSellAmount > 0;
        setItem(
                CUSTOM_SLOT,
                createItem(
                        Material.NAME_TAG,
                        getPlugin().getMessageConfig().getMessage(
                                buyEnabled || sellEnabled ? "shop.gui.custom.name" : "shop.gui.custom.disabled-name"),
                        List.of(getPlugin().getMessageConfig().getMessage(
                                buyEnabled || sellEnabled ? "shop.gui.custom.lore" : "shop.gui.custom.disabled-lore"))),
                buyEnabled || sellEnabled ? event -> getPlugin().getAnvilGuiManager().openNumberInput(
                        this.player,
                        getPlugin().getMessageConfig().getMessage("shop.gui.custom.title"),
                        value -> this.shopManager.executeTrade(
                                this.player,
                                this.shop,
                                this.shopManager.isBuyClick(event.getClick()) ? TradeAction.BUY : TradeAction.SELL,
                                Math.max(0, (int) Math.floor(value)),
                                this::refreshAfterTrade)) : null);
    }

    private void refreshAfterTrade() {
        refresh();
    }

    private String formatPrice(Double unitPrice) {
        if (unitPrice == null) {
            return "-";
        }
        return CurrencyFormatter.format(getPlugin(), unitPrice);
    }

    private String formatTotal(Double unitPrice, int amount) {
        if (unitPrice == null) {
            return "-";
        }
        return CurrencyFormatter.format(getPlugin(), unitPrice * amount);
    }
}
