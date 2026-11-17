package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FurnaceExperienceBoostTest {
    @Test
    void fiveLevelsDoubleVanillaExperience() {
        assertEquals(1.0D, FurnaceExperienceBoost.multiplier(0, 0.20D), 0.00001D);
        assertEquals(2.0D, FurnaceExperienceBoost.multiplier(5, 0.20D), 0.00001D);
        assertEquals(2.0D, FurnaceExperienceBoost.multiplier(10, 0.20D), 0.00001D);
    }

    @Test
    void boostIncludesBaseAndAllMultismeltInputs() {
        double additional = FurnaceExperienceBoost.additionalExperience(
                0.70D, 3, 5, 0.20D);

        // Vanilla tracks 0.7 XP for the base item. The stored addition contains
        // 2.1 XP for Multismelt plus 2.8 XP boost for all four processed items.
        assertEquals(4.90D, additional, 0.00001D);
    }

    @Test
    void normalSmeltReceivesBoostWithoutMultismelt() {
        assertEquals(0.35D, FurnaceExperienceBoost.additionalExperience(
                0.70D, 0, 1, 0.50D), 0.00001D);
    }
}
