package network.tserver.tnexus.manager.recap.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Aggregated timeline bucket for graph-oriented damage visualization.
 */
public final class DamageTimelineBucket {

    private final long bucketIndex;
    private final long bucketStartMicros;
    private final long bucketEndMicros;
    private final UUID attackerUuid;
    private final UUID victimUuid;
    private final String attackerTeamId;
    private final String victimTeamId;
    private double damage;
    private int hitCount;

    /**
     * Creates an empty timeline bucket.
     *
     * @param bucketIndex bucket index relative to the session start
     * @param bucketStartMicros bucket inclusive start timestamp
     * @param bucketEndMicros bucket exclusive end timestamp
     * @param attackerUuid attacker UUID
     * @param victimUuid victim UUID
     * @param attackerTeamId attacker team identifier
     * @param victimTeamId victim team identifier
     */
    public DamageTimelineBucket(
            long bucketIndex,
            long bucketStartMicros,
            long bucketEndMicros,
            UUID attackerUuid,
            UUID victimUuid,
            String attackerTeamId,
            String victimTeamId) {
        if (bucketIndex < 0L) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (bucketStartMicros < 0L) {
            throw new IllegalArgumentException("bucketStartMicros must be non-negative");
        }
        if (bucketEndMicros <= bucketStartMicros) {
            throw new IllegalArgumentException("bucketEndMicros must be greater than bucketStartMicros");
        }

        this.bucketIndex = bucketIndex;
        this.bucketStartMicros = bucketStartMicros;
        this.bucketEndMicros = bucketEndMicros;
        this.attackerUuid = Objects.requireNonNull(attackerUuid, "attackerUuid");
        this.victimUuid = Objects.requireNonNull(victimUuid, "victimUuid");
        this.attackerTeamId = Objects.requireNonNull(attackerTeamId, "attackerTeamId");
        this.victimTeamId = Objects.requireNonNull(victimTeamId, "victimTeamId");
    }

    /**
     * Returns the bucket index.
     *
     * @return bucket index
     */
    public long getBucketIndex() {
        return this.bucketIndex;
    }

    /**
     * Returns the inclusive bucket start timestamp in epoch microseconds.
     *
     * @return bucket start timestamp
     */
    public long getBucketStartMicros() {
        return this.bucketStartMicros;
    }

    /**
     * Returns the exclusive bucket end timestamp in epoch microseconds.
     *
     * @return bucket end timestamp
     */
    public long getBucketEndMicros() {
        return this.bucketEndMicros;
    }

    /**
     * Returns the attacker UUID.
     *
     * @return attacker UUID
     */
    public UUID getAttackerUuid() {
        return this.attackerUuid;
    }

    /**
     * Returns the victim UUID.
     *
     * @return victim UUID
     */
    public UUID getVictimUuid() {
        return this.victimUuid;
    }

    /**
     * Returns the attacker team identifier.
     *
     * @return attacker team identifier
     */
    public String getAttackerTeamId() {
        return this.attackerTeamId;
    }

    /**
     * Returns the victim team identifier.
     *
     * @return victim team identifier
     */
    public String getVictimTeamId() {
        return this.victimTeamId;
    }

    /**
     * Returns the aggregated damage total.
     *
     * @return aggregated damage total
     */
    public double getDamage() {
        return this.damage;
    }

    /**
     * Returns the hit count captured in the bucket.
     *
     * @return hit count
     */
    public int getHitCount() {
        return this.hitCount;
    }

    /**
     * Adds one hit into the bucket aggregate.
     *
     * @param damage damage amount
     */
    public void addDamage(double damage) {
        if (Double.isNaN(damage) || Double.isInfinite(damage) || damage < 0.0D) {
            throw new IllegalArgumentException("damage must be a finite non-negative value");
        }
        this.damage += damage;
        this.hitCount++;
    }
}
