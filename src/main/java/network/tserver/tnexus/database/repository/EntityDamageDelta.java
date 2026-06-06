package network.tserver.tnexus.database.repository;

/**
 * Represents pending dealt and taken damage deltas for an entity identifier.
 *
 * @param damageDealt dealt damage delta
 * @param damageTaken taken damage delta
 */
public record EntityDamageDelta(double damageDealt, double damageTaken) {

    /**
     * Returns a new delta with additional dealt damage applied.
     *
     * @param amount dealt damage to add
     * @return updated delta
     */
    public EntityDamageDelta addDealt(double amount) {
        return new EntityDamageDelta(this.damageDealt + amount, this.damageTaken);
    }

    /**
     * Returns a new delta with additional taken damage applied.
     *
     * @param amount taken damage to add
     * @return updated delta
     */
    public EntityDamageDelta addTaken(double amount) {
        return new EntityDamageDelta(this.damageDealt, this.damageTaken + amount);
    }
}
