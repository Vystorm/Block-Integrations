package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ComboTypeTest {
    @Test
    void pressurePlateCombosAcceptFullCopperVariants() {
        assertTrue(ComboType.SORTER.matches(Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
                Material.WAXED_OXIDIZED_COPPER, Material.AIR));
        assertTrue(ComboType.VACUUM.matches(Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
                Material.CUT_COPPER, Material.AIR));
        assertFalse(ComboType.SORTER.matches(Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
                Material.COPPER_ORE, Material.AIR));
    }

    @Test
    void movementCombosRequireTheirExactLayers() {
        assertTrue(ComboType.LAUNCHER.matches(
                Material.SLIME_BLOCK, Material.REDSTONE_BLOCK, Material.AIR));
        assertTrue(ComboType.BOOSTER.matches(
                Material.PACKED_ICE, Material.REDSTONE_BLOCK, Material.AIR));
        assertTrue(ComboType.FLOAT_LAUNCHER.matches(Material.WHITE_TERRACOTTA,
                Material.WEATHERED_CHISELED_COPPER, Material.REDSTONE_BLOCK));
        assertTrue(ComboType.HEAL.matches(
                Material.HONEY_BLOCK, Material.REDSTONE_BLOCK, Material.AIR));
        assertFalse(ComboType.FLOAT_LAUNCHER.matches(Material.WHITE_TERRACOTTA,
                Material.WEATHERED_CHISELED_COPPER, Material.IRON_BLOCK));
        assertFalse(ComboType.HEAL.matches(
                Material.HONEY_BLOCK, Material.GOLD_BLOCK, Material.AIR));
    }

    @Test
    void everyComboExposesOnlyUsefulUpgradePaths() {
        assertTrue(ComboType.SORTER.supports(ComboUpgrade.RANGE));
        assertFalse(ComboType.SORTER.supports(ComboUpgrade.POWER));
        assertTrue(ComboType.LAUNCHER.supports(ComboUpgrade.POWER));
        assertFalse(ComboType.LAUNCHER.supports(ComboUpgrade.DURATION));
        assertTrue(ComboType.FLOAT_LAUNCHER.supports(ComboUpgrade.DURATION));
        assertTrue(ComboType.HEAL.supports(ComboUpgrade.POWER));
        assertTrue(ComboType.HEAL.supports(ComboUpgrade.COOLDOWN));
        assertTrue(ComboType.HEAL.supports(ComboUpgrade.RANGE));
        assertFalse(ComboType.HEAL.supports(ComboUpgrade.DURATION));
    }
}
