package network.tserver.tnexus.manager.recap.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Short-lived hit trace used for kill and assist attribution.
 *
 * @param attackerUuid attacker UUID
 * @param victimUuid victim UUID
 * @param damage effective damage amount
 * @param occurredAtMicros event timestamp in epoch microseconds
 */
public record HitTrace(
        UUID attackerUuid,
        UUID victimUuid,
        double damage,
        long occurredAtMicros) {

    /**
     * Creates a validated hit trace.
     */
    public HitTrace {
        attackerUuid = Objects.requireNonNull(attackerUuid, "attackerUuid");
        victimUuid = Objects.requireNonNull(victimUuid, "victimUuid");
        if (Double.isNaN(damage) || Double.isInfinite(damage) || damage < 0.0D) {
            throw new IllegalArgumentException("damage must be a finite non-negative value");
        }
        if (occurredAtMicros < 0L) {
            throw new IllegalArgumentException("occurredAtMicros must be non-negative");
        }
    }
}
