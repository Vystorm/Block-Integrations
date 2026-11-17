package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class FloatLauncherStructureTest {
    @Test
    void acceptsCopperVariantsBetweenTerracottaAndRedstone() {
        assertTrue(FloatLauncherStructure.matches(
                Material.WHITE_TERRACOTTA, Material.COPPER_BLOCK, Material.REDSTONE_BLOCK));
        assertTrue(FloatLauncherStructure.matches(
                Material.WHITE_TERRACOTTA, Material.OXIDIZED_COPPER, Material.REDSTONE_BLOCK));
        assertTrue(FloatLauncherStructure.matches(
                Material.WHITE_TERRACOTTA, Material.WAXED_WEATHERED_CHISELED_COPPER,
                Material.REDSTONE_BLOCK));
    }

    @Test
    void requiresExactTopAndBottomAndAFullCopperMiddle() {
        assertFalse(FloatLauncherStructure.matches(
                Material.RED_TERRACOTTA, Material.COPPER_BLOCK, Material.REDSTONE_BLOCK));
        assertFalse(FloatLauncherStructure.matches(
                Material.WHITE_TERRACOTTA, Material.COPPER_ORE, Material.REDSTONE_BLOCK));
        assertFalse(FloatLauncherStructure.matches(
                Material.WHITE_TERRACOTTA, Material.COPPER_BLOCK, Material.IRON_BLOCK));
    }
}
