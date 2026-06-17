package network.tserver.tnexus.manager.recap.model;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageStatsTest {

    @Test
    void shouldAggregateDamageCountersAndTimestamps() {
        DamageStats stats = new DamageStats(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "red",
                "blue");

        stats.addDamage(4.5D, 1_000L);
        stats.addDamage(8.0D, 1_500L);
        stats.addDamage(3.0D, 1_750L);

        assertEquals(15.5D, stats.getTotalDamage());
        assertEquals(3, stats.getHitCount());
        assertEquals(8.0D, stats.getMaxSingleDamage());
        assertEquals(1_000L, stats.getFirstHitAtMicros());
        assertEquals(1_750L, stats.getLastHitAtMicros());
    }
}
