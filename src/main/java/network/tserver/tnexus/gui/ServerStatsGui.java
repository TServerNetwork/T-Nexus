package network.tserver.tnexus.gui;

import java.util.List;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.ServerStatsManager.ServerStatsSnapshot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only GUI showing aggregate server statistics.
 */
public final class ServerStatsGui extends BaseGui {

    private static final int ROWS = 4;
    private static final int ONLINE_SLOT = 10;
    private static final int TOTAL_TRANSACTIONS_SLOT = 12;
    private static final int TOTAL_VOLUME_SLOT = 14;
    private static final int UPTIME_SLOT = 19;
    private static final int CIRCULATION_SLOT = 21;
    private static final int ACTIVE_SHOPS_SLOT = 23;

    private @Nullable ServerStatsSnapshot snapshot;

    /**
     * Creates a new GUI instance.
     *
     * @param plugin plugin instance
     * @param player target player
     */
    public ServerStatsGui(TNexus plugin, Player player) {
        super(plugin, player, plugin.getMessageConfig().getMessage("server-stats.gui.title"), ROWS);
    }

    /**
     * Applies the loaded stats and re-renders the GUI.
     *
     * @param stats loaded stats snapshot
     */
    public void showStats(ServerStatsSnapshot stats) {
        this.snapshot = stats;
        refresh();
    }

    @Override
    protected void buildContent() {
        if (this.snapshot == null) {
            renderLoadingState();
            return;
        }

        setItem(ONLINE_SLOT, createItem(
                Material.PLAYER_HEAD,
                getPlugin().getMessageConfig().getMessage("server-stats.gui.online.name"),
                List.of(getPlugin().getMessageConfig().getMessage(
                        "server-stats.gui.online.lore",
                        this.snapshot.onlinePlayers()))),
                null);
        setItem(TOTAL_TRANSACTIONS_SLOT, createItem(
                Material.GOLD_INGOT,
                getPlugin().getMessageConfig().getMessage("server-stats.gui.total-transactions.name"),
                List.of(getPlugin().getMessageConfig().getMessage(
                        "server-stats.gui.total-transactions.lore",
                        this.snapshot.totalTransactions()))),
                null);
        setItem(TOTAL_VOLUME_SLOT, createItem(
                Material.GOLD_BLOCK,
                getPlugin().getMessageConfig().getMessage("server-stats.gui.total-volume.name"),
                List.of(getPlugin().getMessageConfig().getMessage(
                        "server-stats.gui.total-volume.lore",
                        this.snapshot.totalTransactionAmount()))),
                null);
        setItem(UPTIME_SLOT, createItem(
                Material.CLOCK,
                getPlugin().getMessageConfig().getMessage("server-stats.gui.uptime.name"),
                List.of(getPlugin().getMessageConfig().getMessage(
                        "server-stats.gui.uptime.lore",
                        this.snapshot.uptime()))),
                null);
        setItem(CIRCULATION_SLOT, createItem(
                Material.EMERALD,
                getPlugin().getMessageConfig().getMessage("server-stats.gui.circulation.name"),
                List.of(getPlugin().getMessageConfig().getMessage(
                        "server-stats.gui.circulation.lore",
                        this.snapshot.circulationAmount()))),
                null);
        setItem(ACTIVE_SHOPS_SLOT, createItem(
                Material.CHEST,
                getPlugin().getMessageConfig().getMessage("server-stats.gui.active-shops.name"),
                List.of(getPlugin().getMessageConfig().getMessage(
                        "server-stats.gui.active-shops.lore",
                        this.snapshot.activeShopCount()))),
                null);
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

    private void renderLoadingState() {
        setItem(ONLINE_SLOT, createLoadingItem(Material.PLAYER_HEAD), null);
        setItem(TOTAL_TRANSACTIONS_SLOT, createLoadingItem(Material.GOLD_INGOT), null);
        setItem(TOTAL_VOLUME_SLOT, createLoadingItem(Material.GOLD_BLOCK), null);
        setItem(UPTIME_SLOT, createLoadingItem(Material.CLOCK), null);
        setItem(CIRCULATION_SLOT, createLoadingItem(Material.EMERALD), null);
        setItem(ACTIVE_SHOPS_SLOT, createLoadingItem(Material.CHEST), null);
    }

    private org.bukkit.inventory.ItemStack createLoadingItem(Material material) {
        return createItem(
                material,
                getPlugin().getMessageConfig().getMessage("server-stats.gui.loading.name"),
                List.of(getPlugin().getMessageConfig().getMessage("server-stats.gui.loading.lore")));
    }
}
