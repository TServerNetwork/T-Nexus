package network.tserver.tnexus.database.repository;

/**
 * Represents queued placed and broken block deltas for a single material.
 *
 * @param placedCount placed block count delta
 * @param brokenCount broken block count delta
 */
public record BlockStatsDelta(int placedCount, int brokenCount) {

    /**
     * Returns a new delta with the given placed increment applied.
     *
     * @param increment placed count increment
     * @return updated delta
     */
    public BlockStatsDelta addPlaced(int increment) {
        return new BlockStatsDelta(this.placedCount + increment, this.brokenCount);
    }

    /**
     * Returns a new delta with the given broken increment applied.
     *
     * @param increment broken count increment
     * @return updated delta
     */
    public BlockStatsDelta addBroken(int increment) {
        return new BlockStatsDelta(this.placedCount, this.brokenCount + increment);
    }
}
