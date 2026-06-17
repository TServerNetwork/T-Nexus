package network.tserver.tnexus.manager.recap.time;

/**
 * Supplies internal recap timestamps as epoch microseconds.
 */
public interface TimeProvider {

    /**
     * Returns the current timestamp in epoch microseconds.
     *
     * @return current epoch-microsecond timestamp
     */
    long nowMicros();
}
