package network.tserver.tnexus.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.config.MessageConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Shared base class for T-Nexus chest GUIs.
 */
public abstract class BaseGui implements InventoryHolder {

    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = 6;
    private static final int BOTTOM_ROW_START = 45;

    private final TNexus plugin;
    private final Player player;
    private final String title;
    private final int rows;
    private final MessageConfig messageConfig;
    private final ConfigManager.GuiSettings guiSettings;
    private final Inventory inventory;
    private final List<Integer> contentSlots;
    private final List<PaginatedItem> paginatedItems;
    private final Map<Integer, Consumer<InventoryClickEvent>> clickHandlers;

    private Consumer<InventoryClickEvent> backHandler;
    private int currentPage;
    private int totalPages;

    /**
     * Creates a new GUI base instance.
     *
     * @param plugin plugin instance
     * @param player target player
     * @param title inventory title
     * @param rows inventory row count
     */
    protected BaseGui(TNexus plugin, Player player, String title, int rows) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.player = Objects.requireNonNull(player, "player");
        this.title = Objects.requireNonNull(title, "title");
        validateRows(rows);
        this.rows = rows;
        this.messageConfig = plugin.getMessageConfig();
        this.guiSettings = plugin.getConfigManager().getGuiSettings();
        this.inventory = Bukkit.createInventory(this, rows * 9, colorize(title));
        this.contentSlots = Collections.unmodifiableList(calculateContentSlots(rows));
        this.paginatedItems = new ArrayList<>();
        this.clickHandlers = new HashMap<>();
        this.currentPage = 0;
        this.totalPages = 1;
    }

    /**
     * Opens the GUI for its player.
     */
    public final void open() {
        render();
        this.plugin.getGuiManager().openGui(this.player, this);
    }

    @Override
    public final Inventory getInventory() {
        return this.inventory;
    }

    /**
     * Returns the GUI title.
     *
     * @return title
     */
    public final String getTitle() {
        return this.title;
    }

    /**
     * Returns the current zero-based page index.
     *
     * @return current page index
     */
    public final int getCurrentPage() {
        return this.currentPage;
    }

    /**
     * Returns the rendered page count.
     *
     * @return total page count
     */
    public final int getTotalPages() {
        return this.totalPages;
    }

    /**
     * Returns the content slot layout used by this GUI.
     *
     * @return content slots
     */
    public final List<Integer> getContentSlots() {
        return this.contentSlots;
    }

    /**
     * Returns the owning plugin instance.
     *
     * @return plugin instance
     */
    protected final TNexus getPlugin() {
        return this.plugin;
    }

    /**
     * Rebuilds the content area for the current page.
     */
    protected abstract void buildContent();

    /**
     * Called when the tracked inventory closes.
     */
    protected void onClose() {
    }

    /**
     * Returns whether the previous-page button should be visible.
     *
     * @return {@code true} when visible
     */
    protected boolean showPreviousPageButton() {
        return true;
    }

    /**
     * Returns whether the next-page button should be visible.
     *
     * @return {@code true} when visible
     */
    protected boolean showNextPageButton() {
        return true;
    }

    /**
     * Returns whether the back button should be visible.
     *
     * @return {@code true} when visible
     */
    protected boolean showBackButton() {
        return true;
    }

    /**
     * Returns whether the current-location indicator should be visible.
     *
     * @return {@code true} when visible
     */
    protected boolean showCurrentLocationIndicator() {
        return true;
    }

    /**
     * Returns whether the close button should be visible.
     *
     * @return {@code true} when visible
     */
    protected boolean showCloseButton() {
        return true;
    }

    /**
     * Returns the close-button handler.
     *
     * @return close-button handler
     */
    protected Consumer<InventoryClickEvent> getCloseHandler() {
        return event -> Bukkit.getScheduler().runTask(
                this.plugin,
                () -> event.getWhoClicked().closeInventory());
    }

    /**
     * Sets a static item and optional click handler at the absolute slot.
     *
     * @param slot target slot
     * @param item item stack
     * @param handler click handler, or {@code null}
     */
    protected final void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        validateInventorySlot(slot);
        this.inventory.setItem(slot, item);
        if (handler != null) {
            this.clickHandlers.put(slot, handler);
        }
    }

    /**
     * Queues a paginated content item for automatic page slicing.
     *
     * @param item item stack
     * @param handler click handler, or {@code null}
     */
    protected final void addPaginatedItem(ItemStack item, Consumer<InventoryClickEvent> handler) {
        this.paginatedItems.add(new PaginatedItem(item, handler));
    }

    /**
     * Replaces the paginated content set.
     *
     * @param items paginated items
     */
    protected final void setPaginatedItems(List<PaginatedItem> items) {
        this.paginatedItems.clear();
        this.paginatedItems.addAll(items);
    }

    /**
     * Sets the optional back-button handler.
     *
     * @param handler back-button handler, or {@code null}
     */
    protected final void setBackHandler(Consumer<InventoryClickEvent> handler) {
        this.backHandler = handler;
    }

    /**
     * Creates a simple item stack with display name and lore lines.
     *
     * @param material item material
     * @param displayName colored display name
     * @param lore colored lore lines
     * @return configured item stack
     */
    protected final ItemStack createItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(displayName));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(this::colorize).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    final void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = this.clickHandlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    final boolean hasClickHandler(int slot) {
        return this.clickHandlers.containsKey(slot);
    }

    final boolean isInventory(Inventory other) {
        return this.inventory.equals(other);
    }

    final boolean isView(InventoryView view) {
        return this.inventory.equals(view.getTopInventory());
    }

    private void render() {
        this.inventory.clear();
        this.paginatedItems.clear();
        this.clickHandlers.clear();

        drawHeader();
        drawContentBorders();
        drawNavigationBar();
        buildContent();
        this.totalPages = calculateTotalPages();
        if (this.currentPage >= this.totalPages) {
            this.currentPage = this.totalPages - 1;
        }
        renderPaginatedItems();
        drawNavigationBar();
    }

    private void drawHeader() {
        ItemStack headerItem = createBlankItem(resolveMaterial(this.guiSettings.headerItem(), Material.BLACK_STAINED_GLASS_PANE));
        for (int slot = 0; slot < 9; slot++) {
            this.inventory.setItem(slot, headerItem.clone());
        }
    }

    private void drawContentBorders() {
        ItemStack borderItem = createBlankItem(resolveMaterial(this.guiSettings.borderItem(), Material.GRAY_STAINED_GLASS_PANE));
        for (int row = 1; row < this.rows - 1; row++) {
            int rowStart = row * 9;
            this.inventory.setItem(rowStart, borderItem.clone());
            this.inventory.setItem(rowStart + 8, borderItem.clone());
        }
    }

    private void drawNavigationBar() {
        ItemStack filler = createBlankItem(resolveMaterial(this.guiSettings.borderItem(), Material.GRAY_STAINED_GLASS_PANE));
        int rowStart = (this.rows - 1) * 9;
        for (int slot = rowStart; slot < rowStart + 9; slot++) {
            this.inventory.setItem(slot, filler.clone());
        }

        int closeSlot = resolveNavigationSlot(this.guiSettings.closeButtonSlot());
        if (showCloseButton()) {
            setItem(closeSlot,
                    createItem(Material.RED_BED,
                            this.messageConfig.getMessage("gui.navigation.close.name"),
                            List.of(this.messageConfig.getMessage("gui.navigation.close.lore"))),
                    getCloseHandler());
        } else {
            this.inventory.setItem(closeSlot, filler.clone());
            this.clickHandlers.remove(closeSlot);
        }

        int currentLocationSlot = resolveNavigationSlot(this.guiSettings.currentLocationSlot());
        if (showCurrentLocationIndicator()) {
            setItem(currentLocationSlot,
                    createItem(Material.EMERALD,
                            this.messageConfig.getMessage("gui.navigation.current.name", this.title),
                            List.of(this.messageConfig.getMessage("gui.navigation.current.lore", this.title))),
                    null);
        } else {
            this.inventory.setItem(currentLocationSlot, filler.clone());
            this.clickHandlers.remove(currentLocationSlot);
        }

        renderBackButton(filler);
        renderPagerButton(resolveNavigationSlot(this.guiSettings.prevPageSlot()),
                this.guiSettings.pagerSettings().previous(),
                showPreviousPageButton(),
                this.currentPage > 0,
                "gui.navigation.previous",
                event -> {
                    this.currentPage--;
                    render();
                });
        renderPagerButton(resolveNavigationSlot(this.guiSettings.nextPageSlot()),
                this.guiSettings.pagerSettings().next(),
                showNextPageButton(),
                this.currentPage + 1 < this.totalPages,
                "gui.navigation.next",
                event -> {
                    this.currentPage++;
                    render();
                });
    }

    private void renderBackButton(ItemStack filler) {
        int slot = resolveNavigationSlot(this.guiSettings.backButtonSlot());
        if (!showBackButton() || this.backHandler == null) {
            this.inventory.setItem(slot, filler.clone());
            this.clickHandlers.remove(slot);
            return;
        }

        setItem(slot,
                createItem(Material.ARROW,
                        this.messageConfig.getMessage("gui.navigation.back.name"),
                        List.of(this.messageConfig.getMessage("gui.navigation.back.lore"))),
                this.backHandler);
    }

    private void renderPagerButton(
            int slot,
            PagerTexture configuredTexture,
            boolean visible,
            boolean enabled,
            String messageKeyPrefix,
            Consumer<InventoryClickEvent> handler) {
        if (!visible) {
            ItemStack filler = createBlankItem(resolveMaterial(this.guiSettings.borderItem(), Material.GRAY_STAINED_GLASS_PANE));
            this.inventory.setItem(slot, filler);
            this.clickHandlers.remove(slot);
            return;
        }
        PagerTexture texture = new PagerTexture(
                configuredTexture.enabledTexture(),
                configuredTexture.disabledTexture());
        String state = enabled ? "enabled" : "disabled";
        ItemStack button = createPlayerHead(
                enabled ? texture.enabledTexture() : texture.disabledTexture(),
                this.messageConfig.getMessage(messageKeyPrefix + "." + state + ".name"),
                List.of(this.messageConfig.getMessage(messageKeyPrefix + "." + state + ".lore")));
        setItem(slot, button, enabled ? handler : null);
    }

    private void renderPaginatedItems() {
        if (this.paginatedItems.isEmpty()) {
            return;
        }

        int startIndex = this.currentPage * this.contentSlots.size();
        int endIndex = Math.min(startIndex + this.contentSlots.size(), this.paginatedItems.size());
        for (int index = startIndex; index < endIndex; index++) {
            int slot = this.contentSlots.get(index - startIndex);
            PaginatedItem item = this.paginatedItems.get(index);
            setItem(slot, item.item(), item.handler());
        }
    }

    private int calculateTotalPages() {
        if (this.paginatedItems.isEmpty()) {
            return 1;
        }
        int pageSize = this.contentSlots.size();
        int pageCount = (int) Math.ceil((double) this.paginatedItems.size() / pageSize);
        return Math.max(1, pageCount);
    }

    private List<Integer> calculateContentSlots(int inventoryRows) {
        return switch (inventoryRows) {
            case 3 -> List.of(10, 11, 12, 13, 14, 15, 16);
            case 4 -> List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
            case 6 -> List.of(
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43);
            default -> throw new IllegalArgumentException("Unsupported GUI row count: " + inventoryRows);
        };
    }

    private int resolveNavigationSlot(int configuredSlot) {
        int normalizedOffset = Math.min(8, Math.max(0, configuredSlot - BOTTOM_ROW_START));
        return (this.rows - 1) * 9 + normalizedOffset;
    }

    private ItemStack createBlankItem(Material material) {
        return createItem(material, " ", List.of());
    }

    private ItemStack createPlayerHead(String textureValue, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(colorize(displayName));
        meta.setLore(lore.stream().map(this::colorize).toList());

        if (textureValue != null && !textureValue.isBlank()) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "tnexus-pager");
            profile.setProperty(new ProfileProperty("textures", textureValue));
            meta.setPlayerProfile(profile);
        }

        item.setItemMeta(meta);
        return item;
    }

    private Material resolveMaterial(String configuredName, Material fallback) {
        if (configuredName == null || configuredName.isBlank()) {
            return fallback;
        }

        Material material = Material.matchMaterial(configuredName);
        return material == null ? fallback : material;
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void validateInventorySlot(int slot) {
        if (slot < 0 || slot >= this.inventory.getSize()) {
            throw new IllegalArgumentException("Invalid GUI slot: " + slot);
        }
    }

    private void validateRows(int inventoryRows) {
        if (inventoryRows < MIN_ROWS || inventoryRows > MAX_ROWS || inventoryRows == 5) {
            throw new IllegalArgumentException("BaseGui supports only 3, 4, or 6 rows.");
        }
    }

    /**
     * Paginated content entry with an optional click handler.
     *
     * @param item rendered item
     * @param handler click handler, or {@code null}
     */
    public record PaginatedItem(ItemStack item, Consumer<InventoryClickEvent> handler) {
    }
}
