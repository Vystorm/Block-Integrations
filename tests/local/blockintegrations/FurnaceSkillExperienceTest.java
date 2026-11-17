package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FurnaceSkillExperienceTest {
    @Test
    void offlineOwnerReceivesBaseMultismeltAndBoostExperience() {
        assertEquals(24.0D, FurnaceSkillExperience.amountToTransfer(
                3, 3, 5, 0.20D, false), 0.00001D);
    }

    @Test
    void onlineOwnerDoesNotReceiveTheMcMmoBaseExperienceTwice() {
        assertEquals(21.0D, FurnaceSkillExperience.amountToTransfer(
                3, 3, 5, 0.20D, true), 0.00001D);
    }

    @Test
    void multismeltWithoutBoostTransfersOnlyAdditionalItemsOnline() {
        assertEquals(9.0D, FurnaceSkillExperience.amountToTransfer(
                3, 3, 0, 0.20D, true), 0.00001D);
    }

    @Test
    void fractionalBoostIsRetainedUntilAWholePointCanBePaid() {
        assertEquals(0, FurnaceSkillExperience.payablePoints(0.60D));
        assertEquals(1, FurnaceSkillExperience.payablePoints(1.20D));
        assertEquals(0.20D, FurnaceSkillExperience.remainder(1.20D, 1), 0.00001D);
    }
}
