package network.tserver.tnexus.gui.audit;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.database.repository.TransactionRepository.AuditEntry;
import network.tserver.tnexus.gui.BaseGui;
import network.tserver.tnexus.gui.MainMenuGui;
import network.tserver.tnexus.manager.AuditLogFilter;
import network.tserver.tnexus.manager.AuditLogManager;
import network.tserver.tnexus.util.CurrencyFormatter;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Paginated audit history viewer GUI.
 */
public final class AuditLogGui extends BaseGui {

    private static final int ROWS = 6;
    private static final int FILTER_SLOT = 4;
    private static final int EMPTY_STATE_SLOT = 22;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private final AuditLogManager auditLogManager;
    private final Player viewer;
    private final OfflinePlayer targetPlayer;
    private final AuditLogFilter filter;
    private final List<AuditEntry> entries;

    /**
     * Creates a new audit history GUI.
     *
     * @param plugin plugin instance
     * @param auditLogManager audit-log manager
     * @param viewer viewing player
     * @param targetPlayer target player
     * @param filter active filter
     * @param entries loaded audit entries
     */
    public AuditLogGui(
            TNexus plugin,
            AuditLogManager auditLogManager,
            Player viewer,
            OfflinePlayer targetPlayer,
            AuditLogFilter filter,
            List<AuditEntry> entries) {
        super(
                plugin,
                viewer,
                viewer.getUniqueId().equals(targetPlayer.getUniqueId())
                        ? plugin.getMessageConfig().getMessage("audit.history.gui.title-self")
                        : plugin.getMessageConfig().getMessage(
                                "audit.history.gui.title-other",
                                resolvePlayerName(targetPlayer)),
                ROWS);
        this.auditLogManager = Objects.requireNonNull(auditLogManager, "auditLogManager");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.targetPlayer = Objects.requireNonNull(targetPlayer, "targetPlayer");
        this.filter = Objects.requireNonNull(filter, "filter");
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    @Override
    protected void buildContent() {
        setBackHandler(event -> new MainMenuGui(getPlugin(), this.viewer).open());
        setItem(
                FILTER_SLOT,
                createItem(
                        Material.HOPPER,
                        getPlugin().getMessageConfig().getMessage("audit.history.gui.filter.name"),
                        List.of(
                                getPlugin().getMessageConfig().getMessage(
                                        "audit.history.gui.filter.current",
                                        getFilterLabel(getPlugin(), this.filter)),
                                getPlugin().getMessageConfig().getMessage("audit.history.gui.filter.lore"))),
                event -> new AuditFilterGui(
                        getPlugin(),
                        this.auditLogManager,
                        this.viewer,
                        this.targetPlayer,
                        this.filter,
                        this::open).open());

        if (this.entries.isEmpty()) {
            setItem(
                    EMPTY_STATE_SLOT,
                    createItem(
                            Material.BARRIER,
                            getPlugin().getMessageConfig().getMessage("audit.history.gui.empty.name"),
                            List.of(getPlugin().getMessageConfig().getMessage("audit.history.gui.empty.lore"))),
                    null);
            return;
        }

        for (AuditEntry entry : this.entries) {
            addPaginatedItem(createAuditItem(entry), null);
        }
    }

    private ItemStack createAuditItem(AuditEntry entry) {
        AuditLogFilter typeFilter = AuditLogFilter.fromTransactionType(entry.type());
        return createItem(
                getFilterMaterial(typeFilter),
                getPlugin().getMessageConfig().getMessage(
                        "audit.history.gui.entry.name",
                        TIMESTAMP_FORMAT.format(entry.createdAt())),
                List.of(
                        getPlugin().getMessageConfig().getMessage(
                                "audit.history.gui.entry.type",
                                getFilterColor(typeFilter),
                                getFilterLabel(getPlugin(), typeFilter)),
                        getPlugin().getMessageConfig().getMessage(
                                "audit.history.gui.entry.counterpart",
                                resolveCounterpartName(entry)),
                        getPlugin().getMessageConfig().getMessage(
                                "audit.history.gui.entry.description",
                                entry.description() == null ? "-" : entry.description()),
                        getPlugin().getMessageConfig().getMessage("audit.history.gui.entry.separator"),
                        getPlugin().getMessageConfig().getMessage(
                                "audit.history.gui.entry.amount",
                                getAmountColor(typeFilter),
                                getAmountPrefix(typeFilter),
                                CurrencyFormatter.format(getPlugin(), entry.amount())),
                        getPlugin().getMessageConfig().getMessage(
                                "audit.history.gui.entry.balance",
                                CurrencyFormatter.format(getPlugin(), entry.balanceAfter()))));
    }

    private String resolveCounterpartName(AuditEntry entry) {
        if (entry.counterpartUuid() == null) {
            return getPlugin().getMessageConfig().getMessage("audit.history.gui.system-name");
        }
        OfflinePlayer counterpart = getPlugin().getServer().getOfflinePlayer(entry.counterpartUuid());
        return resolvePlayerName(counterpart);
    }

    private static String resolvePlayerName(OfflinePlayer player) {
        String name = player.getName();
        return name == null || name.isBlank() ? player.getUniqueId().toString() : name;
    }

    static Material getFilterMaterial(AuditLogFilter filter) {
        return switch (filter) {
            case ALL -> Material.COMPASS;
            case DEPOSIT -> Material.SUNFLOWER;
            case WITHDRAW -> Material.REDSTONE;
            case PAYMENT_RECEIVED -> Material.BOOK;
            case PAYMENT_SENT -> Material.PAPER;
            case SHOP_SELL -> Material.GOLD_INGOT;
            case SHOP_BUY -> Material.CHEST;
        };
    }

    static String getFilterLabel(TNexus plugin, AuditLogFilter filter) {
        return switch (filter) {
            case ALL -> plugin.getMessageConfig().getMessage("audit.filter.label.all");
            case DEPOSIT -> plugin.getMessageConfig().getMessage("audit.filter.label.deposit");
            case WITHDRAW -> plugin.getMessageConfig().getMessage("audit.filter.label.withdraw");
            case PAYMENT_RECEIVED -> plugin.getMessageConfig().getMessage("audit.filter.label.payment-received");
            case PAYMENT_SENT -> plugin.getMessageConfig().getMessage("audit.filter.label.payment-sent");
            case SHOP_SELL -> plugin.getMessageConfig().getMessage("audit.filter.label.shop-sell");
            case SHOP_BUY -> plugin.getMessageConfig().getMessage("audit.filter.label.shop-buy");
        };
    }

    private static String getFilterColor(AuditLogFilter filter) {
        return switch (filter) {
            case ALL -> "&f";
            case DEPOSIT, PAYMENT_RECEIVED, SHOP_SELL -> "&a";
            case WITHDRAW, PAYMENT_SENT, SHOP_BUY -> "&c";
        };
    }

    private static String getAmountColor(AuditLogFilter filter) {
        return switch (filter) {
            case ALL -> "&f";
            case DEPOSIT, PAYMENT_RECEIVED, SHOP_SELL -> "&a";
            case WITHDRAW, PAYMENT_SENT, SHOP_BUY -> "&c";
        };
    }

    private static String getAmountPrefix(AuditLogFilter filter) {
        return switch (filter) {
            case DEPOSIT, PAYMENT_RECEIVED, SHOP_SELL -> "+";
            case WITHDRAW, PAYMENT_SENT, SHOP_BUY -> "-";
            case ALL -> "";
        };
    }
}
