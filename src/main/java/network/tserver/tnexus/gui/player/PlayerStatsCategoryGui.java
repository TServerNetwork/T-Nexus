package network.tserver.tnexus.gui.player;

import java.util.ArrayList;
import java.util.List;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.gui.BaseGui;
import network.tserver.tnexus.manager.PlayerStatsViewerManager;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.CombatDetailType;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.FavoriteToggleStatus;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.PlayerStatsSnapshot;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsCategory;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsEntry;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsPeriodFilter;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsSortOrder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Category detail GUI for player stats entries.
 */
public final class PlayerStatsCategoryGui extends BaseGui {

    private static final int ROWS = 6;
    private static final int CATEGORY_SLOT = 4;
    private static final int FILTER_SLOT = 7;
    private static final int SORT_SLOT = 8;
    private static final int EMPTY_STATE_SLOT = 22;

    private final PlayerStatsViewerManager statsViewerManager;
    private final Player viewer;
    private final OfflinePlayer target;
    private final PlayerStatsSnapshot snapshot;
    private final StatsCategory category;
    private final StatsPeriodFilter periodFilter;
    private final StatsSortOrder sortOrder;

    /**
     * Creates a new category GUI.
     *
     * @param plugin plugin instance
     * @param statsViewerManager stats manager
     * @param viewer viewer
     * @param snapshot loaded snapshot
     * @param category category
     * @param periodFilter active period filter
     * @param sortOrder active sort order
     */
    public PlayerStatsCategoryGui(
            TNexus plugin,
            PlayerStatsViewerManager statsViewerManager,
            Player viewer,
            PlayerStatsSnapshot snapshot,
            StatsCategory category,
            StatsPeriodFilter periodFilter,
            StatsSortOrder sortOrder) {
        super(
                plugin,
                viewer,
                plugin.getMessageConfig().getMessage(
                        "stats.gui.category.title",
                        snapshot.targetName(),
                        statsViewerManager.getCategoryLabel(category)),
                ROWS);
        this.statsViewerManager = statsViewerManager;
        this.viewer = viewer;
        this.target = Bukkit.getOfflinePlayer(snapshot.targetId());
        this.snapshot = snapshot;
        this.category = category;
        this.periodFilter = periodFilter;
        this.sortOrder = sortOrder;
    }

    @Override
    protected void buildContent() {
        setBackHandler(event -> this.statsViewerManager.openMainGui(
                this.viewer,
                this.target,
                this.periodFilter,
                this.sortOrder));
        setItem(CATEGORY_SLOT, createCategoryIcon(), null);
        setItem(FILTER_SLOT, createFilterItem(), event -> this.statsViewerManager.openCategoryGui(
                this.viewer,
                this.target,
                this.category,
                this.periodFilter.next(),
                this.sortOrder));
        setItem(SORT_SLOT, createSortItem(), event -> this.statsViewerManager.openCategoryGui(
                this.viewer,
                this.target,
                this.category,
                this.periodFilter,
                this.sortOrder.next()));

        List<StatsEntry> entries = this.snapshot.getSortedEntries(this.category, this.sortOrder);
        if (entries.isEmpty()) {
            setItem(
                    EMPTY_STATE_SLOT,
                    createItem(
                            Material.BARRIER,
                            getPlugin().getMessageConfig().getMessage("stats.gui.category.empty.name"),
                            List.of(getPlugin().getMessageConfig().getMessage("stats.gui.category.empty.lore"))),
                    null);
            return;
        }

        for (StatsEntry entry : entries) {
            addPaginatedItem(createEntryItem(entry), event -> {
                if (event.getClick() == ClickType.RIGHT) {
                    toggleFavorite(entry);
                    return;
                }
                if (this.category == StatsCategory.COMBAT) {
                    CombatDetailType detailType = this.statsViewerManager.resolveCombatDetailType(entry.key());
                    if (detailType != null) {
                        this.statsViewerManager.openCombatDetailGui(
                                this.viewer,
                                this.target,
                                detailType,
                                this.periodFilter,
                                this.sortOrder);
                    }
                }
            });
        }
    }

    @Override
    protected boolean showCurrentLocationIndicator() {
        return false;
    }

    private void toggleFavorite(StatsEntry entry) {
        this.statsViewerManager.toggleFavorite(this.viewer.getUniqueId(), this.snapshot, entry)
                .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(getPlugin(), () -> {
                    if (throwable != null) {
                        getPlugin().getMessageConfig().sendMessage(this.viewer, "stats.command.load-failed");
                        this.viewer.playSound(this.viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                        return;
                    }

                    if (result.status() == FavoriteToggleStatus.FULL) {
                        getPlugin().getMessageConfig().sendMessage(this.viewer, "stats.command.favorites-full");
                        this.viewer.playSound(this.viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                        return;
                    }

                    refresh();
                }));
    }

    private org.bukkit.inventory.ItemStack createCategoryIcon() {
        return createItem(
                this.category.icon(),
                this.statsViewerManager.getCategoryLabel(this.category),
                List.of(getPlugin().getMessageConfig().getMessage("stats.gui.category.icon.lore")));
    }

    private org.bukkit.inventory.ItemStack createFilterItem() {
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

    private org.bukkit.inventory.ItemStack createSortItem() {
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

    private ItemStack createEntryItem(StatsEntry entry) {
        List<String> lore = new ArrayList<>();
        if (this.snapshot.getFavorites().containsValue(entry.key())) {
            lore.add(getPlugin().getMessageConfig().getMessage("stats.gui.entry.pinned"));
        }
        lore.add(getPlugin().getMessageConfig().getMessage("stats.gui.entry.separator"));
        lore.add(getPlugin().getMessageConfig().getMessage("stats.gui.entry.value", entry.valueText()));
        lore.addAll(entry.detailLines());
        lore.add(getPlugin().getMessageConfig().getMessage("stats.gui.entry.separator"));
        lore.add(getPlugin().getMessageConfig().getMessage(
                "stats.gui.entry.period",
                this.statsViewerManager.getPeriodLabel(this.periodFilter)));
        lore.add(getPlugin().getMessageConfig().getMessage("stats.gui.entry.separator"));
        lore.add(getPlugin().getMessageConfig().getMessage("stats.gui.entry.favorite-hint"));
        if (entry.material() == Material.PLAYER_HEAD && entry.playerHeadId() != null) {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta == null) {
                return item;
            }
            meta.setOwningPlayer(getPlugin().getServer().getOfflinePlayer(entry.playerHeadId()));
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', entry.displayName()));
            meta.setLore(lore.stream().map(line ->
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', line)).toList());
            item.setItemMeta(meta);
            return item;
        }
        return createItem(entry.material(), entry.displayName(), lore);
    }
}
