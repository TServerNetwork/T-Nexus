package network.tserver.tnexus.manager.recap.config;

import java.util.Objects;
import network.tserver.tnexus.manager.recap.model.AutoJoinPolicyType;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable runtime configuration for one combat recap session.
 *
 * @param autoJoinPolicyType auto-join policy for unassigned entities
 * @param fixedJoinTeamId fixed team id used by the fixed-team join policy
 * @param recordFriendlyFire whether friendly fire should be recorded
 * @param recordSelfDamage whether self-damage should be recorded
 * @param timeoutMicros session timeout in epoch microseconds
 * @param timelineBucketSizeMicros timeline bucket size in microseconds
 * @param maxMembers maximum tracked members
 * @param maxDamagePairs maximum tracked attacker-victim pairs
 * @param maxDamageEvents maximum persisted damage event records
 * @param maxTimelineBuckets maximum timeline aggregation buckets
 * @param damageEventLogEnabled whether ordered damage-event logging is enabled
 * @param timelineEnabled whether timeline aggregation is enabled
 */
public record RecapSessionConfig(
        AutoJoinPolicyType autoJoinPolicyType,
        @Nullable String fixedJoinTeamId,
        boolean recordFriendlyFire,
        boolean recordSelfDamage,
        long timeoutMicros,
        long timelineBucketSizeMicros,
        int maxMembers,
        int maxDamagePairs,
        int maxDamageEvents,
        int maxTimelineBuckets,
        boolean damageEventLogEnabled,
        boolean timelineEnabled) {

    /**
     * Creates a validated recap-session configuration.
     */
    public RecapSessionConfig {
        autoJoinPolicyType = Objects.requireNonNull(autoJoinPolicyType, "autoJoinPolicyType");
        if (autoJoinPolicyType == AutoJoinPolicyType.FIXED_TEAM) {
            if (fixedJoinTeamId == null || fixedJoinTeamId.isBlank()) {
                throw new IllegalArgumentException("fixedJoinTeamId is required for FIXED_TEAM");
            }
        } else if (fixedJoinTeamId != null && fixedJoinTeamId.isBlank()) {
            throw new IllegalArgumentException("fixedJoinTeamId must not be blank");
        }

        validateNonNegative(timeoutMicros, "timeoutMicros");
        validatePositive(timelineBucketSizeMicros, "timelineBucketSizeMicros");
        validateNonNegative(maxMembers, "maxMembers");
        validateNonNegative(maxDamagePairs, "maxDamagePairs");
        validateNonNegative(maxDamageEvents, "maxDamageEvents");
        validateNonNegative(maxTimelineBuckets, "maxTimelineBuckets");
    }

    private static void validateNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void validateNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void validatePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
