package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ComboScalingTest {
    @Test
    void rangeIsCappedAtSafeSearchRadius() {
        assertEquals(20, ComboScaling.range(10, 10, 1));
        assertEquals(13, ComboScaling.range(3, 10, 1));
        assertEquals(32, ComboScaling.range(30, 10, 1));
    }

    @Test
    void floatDurationStartsAtFiveAndReachesTenSeconds() {
        assertEquals(100, ComboScaling.duration(100, 0, 10));
        assertEquals(200, ComboScaling.duration(100, 10, 10));
    }

    @Test
    void cooldownIsHalvedAtMaximumLevel() {
        assertEquals(60L, ComboScaling.cooldown(60L, 0, 0.05D, 5L));
        assertEquals(30L, ComboScaling.cooldown(60L, 10, 0.05D, 5L));
        assertEquals(5L, ComboScaling.cooldown(4L, 10, 0.05D, 5L));
    }

    @Test
    void powerIncreasesLinearly() {
        assertEquals(2.05D, ComboScaling.power(1.25D, 10, 0.08D), 0.000001D);
        assertEquals(6.0D, ComboScaling.power(1.0D, 10, 0.50D), 0.000001D);
    }

    @Test
    void minimumDistanceAllowsExactlyTheConfiguredDistance() {
        assertTrue(ComboScaling.tooClose(0, 64, 0, 9, 64, 0, 10));
        assertFalse(ComboScaling.tooClose(0, 64, 0, 10, 64, 0, 10));
        assertFalse(ComboScaling.tooClose(0, 64, 0, 11, 64, 0, 10));
    }
}
