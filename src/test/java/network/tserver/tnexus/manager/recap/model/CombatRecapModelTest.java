package network.tserver.tnexus.manager.recap.model;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CombatRecapModelTest {

    @Test
    void shouldRetainDamageEventSequenceAndTimestamp() {
        UUID attackerId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();
        DamageEventRecord record = new DamageEventRecord(
                42L,
                9_876_543L,
                attackerId,
                victimId,
                "red",
                "blue",
                6.25D);

        assertEquals(42L, record.sequence());
        assertEquals(9_876_543L, record.occurredAtMicros());
        assertEquals(attackerId, record.attackerUuid());
        assertEquals(victimId, record.victimUuid());
    }

    @Test
    void shouldIdentifyTimelineKeysByBucketAndEntityPair() {
        UUID attackerId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();

        TimelineKey first = new TimelineKey(3L, attackerId, victimId);
        TimelineKey same = new TimelineKey(3L, attackerId, victimId);
        TimelineKey differentBucket = new TimelineKey(4L, attackerId, victimId);

        assertEquals(first, same);
        assertNotEquals(first, differentBucket);
    }

    @Test
    void shouldAggregateTimelineDamageAndHitCount() {
        DamageTimelineBucket bucket = new DamageTimelineBucket(
                2L,
                2_000L,
                2_500L,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "red",
                "blue");

        bucket.addDamage(3.5D);
        bucket.addDamage(4.0D);

        assertEquals(7.5D, bucket.getDamage());
        assertEquals(2, bucket.getHitCount());
    }
}
