package network.tserver.tnexus.manager.recap.model;

/**
 * Reason why a combat recap session ended.
 */
public enum RecapEndReason {
    TEAM_DEFEATED,
    MANUAL_STOP,
    TIMEOUT,
    SERVER_SHUTDOWN
}
