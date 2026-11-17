package local.blockintegrations;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnvilRulesTest {
    @Test void reductionsRoundUpAndNeverMakePaidWorkFree() {
        assertEquals(20, AnvilRules.repairCost(40, 10));
        assertEquals(39, AnvilRules.repairCost(41, 1));
        assertEquals(1, AnvilRules.repairCost(1, 10));
        assertEquals(0, AnvilRules.repairCost(0, 10));
        assertEquals(20, AnvilRules.repairCost(40, 100));
    }
    @Test void equalEnchantmentsProgressOnlyToPurchasedCapAndKeepExistingHigherLevels() {
        assertEquals(5, AnvilRules.mergedLevel(5, 5, 5, 0));
        assertEquals(6, AnvilRules.mergedLevel(5, 5, 5, 1));
        assertEquals(6, AnvilRules.mergedLevel(6, 6, 5, 1));
        assertEquals(10, AnvilRules.mergedLevel(9, 9, 5, 5));
        assertEquals(12, AnvilRules.mergedLevel(12, 1, 5, 1));
        assertEquals(6, AnvilRules.mergedLevel(1, 50, 5, 1));
    }
    @Test void minedAndFallenAnvilsKeepAllUpgradesButRejectForgedRanks() throws Exception {
        for (Material material : new Material[]{Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL}) {
            var state = new PortableBlockData.State(material, "anvil", material.name(),
                    Map.of("efficiency", 10, "enchantments", 5, "durability", 1), UUID.randomUUID(), 0);
            assertEquals(state, PortableBlockData.decode(PortableBlockData.encode(state)));
            assertThrows(IllegalArgumentException.class, () -> PortableBlockData.encode(new PortableBlockData.State(
                    material, "anvil", material.name(), Map.of("durability", 2), state.owner(), 0)));
        }
    }
}
