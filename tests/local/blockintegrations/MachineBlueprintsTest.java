package local.blockintegrations;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineBlueprintsTest {
    private static EnumMap<FurnaceUpgrade, Integer> levels(int speed, int fuel, int yield) {
        EnumMap<FurnaceUpgrade, Integer> out = new EnumMap<>(FurnaceUpgrade.class);
        out.put(FurnaceUpgrade.SPEED, speed); out.put(FurnaceUpgrade.FUEL, fuel); out.put(FurnaceUpgrade.YIELD, yield);
        return out;
    }

    @Test
    void givenBlueprint_whenPlanned_thenOnlyMissingLevelsInEnumOrder() {
        List<FurnaceUpgrade> steps = MachineBlueprints.steps(levels(2, 0, 3), levels(0, 4, 2));
        assertEquals(List.of(FurnaceUpgrade.SPEED, FurnaceUpgrade.SPEED, FurnaceUpgrade.YIELD), steps, "fuel 4 stays, never lowered");
    }

    @Test
    void givenSteps_whenCosted_thenEachStepUsesItsOwnLevel() {
        Map<FurnaceUpgrade, Integer> current = levels(1, 0, 0);
        List<FurnaceUpgrade> steps = MachineBlueprints.steps(levels(3, 1, 0), current);
        long total = MachineBlueprints.cost(steps, current, (upgrade, level) -> (level + 1) * 100);
        assertEquals(200 + 300 + 100, total);
    }

    @Test
    void givenEqualMachines_whenPlanned_thenNothingToDo() {
        assertEquals(List.of(), MachineBlueprints.steps(levels(5, 5, 5), levels(5, 5, 5)));
    }
}
