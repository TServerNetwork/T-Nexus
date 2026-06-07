package network.tserver.tnexus.database.repository;

/**
 * Immutable delta for per-material pickup and drop item statistics.
 *
 * @param pickupCount picked up item count delta
 * @param dropCount dropped item count delta
 */
public record ItemStatsDelta(int pickupCount, int dropCount) {

    /**
     * Returns a new delta with the pickup count incremented by the given amount.
     *
     * @param amount pickup amount
     * @return updated delta
     */
    public ItemStatsDelta addPickup(int amount) {
        return new ItemStatsDelta(this.pickupCount + amount, this.dropCount);
    }

    /**
     * Returns a new delta with the drop count incremented by the given amount.
     *
     * @param amount drop amount
     * @return updated delta
     */
    public ItemStatsDelta addDrop(int amount) {
        return new ItemStatsDelta(this.pickupCount, this.dropCount + amount);
    }
}
