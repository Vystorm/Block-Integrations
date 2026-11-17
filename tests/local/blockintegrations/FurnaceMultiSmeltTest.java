package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FurnaceMultiSmeltTest {
    @Test
    void everyLevelProcessesOneAdditionalInput() {
        FurnaceMultiSmelt.Plan plan = FurnaceMultiSmelt.plan(4, 16, 0, 1, 1, 64);

        assertEquals(4, plan.additionalInputs());
        assertEquals(4, plan.additionalOutput());
        assertEquals(5, FurnaceMultiSmelt.itemsPerCycle(4));
        assertEquals(11, FurnaceMultiSmelt.itemsPerCycle(10));
    }

    @Test
    void availableInputsLimitTheBatch() {
        FurnaceMultiSmelt.Plan plan = FurnaceMultiSmelt.plan(10, 3, 0, 1, 1, 64);

        assertEquals(2, plan.additionalInputs());
        assertEquals(2, plan.additionalOutput());
    }

    @Test
    void outputCapacityLimitsWithoutOverflowOrLoss() {
        FurnaceMultiSmelt.Plan plan = FurnaceMultiSmelt.plan(10, 64, 60, 1, 1, 64);

        assertEquals(3, plan.additionalInputs());
        assertEquals(3, plan.additionalOutput());
    }

    @Test
    void recipesWithMultipleResultsReserveTheirFullOutput() {
        FurnaceMultiSmelt.Plan plan = FurnaceMultiSmelt.plan(5, 20, 55, 2, 2, 64);

        assertEquals(3, plan.additionalInputs());
        assertEquals(6, plan.additionalOutput());
    }

    @Test
    void levelZeroKeepsVanillaSingleSmelt() {
        FurnaceMultiSmelt.Plan plan = FurnaceMultiSmelt.plan(0, 64, 0, 1, 1, 64);

        assertEquals(0, plan.additionalInputs());
        assertEquals(0, plan.additionalOutput());
        assertEquals(1, FurnaceMultiSmelt.itemsPerCycle(0));
    }
}
