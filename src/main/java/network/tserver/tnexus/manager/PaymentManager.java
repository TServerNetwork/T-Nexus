package network.tserver.tnexus.manager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.MessageConfig;
import network.tserver.tnexus.database.repository.PayQueueRepository;
import network.tserver.tnexus.database.repository.PayQueueRepository.PayQueueEntry;
import network.tserver.tnexus.database.repository.TransactionRepository;
import network.tserver.tnexus.database.repository.TransactionRepository.AuditRecord;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import network.tserver.tnexus.util.CurrencyFormatter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Coordinates queued payment confirmation, transfer execution, and notifications.
 */
public final class PaymentManager {

    static final Duration TOKEN_TTL = Duration.ofSeconds(30);

    private final TNexus plugin;
    private final EconomyManager economyManager;
    private final MessageConfig messageConfig;
    private final PayQueueRepository payQueueRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final Map<UUID, List<PendingNotification>> pendingNotifications;

    /**
     * Creates a new payment manager.
     *
     * @param plugin plugin instance
     * @param economyManager economy manager
     * @param payQueueRepository pay queue repository
     * @param transactionRepository transaction repository
     */
    public PaymentManager(
            TNexus plugin,
            EconomyManager economyManager,
            PayQueueRepository payQueueRepository,
            TransactionRepository transactionRepository) {
        this(
                plugin,
                economyManager,
                payQueueRepository,
                transactionRepository,
                Clock.systemUTC(),
                new ConcurrentHashMap<>());
    }

    PaymentManager(
            TNexus plugin,
            EconomyManager economyManager,
            PayQueueRepository payQueueRepository,
            TransactionRepository transactionRepository,
            Clock clock,
            Map<UUID, List<PendingNotification>> pendingNotifications) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economyManager = Objects.requireNonNull(economyManager, "economyManager");
        this.messageConfig = plugin.getMessageConfig();
        this.payQueueRepository = Objects.requireNonNull(payQueueRepository, "payQueueRepository");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pendingNotifications = Objects.requireNonNull(pendingNotifications, "pendingNotifications");
    }

    /**
     * Queues a payment confirmation token for the sender.
     *
     * @param sender sender player
     * @param target target player
     * @param amount payment amount
     * @return future resolving to a queued entry
     */
    public CompletableFuture<QueueResult> queuePayment(Player sender, OfflinePlayer target, double amount) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(target, "target");

        if (!Double.isFinite(amount) || amount <= 0.0D) {
            return CompletableFuture.completedFuture(QueueResult.invalidAmount());
        }

        return this.economyManager.has(sender.getUniqueId(), amount)
                .thenCompose(hasFunds -> {
                    if (!hasFunds) {
                        return CompletableFuture.completedFuture(QueueResult.insufficientFunds());
                    }
                    PayQueueEntry entry = PayQueueEntry.create(
                            sender.getUniqueId(),
                            target.getUniqueId(),
                            amount,
                            this.clock.instant());
                    return this.payQueueRepository.insert(entry)
                            .thenApply(ignored -> QueueResult.queued(entry));
                });
    }

    /**
     * Cancels a queued payment token.
     *
     * @param sender sender player
     * @param token queued token
     * @return future resolving to a cancel result
     */
    public CompletableFuture<ConfirmationResult> cancelPayment(Player sender, String token) {
        return loadOwnedToken(sender, token).thenCompose(optionalEntry -> {
            if (optionalEntry.isEmpty()) {
                return CompletableFuture.completedFuture(ConfirmationResult.invalid());
            }

            PayQueueEntry entry = optionalEntry.get();
            if (isExpired(entry)) {
                return this.payQueueRepository.deleteByToken(entry.token())
                        .thenApply(deleted -> ConfirmationResult.expired());
            }

            return this.payQueueRepository.deleteByToken(entry.token())
                    .thenApply(deleted -> deleted ? ConfirmationResult.cancelled() : ConfirmationResult.invalid());
        });
    }

    /**
     * Confirms a queued payment and executes the transfer.
     *
     * @param sender sender player
     * @param token queued token
     * @return future resolving to a confirmation result
     */
    public CompletableFuture<ConfirmationResult> confirmPayment(Player sender, String token) {
        return loadOwnedToken(sender, token).thenCompose(optionalEntry -> {
            if (optionalEntry.isEmpty()) {
                return CompletableFuture.completedFuture(ConfirmationResult.invalid());
            }

            PayQueueEntry entry = optionalEntry.get();
            if (isExpired(entry)) {
                return this.payQueueRepository.deleteByToken(entry.token())
                        .thenApply(deleted -> ConfirmationResult.expired());
            }

            return this.payQueueRepository.deleteByToken(entry.token()).thenCompose(deleted -> {
                if (!deleted) {
                    return CompletableFuture.completedFuture(ConfirmationResult.invalid());
                }
                return executeTransfer(sender, entry);
            });
        });
    }

    /**
     * Flushes pending offline notifications for a player.
     *
     * @param player joining player
     */
    public void deliverPendingNotifications(Player player) {
        List<PendingNotification> notifications = this.pendingNotifications.remove(player.getUniqueId());
        if (notifications == null || notifications.isEmpty()) {
            return;
        }

        for (PendingNotification notification : notifications) {
            this.messageConfig.sendMessage(
                    player,
                    "economy.pay.receiver-offline-delivery",
                    notification.senderName(),
                    CurrencyFormatter.format(this.plugin, notification.amount()));
        }
    }

    private CompletableFuture<Optional<PayQueueEntry>> loadOwnedToken(Player sender, String token) {
        if (token == null || token.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return this.payQueueRepository.findByToken(token).thenApply(optionalEntry ->
                optionalEntry.filter(entry -> entry.senderUuid().equals(sender.getUniqueId())));
    }

    private boolean isExpired(PayQueueEntry entry) {
        return entry.createdAt().plus(TOKEN_TTL).isBefore(this.clock.instant());
    }

    private CompletableFuture<ConfirmationResult> executeTransfer(Player sender, PayQueueEntry entry) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(entry.targetUuid());
        return this.economyManager.withdraw(sender.getUniqueId(), entry.amount()).thenCompose(withdrawn -> {
            if (!withdrawn) {
                return CompletableFuture.completedFuture(ConfirmationResult.insufficientFunds());
            }

            return this.economyManager.deposit(entry.targetUuid(), entry.amount()).thenCompose(deposited -> {
                if (!deposited) {
                    return this.economyManager.deposit(sender.getUniqueId(), entry.amount())
                            .thenApply(refunded -> ConfirmationResult.failed());
                }

                CompletableFuture<Double> senderBalanceFuture = this.economyManager.getBalance(sender.getUniqueId());
                CompletableFuture<Double> targetBalanceFuture = this.economyManager.getBalance(entry.targetUuid());
                return senderBalanceFuture.thenCombine(targetBalanceFuture, Balances::new)
                        .thenCompose(balances -> recordAudit(sender, target, entry, balances)
                                .handle((ignored, throwable) -> {
                                    if (throwable != null) {
                                        this.plugin.getLogger().log(
                                                Level.SEVERE,
                                                "Failed to write payment audit records.",
                                                throwable);
                                    }
                                    queueOfflineNotificationIfRequired(target, sender.getName(), entry.amount());
                                    return ConfirmationResult.success(target, entry.amount());
                                }));
            });
        });
    }

    private CompletableFuture<Void> recordAudit(
            Player sender,
            OfflinePlayer target,
            PayQueueEntry entry,
            Balances balances) {
        String targetName = resolveName(target);
        String senderName = sender.getName();
        CompletableFuture<Void> senderAudit = this.transactionRepository.insert(new AuditRecord(
                sender.getUniqueId(),
                TransactionType.PAYMENT_SENT,
                entry.amount(),
                balances.senderBalance(),
                "Payment sent to " + targetName,
                target.getUniqueId()));
        CompletableFuture<Void> receiverAudit = this.transactionRepository.insert(new AuditRecord(
                target.getUniqueId(),
                TransactionType.PAYMENT_RECEIVED,
                entry.amount(),
                balances.targetBalance(),
                "Payment received from " + senderName,
                sender.getUniqueId()));
        return CompletableFuture.allOf(senderAudit, receiverAudit);
    }

    private void queueOfflineNotificationIfRequired(OfflinePlayer target, String senderName, double amount) {
        if (target.isOnline()) {
            return;
        }
        this.pendingNotifications.compute(target.getUniqueId(), (ignored, existing) -> {
            List<PendingNotification> notifications = existing == null ? new java.util.ArrayList<>() : existing;
            notifications.add(new PendingNotification(senderName, amount));
            return notifications;
        });
    }

    /**
     * Returns a display-safe player name.
     *
     * @param player player
     * @return resolved display name
     */
    public static String resolveName(OfflinePlayer player) {
        String name = player.getName();
        return name == null || name.isBlank() ? player.getUniqueId().toString() : name;
    }

    /**
     * Result of queueing a payment confirmation.
     *
     * @param status queue status
     * @param entry queued entry when available
     */
    public record QueueResult(QueueStatus status, PayQueueEntry entry) {

        static QueueResult queued(PayQueueEntry entry) {
            return new QueueResult(QueueStatus.QUEUED, entry);
        }

        static QueueResult invalidAmount() {
            return new QueueResult(QueueStatus.INVALID_AMOUNT, null);
        }

        static QueueResult insufficientFunds() {
            return new QueueResult(QueueStatus.INSUFFICIENT_FUNDS, null);
        }

        /**
         * Returns whether queueing succeeded.
         *
         * @return {@code true} when queued
         */
        public boolean isQueued() {
            return this.status == QueueStatus.QUEUED && this.entry != null;
        }
    }

    /**
     * Result of confirming or cancelling a payment token.
     *
     * @param status confirmation status
     * @param target target player when relevant
     * @param amount amount when relevant
     */
    public record ConfirmationResult(ConfirmationStatus status, OfflinePlayer target, double amount) {

        static ConfirmationResult success(OfflinePlayer target, double amount) {
            return new ConfirmationResult(ConfirmationStatus.SUCCESS, target, amount);
        }

        static ConfirmationResult cancelled() {
            return new ConfirmationResult(ConfirmationStatus.CANCELLED, null, 0.0D);
        }

        static ConfirmationResult invalid() {
            return new ConfirmationResult(ConfirmationStatus.INVALID, null, 0.0D);
        }

        static ConfirmationResult expired() {
            return new ConfirmationResult(ConfirmationStatus.EXPIRED, null, 0.0D);
        }

        static ConfirmationResult insufficientFunds() {
            return new ConfirmationResult(ConfirmationStatus.INSUFFICIENT_FUNDS, null, 0.0D);
        }

        static ConfirmationResult failed() {
            return new ConfirmationResult(ConfirmationStatus.FAILED, null, 0.0D);
        }
    }

    /**
     * Queue operation status values.
     */
    public enum QueueStatus {
        QUEUED,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS
    }

    /**
     * Confirmation operation status values.
     */
    public enum ConfirmationStatus {
        SUCCESS,
        CANCELLED,
        INVALID,
        EXPIRED,
        INSUFFICIENT_FUNDS,
        FAILED
    }

    private record Balances(double senderBalance, double targetBalance) {
    }

    private record PendingNotification(String senderName, double amount) {
    }
}
