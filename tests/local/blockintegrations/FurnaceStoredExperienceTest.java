package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FurnaceStoredExperienceTest {
    @Test
    void calculatesPayablePointsAndKeepsFractionForTheNextWithdrawal() {
        double stored = FurnaceStoredExperience.addRecipeExperience(0.25D, 0.7D, 3);

        assertEquals(2.35D, stored, 0.0001D);
        assertEquals(2, FurnaceStoredExperience.payablePoints(stored));
        assertEquals(0.35D, FurnaceStoredExperience.remainder(stored, 2), 0.0001D);
    }

    @Test
    void doesNotDiscardAnIncompleteExperiencePoint() {
        assertEquals(0, FurnaceStoredExperience.payablePoints(0.99D));
        assertEquals(0.99D, FurnaceStoredExperience.remainder(0.99D, 0), 0.0001D);
    }
}
