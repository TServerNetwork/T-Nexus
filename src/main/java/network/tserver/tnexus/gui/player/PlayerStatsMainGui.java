package network.tserver.tnexus.gui.player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.gui.BaseGui;
import network.tserver.tnexus.manager.PlayerStatsViewerManager;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.PlayerStatsSnapshot;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsCategory;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsEntry;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsPeriodFilter;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsSortOrder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Main stats hub GUI with category buttons and favorite slots.
 */
public final class PlayerStatsMainGui extends BaseGui {

    private static final int ROWS = 6;
    private static final int PLAYER_INFO_SLOT = 4;
    private static final int FILTER_SLOT = 7;
    private static final int SORT_SLOT = 8;
    private static final int GENERAL_SLOT = 20;
    private static final int ECONOMY_SLOT = 21;
    private static final int BLOCKS_SLOT = 22;
    private static final int COMBAT_SLOT = 23;
    private static final int ACTIVITY_SLOT = 24;
    private static final List<Integer> FAVORITE_SLOTS =
            List.of(28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final PlayerStatsViewerManager statsViewerManager;
    private final Player viewer;
    private final OfflinePlayer target;
    private final PlayerStatsSnapshot snapshot;
    private final StatsPeriodFilter periodFilter;
    private final StatsSortOrder sortOrder;

    /**
     * Creates a new main stats GUI.
     *
     * @param plugin plugin instance
     * @param statsViewerManager stats manager
     * @param viewer viewer
     * @param snapshot loaded snapshot
     * @param periodFilter active period filter
     * @param sortOrder active sort order
     */
    public PlayerStatsMainGui(
            TNexus plugin,
            PlayerStatsViewerManager statsViewerManager,
            Player viewer,
            PlayerStatsSnapshot snapshot,
            StatsPeriodFilter periodFilter,
            StatsSortOrder sortOrder) {
        super(
                plugin,
                viewer,
                plugin.getMessageConfig().getMessage("stats.gui.main.title", snapshot.targetName()),
                ROWS);
        this.statsViewerManager = statsViewerManager;
        this.viewer = viewer;
        this.target = Bukkit.getOfflinePlayer(snapshot.targetId());
        this.snapshot = snapshot;
        this.periodFilter = periodFilter;
        this.sortOrder = sortOrder;
    }

    @Override
    protected void buildContent() {
        setItem(PLAYER_INFO_SLOT, createPlayerHead(), null);
        setItem(FILTER_SLOT, createFilterItem(), event -> this.statsViewerManager.openMainGui(
                this.viewer,
                this.target,
                this.periodFilter.next(),
                this.sortOrder));
        setItem(SORT_SLOT, createSortItem(), event -> this.statsViewerManager.openMainGui(
                this.viewer,
                this.target,
                this.periodFilter,
                this.sortOrder.next()));

        setItem(GENERAL_SLOT, createCategoryItem(StatsCategory.GENERAL), event -> openCategory(StatsCategory.GENERAL));
        setItem(ECONOMY_SLOT, createCategoryItem(StatsCategory.ECONOMY), event -> openCategory(StatsCategory.ECONOMY));
        setItem(BLOCKS_SLOT, createCategoryItem(StatsCategory.BLOCKS), event -> openCategory(StatsCategory.BLOCKS));
        setItem(COMBAT_SLOT, createCategoryItem(StatsCategory.COMBAT), event -> openCategory(StatsCategory.COMBAT));
        setItem(ACTIVITY_SLOT, createCategoryItem(StatsCategory.ACTIVITY), event -> openCategory(StatsCategory.ACTIVITY));

        renderFavorites();
    }

    @Override
    protected boolean showPreviousPageButton() {
        return false;
    }

    @Override
    protected boolean showNextPageButton() {
        return false;
    }

    @Override
    protected boolean showBackButton() {
        return false;
    }

    @Override
    protected boolean showCurrentLocationIndicator() {
        return false;
    }

    private void openCategory(StatsCategory category) {
        this.statsViewerManager.openCategoryGui(
                this.viewer,
                this.target,
                category,
                this.periodFilter,
                this.sortOrder);
    }

    private ItemStack createPlayerHead() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setOwningPlayer(this.target);
        meta.setDisplayName(getPlugin().getMessageConfig().getMessage(
                "stats.gui.main.player.name",
                this.snapshot.targetName()));
        List<String> lore = new ArrayList<>();
        lore.add(getPlugin().getMessageConfig().getMessage(
                "stats.gui.main.player.uuid",
                this.snapshot.targetId()));
        Instant firstLogin = this.snapshot.firstLogin();
        lore.add(getPlugin().getMessageConfig().getMessage(
                "stats.gui.main.player.first-login",
                firstLogin == null
                        ? getPlugin().getMessageConfig().getMessage("stats.values.unavailable")
                        : DATE_TIME_FORMAT.format(firstLogin.atZone(ZoneId.systemDefault()))));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFilterItem() {
        return createItem(
                Material.CLOCK,
                getPlugin().getMessageConfig().getMessage("stats.gui.main.filter.name"),
                List.of(
                        getPlugin().getMessageConfig().getMessage(
                                "stats.gui.main.filter.current",
                                this.statsViewerManager.getPeriodLabel(this.periodFilter)),
                        getPlugin().getMessageConfig().getMessage("stats.gui.main.filter.options"),
                        getPlugin().getMessageConfig().getMessage("stats.gui.main.filter.hint")));
    }

    private ItemStack createSortItem() {
        return createItem(
                Material.COMPARATOR,
                getPlugin().getMessageConfig().getMessage("stats.gui.main.sort.name"),
                List.of(
                        getPlugin().getMessageConfig().getMessage(
                                "stats.gui.main.sort.current",
                                this.statsViewerManager.getSortLabel(this.sortOrder)),
                        getPlugin().getMessageConfig().getMessage("stats.gui.main.sort.options"),
                        getPlugin().getMessageConfig().getMessage("stats.gui.main.sort.hint")));
    }

    private ItemStack createCategoryItem(StatsCategory category) {
        return createItem(
                category.icon(),
                this.statsViewerManager.getCategoryLabel(category),
                List.of(getPlugin().getMessageConfig().getMessage(categoryLoreKey(category))));
    }

    private String categoryLoreKey(StatsCategory category) {
        return switch (category) {
            case GENERAL -> "stats.gui.main.category.general";
            case ECONOMY -> "stats.gui.main.category.economy";
            case BLOCKS -> "stats.gui.main.category.blocks";
            case COMBAT -> "stats.gui.main.category.combat";
            case ACTIVITY -> "stats.gui.main.category.activity";
        };
    }

    private void renderFavorites() {
        Map<Integer, String> favorites = this.snapshot.getFavorites();
        for (Integer slot : FAVORITE_SLOTS) {
            String statKey = favorites.get(slot);
            StatsEntry entry = statKey == null ? null : this.snapshot.getEntry(statKey);
            if (entry == null) {
                setItem(
                        slot,
                        createItem(
                                Material.GRAY_STAINED_GLASS_PANE,
                                getPlugin().getMessageConfig().getMessage("stats.gui.main.favorite.empty.name"),
                                List.of(getPlugin().getMessageConfig().getMessage("stats.gui.main.favorite.empty.lore"))),
                        null);
                continue;
            }
            setItem(slot, createPinnedFavoriteItem(entry), null);
        }
    }

    private ItemStack createPinnedFavoriteItem(StatsEntry entry) {
        List<String> lore = new ArrayList<>();
        lore.add(getPlugin().getMessageConfig().getMessage(
                "stats.gui.entry.value",
                entry.valueText()));
        for (String detailLine : entry.detailLines()) {
            lore.add(detailLine);
        }
        lore.add(getPlugin().getMessageConfig().getMessage(
                "stats.gui.main.favorite.category",
                this.statsViewerManager.getCategoryLabel(entry.category())));
        lore.add(getPlugin().getMessageConfig().getMessage(
                "stats.gui.entry.period",
                this.statsViewerManager.getPeriodLabel(this.periodFilter)));
        return createItem(entry.material(), entry.displayName(), lore);
    }
}
