package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FurnaceMetricsTest {
    @Test
    void reportsAcceptedEventUnitsAndExpiresOutsideSlidingWindow() {
        long[] now = {1_000L};
        FurnaceMetrics metrics = new FurnaceMetrics(() -> now[0]);
        BlockKey key = new BlockKey(UUID.randomUUID(), 35, 70, -17);

        metrics.fuel(key);
        metrics.smelt(key, 4, true);
        assertEquals(new FurnaceMetrics.Snapshot(1, 1, 4, 1), metrics.snapshot(key));

        now[0] += FurnaceMetrics.WINDOW_MILLIS + 1;
        assertEquals(new FurnaceMetrics.Snapshot(0, 0, 0, 0), metrics.snapshot(key));
    }

    @Test
    void removingChunkAndBlockDropsOnlyMatchingTransientMetrics() {
        FurnaceMetrics metrics = new FurnaceMetrics(() -> 1_000L);
        UUID world = UUID.randomUUID();
        BlockKey inChunk = new BlockKey(world, 35, 70, -17);
        BlockKey otherChunk = new BlockKey(world, 16, 70, -17);
        metrics.fuel(inChunk);
        metrics.smelt(otherChunk, 2, false);

        metrics.removeChunk(world, 2, -2);
        assertEquals(FurnaceMetrics.Snapshot.EMPTY, metrics.snapshot(inChunk));
        assertEquals(1, metrics.snapshot(otherChunk).smeltCycles());
        metrics.remove(otherChunk);
        assertEquals(FurnaceMetrics.Snapshot.EMPTY, metrics.snapshot(otherChunk));
    }
}
