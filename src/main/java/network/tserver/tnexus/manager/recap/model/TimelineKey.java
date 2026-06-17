package network.tserver.tnexus.manager.recap.model;

import java.util.UUID;

/**
 * Identifies one aggregated timeline bucket for an attacker-to-victim pair.
 *
 * @param bucketIndex bucket index relative to the session start
 * @param attackerUuid attacker UUID
 * @param victimUuid victim UUID
 */
public record TimelineKey(
        long bucketIndex,
        UUID attackerUuid,
        UUID victimUuid) {
}
