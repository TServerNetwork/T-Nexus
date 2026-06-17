package network.tserver.tnexus.manager.recap.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Aggregated damage statistics for one attacker-to-victim pair.
 */
public final class DamageStats {

    private final UUID attackerUuid;
    private final UUID victimUuid;
    private final String attackerTeamId;
    private final String victimTeamId;
    private double totalDamage;
    private int hitCount;
    private double maxSingleDamage;
    private long firstHitAtMicros;
    private long lastHitAtMicros;

    /**
     * Creates a new empty damage aggregate.
     *
     * @param attackerUuid attacker UUID
     * @param victimUuid victim UUID
     * @param attackerTeamId attacker team identifier
     * @param victimTeamId victim team identifier
     */
    public DamageStats(UUID attackerUuid, UUID victimUuid, String attackerTeamId, String victimTeamId) {
        this.attackerUuid = Objects.requireNonNull(attackerUuid, "attackerUuid");
        this.victimUuid = Objects.requireNonNull(victimUuid, "victimUuid");
        this.attackerTeamId = Objects.requireNonNull(attackerTeamId, "attackerTeamId");
        this.victimTeamId = Objects.requireNonNull(victimTeamId, "victimTeamId");
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
     * Returns the total accumulated damage.
     *
     * @return total damage
     */
    public double getTotalDamage() {
        return this.totalDamage;
    }

    /**
     * Returns the number of hits contributing to the aggregate.
     *
     * @return hit count
     */
    public int getHitCount() {
        return this.hitCount;
    }

    /**
     * Returns the maximum single-hit damage in the aggregate.
     *
     * @return maximum single-hit damage
     */
    public double getMaxSingleDamage() {
        return this.maxSingleDamage;
    }

    /**
     * Returns the first-hit timestamp in epoch microseconds.
     *
     * @return first-hit timestamp
     */
    public long getFirstHitAtMicros() {
        return this.firstHitAtMicros;
    }

    /**
     * Returns the last-hit timestamp in epoch microseconds.
     *
     * @return last-hit timestamp
     */
    public long getLastHitAtMicros() {
        return this.lastHitAtMicros;
    }

    /**
     * Adds one damage event into this aggregate.
     *
     * @param damage damage amount
     * @param occurredAtMicros event timestamp in epoch microseconds
     */
    public void addDamage(double damage, long occurredAtMicros) {
        validateDamage(damage);
        if (occurredAtMicros < 0L) {
            throw new IllegalArgumentException("occurredAtMicros must be non-negative");
        }

        if (this.hitCount == 0) {
            this.firstHitAtMicros = occurredAtMicros;
        }

        this.lastHitAtMicros = occurredAtMicros;
        this.totalDamage += damage;
        this.hitCount++;
        this.maxSingleDamage = Math.max(this.maxSingleDamage, damage);
    }

    private static void validateDamage(double damage) {
        if (Double.isNaN(damage) || Double.isInfinite(damage) || damage < 0.0D) {
            throw new IllegalArgumentException("damage must be a finite non-negative value");
        }
    }
}
