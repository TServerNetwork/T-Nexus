package network.tserver.tnexus.gui.player;

import java.util.ArrayList;
import java.util.List;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.gui.BaseGui;
import network.tserver.tnexus.manager.PlayerStatsRankingManager;
import network.tserver.tnexus.manager.PlayerStatsRankingManager.RankingEntry;
import network.tserver.tnexus.manager.PlayerStatsRankingManager.RankingSnapshot;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsPeriodFilter;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsSortOrder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Paginated play-time ranking GUI.
 */
public final class PlayerStatsRankingGui extends BaseGui {

    private static final int ROWS = 6;
    private static final int TITLE_SLOT = 4;
    private static final int FILTER_SLOT = 7;
    private static final int VIEWER_RANK_SLOT = 8;
    private static final int EMPTY_STATE_SLOT = 22;

    private final PlayerStatsRankingManager rankingManager;
    private final Player viewer;
    private final RankingSnapshot snapshot;
    private final StatsPeriodFilter periodFilter;

    /**
     * Creates a new ranking GUI.
     *
     * @param plugin plugin instance
     * @param rankingManager ranking manager
     * @param viewer viewing player
     * @param snapshot ranking snapshot
     * @param periodFilter active period filter
     */
    public PlayerStatsRankingGui(
            TNexus plugin,
            PlayerStatsRankingManager rankingManager,
            Player viewer,
            RankingSnapshot snapshot,
            StatsPeriodFilter periodFilter) {
        super(
                plugin,
                viewer,
                plugin.getMessageConfig().getMessage("stats.ranking.gui.title"),
                ROWS);
        this.rankingManager = rankingManager;
        this.viewer = viewer;
        this.snapshot = snapshot;
        this.periodFilter = periodFilter;
    }

    @Override
    protected void buildContent() {
        setBackHandler(event -> getPlugin().getPlayerStatsViewerManager().openMainGui(
                this.viewer,
                this.viewer,
                StatsPeriodFilter.ALL_TIME,
                StatsSortOrder.VALUE_DESC));
        setItem(TITLE_SLOT, createTitleItem(), null);
        setItem(FILTER_SLOT, createFilterItem(), event -> this.rankingManager.openRankingGui(
                this.viewer,
                this.periodFilter.next()));
        setItem(VIEWER_RANK_SLOT, createViewerRankItem(), null);

        if (this.snapshot.entries().isEmpty()) {
            setItem(
                    EMPTY_STATE_SLOT,
                    createItem(
                            Material.BARRIER,
                            getPlugin().getMessageConfig().getMessage("stats.ranking.gui.empty.name"),
                            List.of(getPlugin().getMessageConfig().getMessage("stats.ranking.gui.empty.lore"))),
                    null);
            return;
        }

        for (RankingEntry entry : this.snapshot.entries()) {
            addPaginatedItem(createRankingHead(entry), null);
        }
    }

    @Override
    protected boolean showCurrentLocationIndicator() {
        return false;
    }

    private ItemStack createTitleItem() {
        return createItem(
                Material.CLOCK,
                getPlugin().getMessageConfig().getMessage("stats.ranking.gui.header.name"),
                List.of(
                        getPlugin().getMessageConfig().getMessage(
                                "stats.ranking.gui.header.period",
                                this.rankingManager.getPeriodLabel(this.periodFilter)),
                        getPlugin().getMessageConfig().getMessage("stats.ranking.gui.header.lore")));
    }

    private ItemStack createFilterItem() {
        return createItem(
                Material.COMPASS,
                getPlugin().getMessageConfig().getMessage("stats.ranking.gui.filter.name"),
                List.of(
                        getPlugin().getMessageConfig().getMessage(
                                "stats.ranking.gui.filter.current",
                                this.rankingManager.getPeriodLabel(this.periodFilter)),
                        getPlugin().getMessageConfig().getMessage("stats.ranking.gui.filter.options"),
                        getPlugin().getMessageConfig().getMessage("stats.ranking.gui.filter.hint")));
    }

    private ItemStack createViewerRankItem() {
        RankingEntry viewerEntry = this.snapshot.viewerEntry();
        if (viewerEntry == null) {
            return createItem(
                    Material.NAME_TAG,
                    getPlugin().getMessageConfig().getMessage("stats.ranking.gui.self.unranked.name"),
                    List.of(
                            getPlugin().getMessageConfig().getMessage(
                                    "stats.ranking.gui.self.period",
                                    this.rankingManager.getPeriodLabel(this.periodFilter)),
                            getPlugin().getMessageConfig().getMessage("stats.ranking.gui.self.unranked.lore")));
        }

        return createItem(
                Material.NAME_TAG,
                getPlugin().getMessageConfig().getMessage("stats.ranking.gui.self.name"),
                List.of(
                        getPlugin().getMessageConfig().getMessage(
                                "stats.ranking.gui.self.rank",
                                this.rankingManager.formatRank(viewerEntry.rank())),
                        getPlugin().getMessageConfig().getMessage(
                                "stats.ranking.gui.self.play-time",
                                this.rankingManager.formatDuration(viewerEntry.playTimeSeconds())),
                        getPlugin().getMessageConfig().getMessage(
                                "stats.ranking.gui.self.period",
                                this.rankingManager.getPeriodLabel(this.periodFilter))));
    }

    private ItemStack createRankingHead(RankingEntry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setOwningPlayer(getPlugin().getServer().getOfflinePlayer(entry.playerId()));
        meta.setDisplayName(getPlugin().getMessageConfig().getMessage(
                entry.highlighted()
                        ? "stats.ranking.gui.entry.name-self"
                        : "stats.ranking.gui.entry.name",
                this.rankingManager.formatRank(entry.rank()),
                entry.playerName()));
        List<String> lore = new ArrayList<>();
        lore.add(getPlugin().getMessageConfig().getMessage(
                "stats.ranking.gui.entry.play-time",
                this.rankingManager.formatDuration(entry.playTimeSeconds())));
        lore.add(getPlugin().getMessageConfig().getMessage(
                "stats.ranking.gui.entry.period",
                this.rankingManager.getPeriodLabel(this.periodFilter)));
        if (entry.highlighted()) {
            lore.add(getPlugin().getMessageConfig().getMessage("stats.ranking.gui.entry.self-highlight"));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.setLore(lore.stream().map(line ->
                org.bukkit.ChatColor.translateAlternateColorCodes('&', line)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
