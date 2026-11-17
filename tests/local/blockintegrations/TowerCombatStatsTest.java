package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TowerCombatStatsTest {
    @Test
    void upgradesScaleAndCooldownNeverDropsBelowTwoTicks() {
        TowerCombatStats stats = new TowerCombatStats(4.0, 2.0, 14.0, 1.5,
                20, 0.5, 3.0, 0);
        assertEquals(10.0, stats.damage(3));
        assertEquals(18.5, stats.range(3));
        assertEquals(10, stats.cooldownTicks(1));
        assertEquals(2, stats.cooldownTicks(10));
    }
}
