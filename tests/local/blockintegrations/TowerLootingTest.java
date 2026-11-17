package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TowerLootingTest {
    @Test
    void levelThreeRollsZeroToThreeBonusItems() {
        assertEquals(0, TowerLooting.bonusAmount(3, 0.00D));
        assertEquals(1, TowerLooting.bonusAmount(3, 0.25D));
        assertEquals(2, TowerLooting.bonusAmount(3, 0.50D));
        assertEquals(3, TowerLooting.bonusAmount(3, 0.99D));
    }

    @Test
    void levelIsClampedToThreeAndInvalidRollIsSafe() {
        assertEquals(3, TowerLooting.bonusAmount(10, 1.0D));
        assertEquals(0, TowerLooting.bonusAmount(0, 0.99D));
        assertEquals(0, TowerLooting.bonusAmount(3, Double.NaN));
    }
}
