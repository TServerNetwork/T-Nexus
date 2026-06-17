package network.tserver.tnexus.manager.recap.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MonotonicMicrosTimeProviderTest {

    @Test
    void shouldIncreaseMonotonically() {
        MonotonicMicrosTimeProvider provider = new MonotonicMicrosTimeProvider();

        long first = provider.nowMicros();
        long second = provider.nowMicros();
        long third = provider.nowMicros();

        assertTrue(second > first);
        assertTrue(third > second);
    }

    @Test
    void shouldIncreaseWithinSameMillisecond() {
        MonotonicMicrosTimeProvider provider = new MonotonicMicrosTimeProvider();

        long previous = provider.nowMicros();
        for (int index = 0; index < 128; index++) {
            long current = provider.nowMicros();
            assertTrue(current > previous);
            previous = current;
        }
    }
}
