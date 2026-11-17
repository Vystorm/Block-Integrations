package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FurnaceUpgradeStatsTest {
    @Test
    void levelTenProvidesConfiguredMaximumEffects() {
        FurnaceUpgradeStats stats = new FurnaceUpgradeStats(0.12, 0.10, 0.025);

        assertEquals(2.2, stats.speedMultiplier(10), 0.0001);
        assertEquals(91, stats.cookTime(200, 10));
        assertEquals(2.0, stats.fuelMultiplier(10), 0.0001);
        assertEquals(0.25, stats.yieldChance(10), 0.0001);
    }

    @Test
    void yieldChanceIsAlwaysSafe() {
        FurnaceUpgradeStats stats = new FurnaceUpgradeStats(0.12, 0.10, 0.25);
        assertEquals(0.0, stats.yieldChance(-1));
        assertEquals(1.0, stats.yieldChance(10));
    }
}
