package network.tserver.tnexus.manager;

import java.util.Objects;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import org.jetbrains.annotations.Nullable;

/**
 * Supported audit-log filters.
 */
public enum AuditLogFilter {
    ALL(null),
    DEPOSIT(TransactionType.DEPOSIT),
    WITHDRAW(TransactionType.WITHDRAW),
    PAYMENT_RECEIVED(TransactionType.PAYMENT_RECEIVED),
    PAYMENT_SENT(TransactionType.PAYMENT_SENT),
    SHOP_SELL(TransactionType.SHOP_SELL),
    SHOP_BUY(TransactionType.SHOP_BUY);

    private final @Nullable TransactionType transactionType;

    AuditLogFilter(@Nullable TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    /**
     * Returns the mapped transaction type, or {@code null} for the all filter.
     *
     * @return mapped transaction type or {@code null}
     */
    public @Nullable TransactionType getTransactionType() {
        return this.transactionType;
    }

    /**
     * Resolves the filter matching a persisted transaction type.
     *
     * @param transactionType transaction type
     * @return matching filter
     */
    public static AuditLogFilter fromTransactionType(TransactionType transactionType) {
        Objects.requireNonNull(transactionType, "transactionType");
        for (AuditLogFilter filter : values()) {
            if (transactionType.equals(filter.transactionType)) {
                return filter;
            }
        }
        throw new IllegalArgumentException("Unsupported transaction type: " + transactionType);
    }
}
