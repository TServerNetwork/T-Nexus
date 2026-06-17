package network.tserver.tnexus.manager.recap.time;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Time provider that guarantees strictly increasing epoch-microsecond values.
 */
public final class MonotonicMicrosTimeProvider implements TimeProvider {

    private final AtomicLong lastMicros = new AtomicLong();

    /**
     * Returns a strictly increasing epoch-microsecond timestamp.
     *
     * @return current epoch-microsecond timestamp
     */
    @Override
    public long nowMicros() {
        long current = System.currentTimeMillis() * 1_000L;

        while (true) {
            long last = this.lastMicros.get();
            long next = Math.max(current, last + 1L);

            if (this.lastMicros.compareAndSet(last, next)) {
                return next;
            }
        }
    }
}
