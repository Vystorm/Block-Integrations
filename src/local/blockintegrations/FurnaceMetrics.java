package local.blockintegrations;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Read-only rolling furnace activity counters. Values count event units, never burn ticks. */
final class FurnaceMetrics {
    static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_TRACKED_FURNACES = 4096;
    private final LongSupplier clock;
    private final Map<BlockKey, Window> windows = new LinkedHashMap<>();

    FurnaceMetrics() { this(() -> System.nanoTime() / 1_000_000L); }

    FurnaceMetrics(LongSupplier clock) { this.clock = clock; }

    void smelt(BlockKey key, int outputItems, boolean yieldBonus) {
        record(key, new Sample(clock.getAsLong(), 1, 0,
                Math.max(0, outputItems), yieldBonus ? 1 : 0));
    }

    void fuel(BlockKey key) { record(key, new Sample(clock.getAsLong(), 0, 1, 0, 0)); }

    Snapshot snapshot(BlockKey key) {
        Window window = windows.get(key);
        if (window == null) {
            return Snapshot.EMPTY;
        }
        prune(window, clock.getAsLong());
        if (window.samples.isEmpty()) {
            windows.remove(key);
            return Snapshot.EMPTY;
        }
        return window.snapshot;
    }

    void remove(BlockKey key) { windows.remove(key); }

    void removeChunk(java.util.UUID worldId, int chunkX, int chunkZ) {
        windows.keySet().removeIf(key -> key.worldId().equals(worldId)
                && (key.x() >> 4) == chunkX && (key.z() >> 4) == chunkZ);
    }

    void clear() { windows.clear(); }

    private void record(BlockKey key, Sample sample) {
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= MAX_TRACKED_FURNACES) {
                windows.remove(windows.keySet().iterator().next());
            }
            window = new Window();
            windows.put(key, window);
        }
        long second = sample.time() / 1_000L;
        Sample bucket = window.samples.peekLast();
        if (bucket != null && bucket.time() / 1_000L == second) {
            window.snapshot = window.snapshot.subtract(bucket);
            bucket = bucket.add(sample);
            window.samples.removeLast();
        } else {
            bucket = sample;
        }
        window.samples.addLast(bucket);
        window.snapshot = window.snapshot.add(bucket);
        prune(window, sample.time());
    }

    private void prune(Window window, long now) {
        long cutoffSecond = (now - WINDOW_MILLIS) / 1_000L;
        while (!window.samples.isEmpty() && window.samples.peekFirst().time() / 1_000L <= cutoffSecond) {
            Sample expired = window.samples.removeFirst();
            window.snapshot = window.snapshot.subtract(expired);
        }
    }

    record Snapshot(long smeltCycles, long fuelItems, long outputItems, long yieldBonusItems) {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0);

        Snapshot add(Sample sample) {
            return new Snapshot(smeltCycles + sample.smelt(), fuelItems + sample.fuel(),
                    outputItems + sample.output(), yieldBonusItems + sample.yieldBonus());
        }

        Snapshot subtract(Sample sample) {
            return new Snapshot(smeltCycles - sample.smelt(), fuelItems - sample.fuel(),
                    outputItems - sample.output(), yieldBonusItems - sample.yieldBonus());
        }
    }

    private record Sample(long time, long smelt, long fuel, long output, long yieldBonus) {
        Sample add(Sample other) {
            return new Sample(time, smelt + other.smelt, fuel + other.fuel,
                    output + other.output, yieldBonus + other.yieldBonus);
        }
    }
    private static final class Window {
        final Deque<Sample> samples = new ArrayDeque<>();
        Snapshot snapshot = Snapshot.EMPTY;
    }
}
