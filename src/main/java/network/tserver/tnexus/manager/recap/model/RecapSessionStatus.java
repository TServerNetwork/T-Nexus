package network.tserver.tnexus.manager.recap.model;

/**
 * Lifecycle status of one combat recap session.
 */
public enum RecapSessionStatus {
    CREATED,
    ACTIVE,
    CLOSING,
    PERSISTING,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    FAILED_SAVE
}
