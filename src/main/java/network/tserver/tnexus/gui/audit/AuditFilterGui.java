package network.tserver.tnexus.gui.audit;

import java.util.List;
import java.util.Objects;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.gui.BaseGui;
import network.tserver.tnexus.manager.AuditLogFilter;
import network.tserver.tnexus.manager.AuditLogManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Filter selection GUI for audit history.
 */
public final class AuditFilterGui extends BaseGui {

    private static final int ROWS = 3;
    private static final int DEPOSIT_SLOT = 10;
    private static final int WITHDRAW_SLOT = 11;
    private static final int PAYMENT_RECEIVED_SLOT = 12;
    private static final int PAYMENT_SENT_SLOT = 13;
    private static final int SHOP_SELL_SLOT = 14;
    private static final int SHOP_BUY_SLOT = 15;
    private static final int ALL_SLOT = 16;

    private final AuditLogManager auditLogManager;
    private final Player viewer;
    private final OfflinePlayer targetPlayer;
    private final AuditLogFilter currentFilter;
    private final Runnable returnAction;

    /**
     * Creates a new filter selection GUI.
     *
     * @param plugin plugin instance
     * @param auditLogManager audit-log manager
     * @param viewer viewing player
     * @param targetPlayer target player
     * @param currentFilter current filter
     * @param returnAction callback returning to the list GUI
     */
    public AuditFilterGui(
            TNexus plugin,
            AuditLogManager auditLogManager,
            Player viewer,
            OfflinePlayer targetPlayer,
            AuditLogFilter currentFilter,
            Runnable returnAction) {
        super(plugin, viewer, plugin.getMessageConfig().getMessage("audit.filter.gui.title"), ROWS);
        this.auditLogManager = Objects.requireNonNull(auditLogManager, "auditLogManager");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.targetPlayer = Objects.requireNonNull(targetPlayer, "targetPlayer");
        this.currentFilter = Objects.requireNonNull(currentFilter, "currentFilter");
        this.returnAction = Objects.requireNonNull(returnAction, "returnAction");
    }

    @Override
    protected void buildContent() {
        setFilterButton(DEPOSIT_SLOT, AuditLogFilter.DEPOSIT);
        setFilterButton(WITHDRAW_SLOT, AuditLogFilter.WITHDRAW);
        setFilterButton(PAYMENT_RECEIVED_SLOT, AuditLogFilter.PAYMENT_RECEIVED);
        setFilterButton(PAYMENT_SENT_SLOT, AuditLogFilter.PAYMENT_SENT);
        setFilterButton(SHOP_SELL_SLOT, AuditLogFilter.SHOP_SELL);
        setFilterButton(SHOP_BUY_SLOT, AuditLogFilter.SHOP_BUY);
        setFilterButton(ALL_SLOT, AuditLogFilter.ALL);
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

    @Override
    protected java.util.function.Consumer<InventoryClickEvent> getCloseHandler() {
        return event -> this.returnAction.run();
    }

    private void setFilterButton(int slot, AuditLogFilter filter) {
        setItem(
                slot,
                createItem(
                        AuditLogGui.getFilterMaterial(filter),
                        getPlugin().getMessageConfig().getMessage(
                                "audit.filter.gui.option.name",
                                AuditLogGui.getFilterLabel(getPlugin(), filter)),
                        List.of(
                                getPlugin().getMessageConfig().getMessage(
                                        "audit.filter.gui.option.current",
                                        this.currentFilter == filter
                                                ? getPlugin().getMessageConfig().getMessage("audit.filter.gui.option.selected")
                                                : getPlugin().getMessageConfig().getMessage("audit.filter.gui.option.not-selected")),
                                getPlugin().getMessageConfig().getMessage("audit.filter.gui.option.lore"))),
                event -> this.auditLogManager.openHistoryViewer(this.viewer, this.targetPlayer, filter));
    }
}
