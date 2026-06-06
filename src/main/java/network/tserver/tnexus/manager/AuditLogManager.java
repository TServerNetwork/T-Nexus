package network.tserver.tnexus.manager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.database.repository.TransactionRepository;
import network.tserver.tnexus.database.repository.TransactionRepository.AuditEntry;
import network.tserver.tnexus.database.repository.TransactionRepository.AuditRecord;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import network.tserver.tnexus.gui.audit.AuditLogGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Coordinates audit-log retrieval and GUI opening.
 */
public final class AuditLogManager {

    private final TNexus plugin;
    private final TransactionRepository transactionRepository;

    /**
     * Creates a new audit-log manager.
     *
     * @param plugin plugin instance
     */
    public AuditLogManager(TNexus plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.transactionRepository = new TransactionRepository(plugin.getDatabaseManager());
    }

    /**
     * Loads audit history for a player and filter.
     *
     * @param playerUuid target player UUID
     * @param filter active filter
     * @return future resolving to audit entries
     */
    public CompletableFuture<List<AuditEntry>> getHistory(UUID playerUuid, AuditLogFilter filter) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(filter, "filter");
        return this.transactionRepository.findByPlayerUuid(playerUuid, filter.getTransactionType());
    }

    /**
     * Records an audit entry for a player's transaction history.
     *
     * @param playerUuid affected player UUID
     * @param type transaction type
     * @param amount transaction amount
     * @param balanceAfter balance after the transaction
     * @param description audit description
     * @param counterpartUuid optional counterpart UUID
     * @return future resolving when the record has been persisted
     */
    public CompletableFuture<Void> recordEntry(
            UUID playerUuid,
            TransactionType type,
            double amount,
            double balanceAfter,
            String description,
            @Nullable UUID counterpartUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(description, "description");
        return this.transactionRepository.insert(new AuditRecord(
                playerUuid,
                type,
                amount,
                balanceAfter,
                description,
                counterpartUuid));
    }

    /**
     * Opens the audit history GUI for a viewer.
     *
     * @param viewer player opening the GUI
     * @param targetPlayer target player whose history is shown
     * @param filter active filter
     */
    public void openHistoryViewer(Player viewer, OfflinePlayer targetPlayer, AuditLogFilter filter) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(targetPlayer, "targetPlayer");
        Objects.requireNonNull(filter, "filter");
        getHistory(targetPlayer.getUniqueId(), filter)
                .whenComplete((entries, throwable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (throwable != null) {
                        this.plugin.getMessageConfig().sendMessage(viewer, "audit.history.load-failed");
                        return;
                    }
                    new AuditLogGui(this.plugin, this, viewer, targetPlayer, filter, entries).open();
                }));
    }
}
