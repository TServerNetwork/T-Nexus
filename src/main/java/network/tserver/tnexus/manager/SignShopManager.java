package network.tserver.tnexus.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.luckperms.api.LuckPerms;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.database.repository.SignShopRepository;
import network.tserver.tnexus.database.repository.TransactionRepository;
import network.tserver.tnexus.database.repository.TransactionRepository.AuditRecord;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import network.tserver.tnexus.gui.shop.SignShopBrowseGui;
import network.tserver.tnexus.gui.shop.SignShopDeleteGui;
import network.tserver.tnexus.gui.shop.SignShopEditGui;
import network.tserver.tnexus.util.BlockPosition;
import network.tserver.tnexus.util.CurrencyFormatter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * Coordinates SignShop persistence, sign rendering, linking, and trade execution.
 */
public final class SignShopManager {

    private static final String DEFAULT_UNLINKED_NAME = "Unlinked";
    private static final String DEFAULT_NOTE = "";
    private static final String SHOP_PERMISSION = "tnexus.shop.use";
    private static final String PLAYER_SHOP_PERMISSION = "tnexus.shop.player";
    private static final String ADMIN_PERMISSION = "tnexus.shop.admin";
    private static final String BYPASS_BAN_PERMISSION = "tnexus.shop.bypass.ban";
    private static final String SHOP_LIMIT_META_KEY = "tnexus.shop.limit";
    private static final int DEFAULT_PLAYER_SHOP_LIMIT = 5;

    private final TNexus plugin;
    private final EconomyManager economyManager;
    private final TransactionRepository transactionRepository;
    private final SignShopRepository signShopRepository;
    private final Map<Long, SignShop> shopsById;
    private final Map<BlockPosition, Long> signIndex;
    private final Map<BlockPosition, Set<Long>> chestIndex;
    private final Map<UUID, LinkSession> linkSessions;
    private final Map<UUID, Integer> pendingPlayerShopCreations;

    /**
     * Creates a new shop manager.
     *
     * @param plugin plugin instance
     */
    public SignShopManager(TNexus plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economyManager = plugin.getEconomyManager();
        this.transactionRepository = new TransactionRepository(plugin.getDatabaseManager());
        this.signShopRepository = new SignShopRepository(plugin.getDatabaseManager());
        this.shopsById = new ConcurrentHashMap<>();
        this.signIndex = new ConcurrentHashMap<>();
        this.chestIndex = new ConcurrentHashMap<>();
        this.linkSessions = new ConcurrentHashMap<>();
        this.pendingPlayerShopCreations = new ConcurrentHashMap<>();
    }

    /**
     * Loads persisted shops into memory.
     */
    public void initialize() {
        this.signShopRepository.loadAll()
                .whenComplete((shops, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        this.plugin.getLogger().log(Level.SEVERE, "Failed to load SignShop cache.", throwable);
                        return;
                    }
                    clearCache();
                    for (SignShop shop : shops) {
                        cacheShop(shop);
                        refreshShopDisplay(shop);
                    }
                }));
    }

    /**
     * Returns the tracked shop for a sign block.
     *
     * @param signBlock sign block
     * @return tracked shop or {@code null}
     */
    public @Nullable SignShop getShop(Block signBlock) {
        Long shopId = this.signIndex.get(BlockPosition.from(signBlock));
        return shopId == null ? null : this.shopsById.get(shopId);
    }

    /**
     * Creates a new sign shop from a sign placement.
     *
     * @param player placing player
     * @param signBlock sign block
     * @param type shop type
     * @param note sign note
     * @param initialChest linked chest, when present
     * @param templateItem template item, when present
     * @return created shop or {@code null} when validation fails
     */
    public @Nullable SignShop createShop(
            Player player,
            Block signBlock,
            ShopType type,
            String note,
            @Nullable Block initialChest,
            @Nullable ItemStack templateItem) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(signBlock, "signBlock");
        Objects.requireNonNull(type, "type");
        ItemStack resolvedTemplateItem = resolveCreationTemplateItem(type, initialChest, templateItem);

        if (type == ShopType.PLAYER
                && !player.hasPermission(PLAYER_SHOP_PERMISSION)
                && !player.hasPermission(ADMIN_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(player, "general.no-permission");
            return null;
        }
        if (type == ShopType.SERVER && !player.hasPermission(SHOP_PERMISSION) && !player.hasPermission(ADMIN_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(player, "general.no-permission");
            return null;
        }
        if (type == ShopType.SERVER && !player.hasPermission(ADMIN_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(player, "general.no-permission");
            return null;
        }
        if (type == ShopType.SERVER && (initialChest == null || resolvedTemplateItem == null)) {
            this.plugin.getMessageConfig().sendMessage(player, "shop.create.server-requires-chest");
            return null;
        }
        if (resolvedTemplateItem != null
                && isBannedMaterial(resolvedTemplateItem.getType())
                && !player.hasPermission(BYPASS_BAN_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(
                    player,
                    "shop.create.banned-material",
                    resolvedTemplateItem.getType().name());
            return null;
        }
        if (type == ShopType.PLAYER) {
            int shopLimit = getPlayerShopLimit(player);
            int currentCount = getPlayerShopCount(player.getUniqueId());
            if (currentCount >= shopLimit) {
                this.plugin.getMessageConfig().sendMessage(
                        player,
                        "shop.create.limit-reached",
                        currentCount,
                        shopLimit);
                return null;
            }
            this.pendingPlayerShopCreations.merge(player.getUniqueId(), 1, Integer::sum);
        }

        ItemStack normalizedItem = normalizeTemplateItem(resolvedTemplateItem);
        BlockPosition chestPosition = type == ShopType.PLAYER && initialChest != null ? BlockPosition.from(initialChest) : null;
        SignShop shop = new SignShop(
                0L,
                type,
                player.getUniqueId(),
                player.getName(),
                BlockPosition.from(signBlock),
                chestPosition,
                normalizedItem,
                normalizedItem == null ? DEFAULT_UNLINKED_NAME : resolveItemName(normalizedItem),
                null,
                null,
                note == null ? DEFAULT_NOTE : sanitizeNote(note),
                true);
        applySignWax(signBlock, true);
        applySignLines(shop, RenderedAvailability.unavailable());
        this.signShopRepository.insert(shop)
                .whenComplete((shopId, throwable) -> runSync(() -> {
                    if (type == ShopType.PLAYER) {
                        decrementPendingPlayerShopCreation(player.getUniqueId());
                    }
                    if (throwable != null) {
                        this.plugin.getLogger().log(Level.SEVERE, "Failed to persist SignShop.", throwable);
                        return;
                    }
                    shop.setId(shopId);
                    cacheShop(shop);
                    refreshShopDisplay(shop);
                    this.plugin.getMessageConfig().sendMessage(player, "shop.create.success");
                    if (shop.getType() == ShopType.PLAYER && shop.getLinkedChestPosition() == null) {
                        this.plugin.getMessageConfig().sendMessage(player, "shop.create.link-guidance");
                    }
                }));
        return shop;
    }

    /**
     * Deletes a sign shop and updates persistence.
     *
     * @param shop target shop
     */
    public void deleteShop(SignShop shop) {
        Objects.requireNonNull(shop, "shop");
        removeCachedShop(shop);
        this.signShopRepository.delete(shop.getId())
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        this.plugin.getLogger().log(Level.SEVERE, "Failed to delete SignShop.", throwable);
                    }
                });
        Block signBlock = shop.getSignPosition().resolveBlock(this.plugin.getServer());
        if (signBlock != null && signBlock.getState() instanceof Sign sign) {
            for (int lineIndex = 0; lineIndex < 4; lineIndex++) {
                sign.getSide(Side.FRONT).line(lineIndex, LegacyComponentSerializer.legacySection().deserialize(""));
            }
            sign.setWaxed(false);
            sign.update(true, false);
        }
    }

    /**
     * Returns whether a player may modify a shop.
     *
     * @param player player
     * @param shop shop
     * @return {@code true} when the player may modify the shop
     */
    public boolean canModify(Player player, SignShop shop) {
        return player.hasPermission(ADMIN_PERMISSION) || player.getUniqueId().equals(shop.getOwnerUuid());
    }

    /**
     * Starts command-driven link mode.
     *
     * @param player player
     */
    public void beginLinkMode(Player player) {
        this.linkSessions.put(player.getUniqueId(), new LinkSession(null, true));
        this.plugin.getMessageConfig().sendMessage(player, "shop.link.mode-started");
    }

    /**
     * Returns the configured link tool item.
     *
     * @return link tool item
     */
    public ItemStack createLinkTool() {
        Material material = Material.matchMaterial(
                this.plugin.getConfigManager().getString("tnexus.shop.link-tool.material", "STICK"));
        if (material == null) {
            material = Material.STICK;
        }

        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes(
                    '&',
                    this.plugin.getMessageConfig().getMessage("shop.link.tool-name")));
            meta.setLore(List.of(this.plugin.getMessageConfig().getMessage("shop.link.tool-lore")));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            itemStack.setItemMeta(meta);
        }
        itemStack.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        return itemStack;
    }

    /**
     * Returns whether the given item behaves as the link tool.
     *
     * @param itemStack item stack
     * @return {@code true} when it is the configured link tool
     */
    public boolean isLinkTool(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }

        Material configured = Material.matchMaterial(
                this.plugin.getConfigManager().getString("tnexus.shop.link-tool.material", "STICK"));
        if (configured == null || itemStack.getType() != configured) {
            return false;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        String configuredName = ChatColor.translateAlternateColorCodes(
                '&',
                this.plugin.getMessageConfig().getMessage("shop.link.tool-name"));
        return configuredName.equals(meta.getDisplayName());
    }

    /**
     * Handles right-click linking interactions.
     *
     * @param player player
     * @param clickedBlock clicked block
     * @param usingLinkTool whether the click used the link tool
     * @return {@code true} when the interaction was handled
     */
    public boolean handleLinkInteraction(Player player, Block clickedBlock, boolean usingLinkTool) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(clickedBlock, "clickedBlock");

        LinkSession session = this.linkSessions.get(player.getUniqueId());
        if (!usingLinkTool && session == null) {
            return false;
        }

        if (isShopSign(clickedBlock)) {
            SignShop shop = getShop(clickedBlock);
            if (shop == null || shop.getType() != ShopType.PLAYER) {
                cancelLinkSessionOnError(player.getUniqueId(), session);
                this.plugin.getMessageConfig().sendMessage(player, "shop.link.not-player-shop");
                return true;
            }
            if (!canModify(player, shop)) {
                cancelLinkSessionOnError(player.getUniqueId(), session);
                this.plugin.getMessageConfig().sendMessage(player, "general.no-permission");
                return true;
            }
            this.linkSessions.put(player.getUniqueId(), new LinkSession(shop.getId(), session != null && session.commandMode()));
            this.plugin.getMessageConfig().sendMessage(player, "shop.link.sign-selected");
            return true;
        }

        if (!isChest(clickedBlock)) {
            return false;
        }

        if (session == null || session.shopId() == null) {
            cancelLinkSessionOnError(player.getUniqueId(), session);
            this.plugin.getMessageConfig().sendMessage(player, "shop.link.select-sign-first");
            return true;
        }

        SignShop shop = this.shopsById.get(session.shopId());
        if (shop == null) {
            this.linkSessions.remove(player.getUniqueId());
            this.plugin.getMessageConfig().sendMessage(player, "shop.link.shop-not-found");
            return true;
        }

        ItemStack templateItem = findFirstTemplateItem(clickedBlock);
        if (templateItem == null) {
            cancelLinkSessionOnError(player.getUniqueId(), session);
            this.plugin.getMessageConfig().sendMessage(player, "shop.link.chest-empty");
            return true;
        }
        if (isBannedMaterial(templateItem.getType()) && !player.hasPermission(BYPASS_BAN_PERMISSION)) {
            cancelLinkSessionOnError(player.getUniqueId(), session);
            this.plugin.getMessageConfig().sendMessage(player, "shop.create.banned-material", templateItem.getType().name());
            return true;
        }

        shop.setLinkedChestPosition(BlockPosition.from(clickedBlock));
        ItemStack normalizedItem = normalizeTemplateItem(templateItem);
        shop.setItemStack(normalizedItem);
        shop.setItemName(resolveItemName(normalizedItem));
        this.linkSessions.remove(player.getUniqueId());
        reindexChest(shop);
        this.signShopRepository.update(shop)
                .whenComplete((ignored, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        this.plugin.getLogger().log(Level.SEVERE, "Failed to persist SignShop link.", throwable);
                        this.plugin.getMessageConfig().sendMessage(player, "shop.link.failed");
                        return;
                    }
                    refreshShopDisplay(shop);
                    this.plugin.getMessageConfig().sendMessage(player, "shop.link.success");
                }));
        return true;
    }

    /**
     * Opens the browse GUI for a player.
     *
     * @param player target player
     * @param shop target shop
     */
    public void openBrowseGui(Player player, SignShop shop) {
        new SignShopBrowseGui(this.plugin, this, player, shop).open();
    }

    /**
     * Opens the edit GUI for a player.
     *
     * @param player target player
     * @param shop target shop
     */
    public void openEditGui(Player player, SignShop shop) {
        new SignShopEditGui(this.plugin, this, player, shop).open();
    }

    /**
     * Opens the buyer-facing preview GUI for a shop owner or admin.
     *
     * @param player target player
     * @param shop target shop
     */
    public void openPreviewGui(Player player, SignShop shop) {
        openBrowseGui(player, shop);
    }

    /**
     * Opens the delete confirmation GUI.
     *
     * @param player target player
     * @param shop target shop
     */
    public void openDeleteGui(Player player, SignShop shop) {
        new SignShopDeleteGui(this.plugin, this, player, shop).open();
    }

    /**
     * Refreshes rendered sign text for a shop.
     *
     * @param shop target shop
     */
    public void refreshShopDisplay(SignShop shop) {
        if (!shop.isEnabled()) {
            applySignLines(shop, RenderedAvailability.disabled());
            return;
        }

        SyncAvailability syncAvailability = evaluateSyncAvailability(shop);
        syncAvailability.ownerBalanceCheck().whenComplete((ownerHasFunds, throwable) -> runSync(() -> {
            if (throwable != null) {
                this.plugin.getLogger().log(Level.WARNING, "Failed to resolve SignShop owner balance.", throwable);
                applySignLines(shop, RenderedAvailability.unavailable());
                return;
            }
            applySignLines(shop, syncAvailability.resolve(ownerHasFunds));
        }));
    }

    /**
     * Updates shop prices and redraws the sign.
     *
     * @param player editor
     * @param shop target shop
     * @param buyPrice buy price or null
     * @param sellPrice sell price or null
     */
    public void updatePrices(Player player, SignShop shop, @Nullable Double buyPrice, @Nullable Double sellPrice) {
        shop.setBuyPrice(normalizePrice(buyPrice));
        shop.setSellPrice(normalizePrice(sellPrice));
        this.signShopRepository.update(shop)
                .whenComplete((ignored, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        this.plugin.getLogger().log(Level.SEVERE, "Failed to update SignShop prices.", throwable);
                        this.plugin.getMessageConfig().sendMessage(player, "shop.edit.price.failed");
                        return;
                    }
                    refreshShopDisplay(shop);
                    this.plugin.getMessageConfig().sendMessage(player, "shop.edit.price.updated");
                }));
    }

    /**
     * Updates the rendered sign note and persists the change.
     *
     * @param player editor
     * @param shop target shop
     * @param note updated note
     */
    public void updateNote(Player player, SignShop shop, String note) {
        shop.setNote(sanitizeNote(note));
        this.signShopRepository.update(shop)
                .whenComplete((ignored, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        this.plugin.getLogger().log(Level.SEVERE, "Failed to update SignShop note.", throwable);
                        this.plugin.getMessageConfig().sendMessage(player, "shop.edit.note.failed");
                        return;
                    }
                    refreshShopDisplay(shop);
                    this.plugin.getMessageConfig().sendMessage(player, "shop.edit.note.updated");
                    openEditGui(player, shop);
                }));
    }

    /**
     * Toggles the enabled state for a shop.
     *
     * @param player editor
     * @param shop target shop
     */
    public void toggleEnabled(Player player, SignShop shop) {
        shop.setEnabled(!shop.isEnabled());
        this.signShopRepository.update(shop)
                .whenComplete((ignored, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        this.plugin.getLogger().log(Level.SEVERE, "Failed to toggle SignShop.", throwable);
                        this.plugin.getMessageConfig().sendMessage(player, "shop.edit.toggle.failed");
                        return;
                    }
                    refreshShopDisplay(shop);
                    this.plugin.getMessageConfig().sendMessage(
                            player,
                            shop.isEnabled() ? "shop.edit.toggle.enabled" : "shop.edit.toggle.disabled");
                    openEditGui(player, shop);
                }));
    }

    /**
     * Opens the note editor for a shop.
     *
     * @param player editor
     * @param shop target shop
     */
    public void openNoteEditor(Player player, SignShop shop) {
        this.plugin.getAnvilGuiManager().openInput(
                player,
                this.plugin.getMessageConfig().getMessage("shop.edit.note.title"),
                shop.getNote(),
                note -> updateNote(player, shop, note));
    }

    /**
     * Executes a shop trade for a viewer.
     *
     * @param player actor
     * @param shop target shop
     * @param action trade direction
     * @param amount requested amount
     */
    public void executeTrade(Player player, SignShop shop, TradeAction action, int amount) {
        executeTrade(player, shop, action, amount, () -> {
        });
    }

    /**
     * Executes a trade and invokes a callback after a successful completion on the main thread.
     *
     * @param player actor
     * @param shop target shop
     * @param action trade direction
     * @param amount requested amount
     * @param successCallback callback invoked after success
     */
    public void executeTrade(Player player, SignShop shop, TradeAction action, int amount, Runnable successCallback) {
        if (amount <= 0) {
            this.plugin.getMessageConfig().sendMessage(player, "shop.trade.invalid-amount");
            return;
        }
        TradeEligibility eligibility = evaluateTradeEligibility(player, shop, action);
        if (!eligibility.available()) {
            this.plugin.getMessageConfig().sendMessage(player, eligibility.unavailableMessageKey());
            return;
        }

        ItemStack template = Objects.requireNonNull(eligibility.template(), "trade template");
        int maxAmount = eligibility.maxAmount();
        int finalAmount = Math.min(amount, maxAmount);
        if (finalAmount <= 0) {
            this.plugin.getMessageConfig().sendMessage(player, eligibility.unavailableMessageKey());
            return;
        }
        if (amount > finalAmount) {
            this.plugin.getMessageConfig().sendMessage(
                    player,
                    Objects.requireNonNull(eligibility.adjustmentMessageKey(), "trade adjustment message"),
                    finalAmount);
        }
        double unitPrice = action == TradeAction.BUY ? nullablePrice(shop.getBuyPrice()) : nullablePrice(shop.getSellPrice());
        if (unitPrice <= 0.0D) {
            this.plugin.getMessageConfig().sendMessage(player, "shop.trade.unavailable");
            return;
        }
        double totalPrice = unitPrice * finalAmount;

        switch (action) {
            case BUY -> executeBuy(player, shop, template, finalAmount, totalPrice, successCallback);
            case SELL -> executeSell(player, shop, template, finalAmount, totalPrice, successCallback);
        }
    }

    /**
     * Returns the maximum immediately-tradable amount for the player.
     *
     * @param player viewer
     * @param shop target shop
     * @param action trade direction
     * @return maximum amount
     */
    public int computeMaxTradeAmount(Player player, SignShop shop, TradeAction action) {
        return evaluateTradeEligibility(player, shop, action).maxAmount();
    }

    private TradeEligibility evaluateTradeEligibility(Player player, SignShop shop, TradeAction action) {
        ItemStack template = shop.getItemStack();
        if (template == null) {
            return TradeEligibility.unavailable("shop.trade.unavailable");
        }
        if (!shop.isEnabled()) {
            return TradeEligibility.unavailable("shop.trade.unavailable-disabled");
        }

        return switch (action) {
            case BUY -> evaluateBuyEligibility(player, shop, template);
            case SELL -> evaluateSellEligibility(player, shop, template);
        };
    }

    private TradeEligibility evaluateBuyEligibility(Player player, SignShop shop, ItemStack template) {
        Double unitPrice = shop.getBuyPrice();
        if (unitPrice == null) {
            return TradeEligibility.unavailable("shop.trade.unavailable");
        }

        List<TradeConstraint> constraints = new ArrayList<>();
        constraints.add(new TradeConstraint(
                calculateInventoryFit(player.getInventory(), template),
                "shop.trade.adjusted.inv-max",
                "shop.trade.unavailable"));
        constraints.add(new TradeConstraint(
                computeAffordableAmount(this.economyManager.getBalanceNow(player.getUniqueId()), unitPrice),
                "shop.trade.adjusted.balance",
                "shop.trade.insufficient-funds"));
        if (shop.getType() == ShopType.PLAYER) {
            Inventory chestInventory = resolveLinkedChestInventory(shop);
            if (chestInventory == null) {
                return TradeEligibility.unavailable("shop.trade.unavailable");
            }
            constraints.add(new TradeConstraint(
                    countMatchingItems(chestInventory, template),
                    "shop.trade.adjusted.stock",
                    "shop.trade.out-of-stock"));
        }
        return resolveTradeEligibility(template, constraints);
    }

    private TradeEligibility evaluateSellEligibility(Player player, SignShop shop, ItemStack template) {
        if (shop.getType() == ShopType.SERVER && !isServerShopSellToVoidEnabled()) {
            return TradeEligibility.unavailable("shop.trade.unavailable");
        }

        Double unitPrice = shop.getSellPrice();
        if (unitPrice == null) {
            return TradeEligibility.unavailable("shop.trade.unavailable");
        }

        List<TradeConstraint> constraints = new ArrayList<>();
        constraints.add(new TradeConstraint(
                countMatchingItems(player.getInventory(), template),
                "shop.trade.adjusted.player-items",
                "shop.trade.unavailable"));
        if (shop.getType() == ShopType.PLAYER) {
            Inventory chestInventory = resolveLinkedChestInventory(shop);
            if (chestInventory == null) {
                return TradeEligibility.unavailable("shop.trade.unavailable");
            }
            constraints.add(new TradeConstraint(
                    calculateInventoryFit(chestInventory, template),
                    "shop.trade.adjusted.capacity",
                    "shop.trade.chest-full"));
            constraints.add(new TradeConstraint(
                    computeAffordableAmount(this.economyManager.getBalanceNow(shop.getOwnerUuid()), unitPrice),
                    "shop.trade.adjusted.owner-funds",
                    "shop.trade.owner-funds"));
        }
        return resolveTradeEligibility(template, constraints);
    }

    private TradeEligibility resolveTradeEligibility(ItemStack template, List<TradeConstraint> constraints) {
        int maxAmount = Integer.MAX_VALUE;
        TradeConstraint limitingConstraint = null;
        for (TradeConstraint constraint : constraints) {
            if (constraint.amount() < maxAmount) {
                maxAmount = constraint.amount();
                limitingConstraint = constraint;
            }
        }
        if (limitingConstraint == null || maxAmount <= 0) {
            String messageKey = limitingConstraint == null
                    ? "shop.trade.unavailable"
                    : limitingConstraint.unavailableMessageKey();
            return TradeEligibility.unavailable(messageKey);
        }
        return new TradeEligibility(
                maxAmount,
                limitingConstraint.adjustmentMessageKey(),
                null,
                true,
                template);
    }

    /**
     * Returns the current available stock for display in the browse GUI.
     *
     * @param shop target shop
     * @return available stock, or {@link Integer#MAX_VALUE} for server shops
     */
    public int getCurrentStock(SignShop shop) {
        if (shop.getType() == ShopType.SERVER) {
            return Integer.MAX_VALUE;
        }
        ItemStack template = shop.getItemStack();
        if (template == null) {
            return 0;
        }
        Inventory chestInventory = resolveLinkedChestInventory(shop);
        return chestInventory == null ? 0 : countMatchingItems(chestInventory, template);
    }

    /**
     * Returns the current available sell capacity for display in the browse GUI.
     *
     * @param shop target shop
     * @return available capacity, or {@link Integer#MAX_VALUE} for server shops
     */
    public int getCurrentCapacity(SignShop shop) {
        if (shop.getType() == ShopType.SERVER) {
            return Integer.MAX_VALUE;
        }
        ItemStack template = shop.getItemStack();
        if (template == null) {
            return 0;
        }
        Inventory chestInventory = resolveLinkedChestInventory(shop);
        return chestInventory == null ? 0 : calculateInventoryFit(chestInventory, template);
    }

    /**
     * Handles inventory-based status refreshes after linked chest changes.
     *
     * @param blockPosition changed block position
     */
    public void refreshByChest(BlockPosition blockPosition) {
        Set<Long> shopIds = this.chestIndex.get(blockPosition);
        if (shopIds == null || shopIds.isEmpty()) {
            return;
        }

        for (Long shopId : shopIds) {
            SignShop shop = this.shopsById.get(shopId);
            if (shop != null) {
                refreshShopDisplay(shop);
            }
        }
    }

    /**
     * Returns whether the block is linked as a PlayerShop chest.
     *
     * @param block target block
     * @return {@code true} when the block is tracked as a linked chest
     */
    public boolean isLinkedChest(Block block) {
        Objects.requireNonNull(block, "block");
        Set<Long> shopIds = this.chestIndex.get(BlockPosition.from(block));
        return shopIds != null && !shopIds.isEmpty();
    }

    /**
     * Returns whether the player may directly open a linked shop chest.
     *
     * @param player player attempting access
     * @param chestBlock target chest block
     * @return {@code true} when every linked chest entry is modifiable by the player
     */
    public boolean canAccessLinkedChest(Player player, Block chestBlock) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(chestBlock, "chestBlock");

        Set<Long> shopIds = this.chestIndex.get(BlockPosition.from(chestBlock));
        if (shopIds == null || shopIds.isEmpty()) {
            return true;
        }

        for (Long shopId : shopIds) {
            SignShop shop = this.shopsById.get(shopId);
            if (shop != null && !canModify(player, shop)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether a material is banned for shop items.
     *
     * @param material material to check
     * @return {@code true} when banned
     */
    public boolean isBannedMaterial(Material material) {
        Material linkToolMaterial = Material.matchMaterial(
                this.plugin.getConfigManager().getString("tnexus.shop.link-tool.material", "STICK"));
        if (linkToolMaterial != null && material == linkToolMaterial) {
            return true;
        }

        List<String> bannedMaterials = this.plugin.getConfig().getStringList("tnexus.shop.banned-materials");
        if (bannedMaterials.isEmpty()) {
            return false;
        }
        String materialName = material.name().toUpperCase(Locale.ROOT);
        return bannedMaterials.stream()
                .map(name -> name.toUpperCase(Locale.ROOT))
                .anyMatch(materialName::equals);
    }

    /**
     * Resolves a first non-air item from a chest block.
     *
     * @param chestBlock chest block
     * @return template item or {@code null}
     */
    public @Nullable ItemStack findFirstTemplateItem(Block chestBlock) {
        Inventory inventory = resolveContainerInventory(chestBlock);
        if (inventory == null) {
            return null;
        }

        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && itemStack.getType() != Material.AIR) {
                return normalizeTemplateItem(itemStack);
            }
        }
        return null;
    }

    /**
     * Returns all linked chest positions represented by an inventory holder.
     *
     * @param inventory inventory to inspect
     * @return linked block positions
     */
    public List<BlockPosition> getChestPositions(Inventory inventory) {
        List<BlockPosition> positions = new ArrayList<>();
        Object holder = inventory.getHolder();
        if (holder instanceof org.bukkit.inventory.BlockInventoryHolder blockInventoryHolder) {
            positions.add(BlockPosition.from(blockInventoryHolder.getBlock()));
        }
        if (holder instanceof org.bukkit.block.DoubleChest doubleChest) {
            if (doubleChest.getLeftSide(true) instanceof org.bukkit.inventory.BlockInventoryHolder leftHolder) {
                positions.add(BlockPosition.from(leftHolder.getBlock()));
            }
            if (doubleChest.getRightSide(true) instanceof org.bukkit.inventory.BlockInventoryHolder rightHolder) {
                positions.add(BlockPosition.from(rightHolder.getBlock()));
            }
        }
        return positions;
    }

    private void executeBuy(
            Player player,
            SignShop shop,
            ItemStack template,
            int amount,
            double totalPrice,
            Runnable successCallback) {
        this.economyManager.withdraw(player.getUniqueId(), totalPrice)
                .thenCompose(withdrawn -> {
                    if (!withdrawn) {
                        return CompletableFuture.completedFuture(TradeResult.insufficientFunds());
                    }
                    if (shop.getType() == ShopType.PLAYER) {
                        return this.economyManager.deposit(shop.getOwnerUuid(), totalPrice)
                                .thenApply(deposited -> deposited ? TradeResult.success() : TradeResult.failed());
                    }
                    return CompletableFuture.completedFuture(TradeResult.success());
                })
                .whenComplete((result, throwable) -> runSync(() -> {
                    if (throwable != null || result == null) {
                        this.plugin.getMessageConfig().sendMessage(player, "shop.trade.failed");
                        return;
                    }
                    if (result.status() == TradeResultStatus.INSUFFICIENT_FUNDS) {
                        this.plugin.getMessageConfig().sendMessage(player, "shop.trade.insufficient-funds");
                        return;
                    }
                    if (result.status() != TradeResultStatus.SUCCESS) {
                        this.plugin.getMessageConfig().sendMessage(player, "shop.trade.failed");
                        return;
                    }

                    if (!finishBuyOnMainThread(player, shop, template, amount, totalPrice)) {
                        rollbackBuy(shop, player, totalPrice);
                        this.plugin.getMessageConfig().sendMessage(
                                player,
                                evaluateTradeEligibility(player, shop, TradeAction.BUY).unavailableMessageKey());
                        return;
                    }
                    recordTradeAudit(player, shop, TransactionType.SHOP_BUY, totalPrice, amount, "Bought from shop");
                    refreshShopDisplay(shop);
                    successCallback.run();
                    this.plugin.getMessageConfig().sendMessage(
                            player,
                            "shop.trade.buy-success",
                            amount,
                            shop.getItemName(),
                            CurrencyFormatter.format(this.plugin, totalPrice));
                }));
    }

    private void executeSell(
            Player player,
            SignShop shop,
            ItemStack template,
            int amount,
            double totalPrice,
            Runnable successCallback) {
        if (shop.getType() == ShopType.SERVER && !isServerShopSellToVoidEnabled()) {
            this.plugin.getMessageConfig().sendMessage(player, "shop.trade.unavailable");
            return;
        }
        CompletableFuture<Boolean> ownerFundsFuture = shop.getType() == ShopType.PLAYER
                ? this.economyManager.has(shop.getOwnerUuid(), totalPrice)
                : CompletableFuture.completedFuture(true);
        ownerFundsFuture.whenComplete((ownerHasFunds, throwable) -> runSync(() -> {
            if (throwable != null || ownerHasFunds == null) {
                this.plugin.getMessageConfig().sendMessage(player, "shop.trade.failed");
                return;
            }
            if (!ownerHasFunds) {
                this.plugin.getMessageConfig().sendMessage(player, "shop.trade.owner-funds");
                return;
            }
            if (!finishSellInventoryStage(player, shop, template, amount)) {
                this.plugin.getMessageConfig().sendMessage(
                        player,
                        evaluateTradeEligibility(player, shop, TradeAction.SELL).unavailableMessageKey());
                return;
            }
            CompletableFuture<Boolean> moneyFuture = this.economyManager.deposit(player.getUniqueId(), totalPrice);
            if (shop.getType() == ShopType.PLAYER) {
                moneyFuture = this.economyManager.withdraw(shop.getOwnerUuid(), totalPrice)
                        .thenCompose(withdrawn -> {
                            if (!withdrawn) {
                                revertSellInventoryStage(player, shop, template, amount);
                                return CompletableFuture.completedFuture(false);
                            }
                            return this.economyManager.deposit(player.getUniqueId(), totalPrice)
                                    .thenApply(deposited -> {
                                        if (!deposited) {
                                            this.economyManager.deposit(shop.getOwnerUuid(), totalPrice);
                                        }
                                        return deposited;
                                    });
                        });
            }
            moneyFuture.whenComplete((paid, moneyThrowable) -> runSync(() -> {
                if (moneyThrowable != null || paid == null || !paid) {
                    revertSellInventoryStage(player, shop, template, amount);
                    this.plugin.getMessageConfig().sendMessage(player, "shop.trade.failed");
                    return;
                }
                recordTradeAudit(player, shop, TransactionType.SHOP_SELL, totalPrice, amount, "Sold to shop");
                refreshShopDisplay(shop);
                successCallback.run();
                this.plugin.getMessageConfig().sendMessage(
                        player,
                        "shop.trade.sell-success",
                        amount,
                        shop.getItemName(),
                        CurrencyFormatter.format(this.plugin, totalPrice));
            }));
        }));
    }

    private void rollbackBuy(SignShop shop, Player player, double totalPrice) {
        this.economyManager.deposit(player.getUniqueId(), totalPrice);
        if (shop.getType() == ShopType.PLAYER) {
            this.economyManager.withdraw(shop.getOwnerUuid(), totalPrice);
        }
    }

    private boolean finishBuyOnMainThread(Player player, SignShop shop, ItemStack template, int amount, double totalPrice) {
        Inventory playerInventory = player.getInventory();
        if (calculateInventoryFit(playerInventory, template) < amount) {
            return false;
        }
        if (shop.getType() == ShopType.PLAYER) {
            Inventory chestInventory = resolveLinkedChestInventory(shop);
            if (chestInventory == null || countMatchingItems(chestInventory, template) < amount) {
                return false;
            }
            removeMatchingItems(chestInventory, template, amount);
        }
        addMatchingItems(playerInventory, template, amount);
        return true;
    }

    private boolean finishSellInventoryStage(Player player, SignShop shop, ItemStack template, int amount) {
        Inventory playerInventory = player.getInventory();
        if (countMatchingItems(playerInventory, template) < amount) {
            return false;
        }
        if (shop.getType() == ShopType.PLAYER) {
            Inventory chestInventory = resolveLinkedChestInventory(shop);
            if (chestInventory == null || calculateInventoryFit(chestInventory, template) < amount) {
                return false;
            }
            addMatchingItems(chestInventory, template, amount);
        }
        removeMatchingItems(playerInventory, template, amount);
        return true;
    }

    private void revertSellInventoryStage(Player player, SignShop shop, ItemStack template, int amount) {
        addMatchingItems(player.getInventory(), template, amount);
        if (shop.getType() == ShopType.PLAYER) {
            Inventory chestInventory = resolveLinkedChestInventory(shop);
            if (chestInventory != null) {
                removeMatchingItems(chestInventory, template, amount);
            }
        }
    }

    private void recordTradeAudit(
            Player player,
            SignShop shop,
            TransactionType transactionType,
            double totalPrice,
            int amount,
            String description) {
        this.economyManager.getBalance(player.getUniqueId())
                .thenCompose(balanceAfter -> this.transactionRepository.insert(new AuditRecord(
                        player.getUniqueId(),
                        transactionType,
                        totalPrice,
                        balanceAfter,
                        description + " x" + amount + " (" + shop.getItemName() + ")",
                        shop.getOwnerUuid())))
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        this.plugin.getLogger().log(Level.WARNING, "Failed to record SignShop audit.", throwable);
                    }
                });
    }

    private Inventory resolveLinkedChestInventory(SignShop shop) {
        BlockPosition chestPosition = shop.getLinkedChestPosition();
        if (chestPosition == null) {
            return null;
        }
        Block chestBlock = chestPosition.resolveBlock(this.plugin.getServer());
        return chestBlock == null ? null : resolveContainerInventory(chestBlock);
    }

    private @Nullable Inventory resolveContainerInventory(Block block) {
        if (block.getState() instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    private int calculateInventoryFit(Inventory inventory, ItemStack template) {
        int capacity = 0;
        int maxStack = template.getMaxStackSize();
        for (ItemStack content : inventory.getStorageContents()) {
            if (content == null || content.getType() == Material.AIR) {
                capacity += maxStack;
                continue;
            }
            if (content.isSimilar(template)) {
                capacity += maxStack - content.getAmount();
            }
        }
        return capacity;
    }

    private int countMatchingItems(Inventory inventory, ItemStack template) {
        int total = 0;
        for (ItemStack content : inventory.getStorageContents()) {
            if (content != null && content.getType() != Material.AIR && content.isSimilar(template)) {
                total += content.getAmount();
            }
        }
        return total;
    }

    private void removeMatchingItems(Inventory inventory, ItemStack template, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack content = contents[index];
            if (content == null || content.getType() == Material.AIR || !content.isSimilar(template)) {
                continue;
            }
            int removeAmount = Math.min(remaining, content.getAmount());
            content.setAmount(content.getAmount() - removeAmount);
            if (content.getAmount() <= 0) {
                contents[index] = null;
            }
            remaining -= removeAmount;
        }
        inventory.setStorageContents(contents);
    }

    private void addMatchingItems(Inventory inventory, ItemStack template, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, template.getMaxStackSize());
            ItemStack stack = template.clone();
            stack.setAmount(stackAmount);
            Map<Integer, ItemStack> leftovers = inventory.addItem(stack);
            if (leftovers.isEmpty()) {
                remaining -= stackAmount;
                continue;
            }
            int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            remaining -= stackAmount - leftoverAmount;
            if (leftoverAmount == stackAmount) {
                break;
            }
        }
    }

    private SyncAvailability evaluateSyncAvailability(SignShop shop) {
        if (!shop.isEnabled()) {
            return SyncAvailability.unavailable();
        }
        if (shop.getItemStack() == null) {
            return SyncAvailability.unavailable();
        }
        if (shop.getType() == ShopType.SERVER) {
            boolean buySideAvailable = shop.getBuyPrice() != null;
            boolean sellSideAvailable = shop.getSellPrice() != null && isServerShopSellToVoidEnabled();
            return new SyncAvailability(buySideAvailable, sellSideAvailable, false, CompletableFuture.completedFuture(true));
        }

        Inventory chestInventory = resolveLinkedChestInventory(shop);
        if (chestInventory == null) {
            return SyncAvailability.unavailable();
        }

        ItemStack template = Objects.requireNonNull(shop.getItemStack(), "shop item");
        boolean buySideAvailable = shop.getBuyPrice() != null && countMatchingItems(chestInventory, template) > 0;
        boolean sellSideCapacity = shop.getSellPrice() != null && calculateInventoryFit(chestInventory, template) > 0;
        CompletableFuture<Boolean> ownerHasFunds = shop.getSellPrice() == null
                ? CompletableFuture.completedFuture(true)
                : this.economyManager.has(shop.getOwnerUuid(), shop.getSellPrice());
        return new SyncAvailability(buySideAvailable, sellSideCapacity, true, ownerHasFunds);
    }

    private void applySignLines(SignShop shop, RenderedAvailability availability) {
        Block signBlock = shop.getSignPosition().resolveBlock(this.plugin.getServer());
        if (!(signBlock != null && signBlock.getState() instanceof Sign sign)) {
            return;
        }

        String color = switch (availability.status()) {
            case AVAILABLE -> "&a";
            case UNAVAILABLE -> "&c";
            case DISABLED -> "&8";
        };
        String buyColor = resolveTradeDisplayColor(shop.getBuyPrice(), availability.buyAvailable());
        String sellColor = resolveTradeDisplayColor(shop.getSellPrice(), availability.sellAvailable());
        String label = shop.getType() == ShopType.SERVER ? "[ServerShop]" : "[Shop]";
        String buyValue = shop.getBuyPrice() == null ? "-" : trimPrice(shop.getBuyPrice());
        String sellValue = shop.getSellPrice() == null ? "-" : trimPrice(shop.getSellPrice());
        sign.getSide(Side.FRONT).line(0, LegacyComponentSerializer.legacyAmpersand().deserialize(color + label));
        sign.getSide(Side.FRONT).line(1, LegacyComponentSerializer.legacyAmpersand().deserialize("&f" + shop.getItemName()));
        sign.getSide(Side.FRONT).line(2, LegacyComponentSerializer.legacyAmpersand().deserialize(
                buyColor + "B " + buyValue + " &8| " + sellColor + "S " + sellValue));
        sign.getSide(Side.FRONT).line(3, LegacyComponentSerializer.legacyAmpersand().deserialize("&f" + shop.getNote()));
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private void applySignWax(Block signBlock, boolean waxed) {
        if (signBlock.getState() instanceof Sign sign) {
            sign.setWaxed(waxed);
            sign.update(true, false);
        }
    }

    private String resolveTradeDisplayColor(@Nullable Double price, boolean available) {
        if (price == null) {
            return "&7";
        }
        return available ? "&a" : "&c";
    }

    private void cacheShop(SignShop shop) {
        this.shopsById.put(shop.getId(), shop);
        this.signIndex.put(shop.getSignPosition(), shop.getId());
        reindexChest(shop);
    }

    private void reindexChest(SignShop shop) {
        for (Set<Long> shopIds : this.chestIndex.values()) {
            shopIds.remove(shop.getId());
        }
        BlockPosition chestPosition = shop.getLinkedChestPosition();
        if (chestPosition != null) {
            this.chestIndex.computeIfAbsent(chestPosition, ignored -> ConcurrentHashMap.newKeySet()).add(shop.getId());
        }
    }

    private void removeCachedShop(SignShop shop) {
        this.shopsById.remove(shop.getId());
        this.signIndex.remove(shop.getSignPosition());
        for (Set<Long> shopIds : this.chestIndex.values()) {
            shopIds.remove(shop.getId());
        }
    }

    private void clearCache() {
        this.shopsById.clear();
        this.signIndex.clear();
        this.chestIndex.clear();
    }

    private boolean isShopSign(Block block) {
        Material type = block.getType();
        return type.name().endsWith("_SIGN") || type.name().endsWith("_WALL_SIGN");
    }

    private boolean isChest(Block block) {
        return block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST;
    }

    /**
     * Finds an adjacent chest for sign placement.
     *
     * @param signBlock sign block
     * @return adjacent chest block or {@code null}
     */
    public @Nullable Block findAdjacentChest(Block signBlock) {
        for (BlockFace blockFace : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN)) {
            Block relative = signBlock.getRelative(blockFace);
            if (isChest(relative)) {
                return relative;
            }
        }
        return null;
    }

    /**
     * Opens the sequential price editor flow.
     *
     * @param player editor
     * @param shop target shop
     */
    public void openPriceFlow(Player player, SignShop shop) {
        this.plugin.getAnvilGuiManager().openNumberInput(
                player,
                this.plugin.getMessageConfig().getMessage("shop.edit.price.buy-title"),
                buyPrice -> this.plugin.getAnvilGuiManager().openNumberInput(
                        player,
                        this.plugin.getMessageConfig().getMessage("shop.edit.price.sell-title"),
                        sellPrice -> updatePrices(player, shop, buyPrice, sellPrice)));
    }

    /**
     * Returns the sign line-0 marker from a sign placement.
     *
     * @param rawLine raw line text
     * @return resolved shop type or {@code null}
     */
    public @Nullable ShopType resolveShopType(String rawLine) {
        String stripped = ChatColor.stripColor(rawLine == null ? "" : rawLine).trim();
        if ("[Shop]".equalsIgnoreCase(stripped)) {
            return ShopType.PLAYER;
        }
        if ("[ServerShop]".equalsIgnoreCase(stripped)) {
            return ShopType.SERVER;
        }
        return null;
    }

    /**
     * Returns whether a click type indicates buying.
     *
     * @param clickType click type
     * @return {@code true} for left clicks
     */
    public boolean isBuyClick(ClickType clickType) {
        return clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT;
    }

    /**
     * Returns whether a click type indicates selling.
     *
     * @param clickType click type
     * @return {@code true} for right clicks
     */
    public boolean isSellClick(ClickType clickType) {
        return clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT;
    }

    /**
     * Returns whether a sender may use shop actions.
     *
     * @param sender sender
     * @return {@code true} when allowed
     */
    public boolean canUseShops(CommandSender sender) {
        return sender.hasPermission(SHOP_PERMISSION)
                || sender.hasPermission(PLAYER_SHOP_PERMISSION)
                || sender.hasPermission(ADMIN_PERMISSION);
    }

    /**
     * Returns the current player shop limit for the given player.
     *
     * @param player target player
     * @return resolved shop limit
     */
    public int getPlayerShopLimit(Player player) {
        Objects.requireNonNull(player, "player");
        LuckPerms luckPerms = this.plugin.getPluginHookManager().getApi(LuckPerms.class);
        if (luckPerms != null) {
            String metaValue = luckPerms.getPlayerAdapter(Player.class)
                    .getUser(player)
                    .getCachedData()
                    .getMetaData()
                    .getMetaValue(SHOP_LIMIT_META_KEY);
            Integer parsedLimit = parsePositiveInteger(metaValue);
            if (parsedLimit != null) {
                return parsedLimit;
            }
        }

        return this.plugin.getConfigManager().getInt(
                "tnexus.shop.player-shop.default-limit",
                DEFAULT_PLAYER_SHOP_LIMIT);
    }

    /**
     * Returns the current number of cached player shops owned by the player.
     *
     * @param ownerUuid owner id
     * @return player shop count
     */
    public int getPlayerShopCount(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        int cachedCount = 0;
        for (SignShop shop : this.shopsById.values()) {
            if (shop.getType() == ShopType.PLAYER && ownerUuid.equals(shop.getOwnerUuid())) {
                cachedCount++;
            }
        }
        return cachedCount + this.pendingPlayerShopCreations.getOrDefault(ownerUuid, 0);
    }

    /**
     * Returns cached shops owned by the given player.
     *
     * @param ownerUuid owner id
     * @return owned shops
     */
    public List<SignShop> getOwnedShops(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return this.shopsById.values().stream()
                .filter(shop -> shop.getType() == ShopType.PLAYER)
                .filter(shop -> ownerUuid.equals(shop.getOwnerUuid()))
                .sorted((left, right) -> {
                    int worldCompare = left.getSignPosition().worldName().compareToIgnoreCase(right.getSignPosition().worldName());
                    if (worldCompare != 0) {
                        return worldCompare;
                    }
                    int xCompare = Integer.compare(left.getSignPosition().x(), right.getSignPosition().x());
                    if (xCompare != 0) {
                        return xCompare;
                    }
                    int yCompare = Integer.compare(left.getSignPosition().y(), right.getSignPosition().y());
                    if (yCompare != 0) {
                        return yCompare;
                    }
                    return Integer.compare(left.getSignPosition().z(), right.getSignPosition().z());
                })
                .toList();
    }

    private String resolveItemName(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName());
        }

        return Arrays.stream(itemStack.getType().name().toLowerCase(Locale.ROOT).split("_"))
                .map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse(itemStack.getType().name());
    }

    private ItemStack normalizeTemplateItem(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        ItemStack clone = itemStack.clone();
        clone.setAmount(1);
        return clone;
    }

    private @Nullable ItemStack resolveCreationTemplateItem(
            ShopType type,
            @Nullable Block initialChest,
            @Nullable ItemStack templateItem) {
        if (initialChest == null) {
            return templateItem;
        }
        ItemStack chestItem = findFirstTemplateItem(initialChest);
        if (type == ShopType.SERVER) {
            return chestItem;
        }
        return chestItem == null ? templateItem : chestItem;
    }

    private @Nullable Double normalizePrice(@Nullable Double price) {
        if (price == null || !Double.isFinite(price) || price <= 0.0D) {
            return null;
        }
        return price;
    }

    private String sanitizeNote(@Nullable String note) {
        return ChatColor.stripColor(note == null ? "" : note).trim();
    }

    private double nullablePrice(@Nullable Double price) {
        return price == null ? 0.0D : price;
    }

    private int computeAffordableAmount(double balance, double unitPrice) {
        if (!Double.isFinite(balance) || !Double.isFinite(unitPrice) || unitPrice <= 0.0D) {
            return 0;
        }
        return Math.max(0, (int) Math.floor(balance / unitPrice));
    }

    private @Nullable Integer parsePositiveInteger(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isServerShopSellToVoidEnabled() {
        return this.plugin.getConfigManager().getBoolean("tnexus.shop.server-shop.sell-to-void", true);
    }

    private void decrementPendingPlayerShopCreation(UUID ownerUuid) {
        this.pendingPlayerShopCreations.computeIfPresent(ownerUuid, (ignored, count) -> count <= 1 ? null : count - 1);
    }

    private void cancelLinkSessionOnError(UUID playerId, @Nullable LinkSession session) {
        if (session != null && session.commandMode()) {
            this.linkSessions.remove(playerId);
        }
    }

    private String trimPrice(double price) {
        if (price == Math.rint(price)) {
            return Long.toString((long) price);
        }
        return Double.toString(price);
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(this.plugin, runnable);
    }

    private record TradeConstraint(int amount, String adjustmentMessageKey, String unavailableMessageKey) {
    }

    private record TradeEligibility(
            int maxAmount,
            @Nullable String adjustmentMessageKey,
            String unavailableMessageKey,
            boolean available,
            @Nullable ItemStack template) {

        private static TradeEligibility unavailable(String unavailableMessageKey) {
            return new TradeEligibility(0, null, unavailableMessageKey, false, null);
        }
    }

    private record SyncAvailability(
            boolean buySideAvailable,
            boolean sellSideAvailable,
            boolean playerShop,
            CompletableFuture<Boolean> ownerBalanceCheck) {

        private static SyncAvailability unavailable() {
            return new SyncAvailability(false, false, false, CompletableFuture.completedFuture(false));
        }

        private RenderedAvailability resolve(boolean ownerHasFunds) {
            boolean resolvedSellAvailable = this.sellSideAvailable && (!this.playerShop || ownerHasFunds);
            ShopStatus overallStatus = this.buySideAvailable || resolvedSellAvailable
                    ? ShopStatus.AVAILABLE
                    : ShopStatus.UNAVAILABLE;
            return new RenderedAvailability(overallStatus, this.buySideAvailable, resolvedSellAvailable);
        }
    }

    private record RenderedAvailability(ShopStatus status, boolean buyAvailable, boolean sellAvailable) {

        private static RenderedAvailability disabled() {
            return new RenderedAvailability(ShopStatus.DISABLED, false, false);
        }

        private static RenderedAvailability unavailable() {
            return new RenderedAvailability(ShopStatus.UNAVAILABLE, false, false);
        }
    }

    private record LinkSession(@Nullable Long shopId, boolean commandMode) {
    }

    private record TradeResult(TradeResultStatus status) {

        private static TradeResult success() {
            return new TradeResult(TradeResultStatus.SUCCESS);
        }

        private static TradeResult insufficientFunds() {
            return new TradeResult(TradeResultStatus.INSUFFICIENT_FUNDS);
        }

        private static TradeResult failed() {
            return new TradeResult(TradeResultStatus.FAILED);
        }
    }

    private enum TradeResultStatus {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        FAILED
    }
}
