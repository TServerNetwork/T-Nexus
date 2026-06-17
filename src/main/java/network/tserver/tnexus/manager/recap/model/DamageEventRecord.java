package network.tserver.tnexus.manager.recap.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Ordered per-hit damage record captured within a recap session.
 *
 * @param sequence per-session sequence number
 * @param occurredAtMicros event timestamp in epoch microseconds
 * @param attackerUuid attacker UUID
 * @param victimUuid victim UUID
 * @param attackerTeamId attacker team identifier
 * @param victimTeamId victim team identifier
 * @param damage effective damage amount
 */
public record DamageEventRecord(
        long sequence,
        long occurredAtMicros,
        UUID attackerUuid,
        UUID victimUuid,
        String attackerTeamId,
        String victimTeamId,
        double damage) {

    /**
     * Creates a validated damage-event record.
     */
    public DamageEventRecord {
        if (sequence < 0L) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
        if (occurredAtMicros < 0L) {
            throw new IllegalArgumentException("occurredAtMicros must be non-negative");
        }
        attackerUuid = Objects.requireNonNull(attackerUuid, "attackerUuid");
        victimUuid = Objects.requireNonNull(victimUuid, "victimUuid");
        attackerTeamId = Objects.requireNonNull(attackerTeamId, "attackerTeamId");
        victimTeamId = Objects.requireNonNull(victimTeamId, "victimTeamId");
        if (Double.isNaN(damage) || Double.isInfinite(damage) || damage < 0.0D) {
            throw new IllegalArgumentException("damage must be a finite non-negative value");
        }
    }
}
