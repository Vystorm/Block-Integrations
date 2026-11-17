package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class XpCollectorTest {
    @Test
    void enchantingTableOnLapisIsAnXpCollector() {
        assertTrue(ComboType.XP_COLLECTOR.matches(Material.ENCHANTING_TABLE, Material.LAPIS_BLOCK, Material.AIR));
        assertFalse(ComboType.XP_COLLECTOR.matches(Material.ENCHANTING_TABLE, Material.BOOKSHELF, Material.AIR));
        assertEquals(ComboType.XP_COLLECTOR, ComboType.byConfigKey("xp-collector"));
        assertTrue(ComboType.XP_COLLECTOR.supports(ComboUpgrade.RANGE));
        assertTrue(ComboType.XP_COLLECTOR.supports(ComboUpgrade.POWER));
        assertFalse(ComboType.XP_COLLECTOR.supports(ComboUpgrade.COOLDOWN));
        assertEquals("combo-upgrade.capacity", ComboType.XP_COLLECTOR.upgradeNameKey(ComboUpgrade.POWER));
        assertEquals("combo-upgrade.range", ComboType.XP_COLLECTOR.upgradeNameKey(ComboUpgrade.RANGE));
        assertEquals("combo-upgrade.power", ComboType.LAUNCHER.upgradeNameKey(ComboUpgrade.POWER));
    }

    @Test
    void capacityGrowsPerLevel() {
        assertEquals(1500, XpCollector.capacity(1500, 1500, 0));
        assertEquals(16500, XpCollector.capacity(1500, 1500, 10));
        assertEquals(1500, XpCollector.capacity(1500, 1500, -1));
    }

    @Test
    void onlyWholeOrbsThatFitAreTaken() {
        assertEquals(3, XpCollector.orbsToTake(0, 100, 7, 3));
        assertEquals(2, XpCollector.orbsToTake(85, 100, 7, 3), "15 points left: two orbs of 7");
        assertEquals(0, XpCollector.orbsToTake(95, 100, 7, 3));
        assertEquals(0, XpCollector.orbsToTake(100, 100, 7, 3));
        assertEquals(0, XpCollector.orbsToTake(0, 100, 0, 3), "worthless orbs stay");
        assertEquals(0, XpCollector.orbsToTake(0, 100, 5, 0));
    }

    @Test
    void oneLevelIsWhatThePlayerStillNeeds() {
        assertEquals(7, XpCollector.pointsToNextLevel(7, 0.0F));
        assertEquals(4, XpCollector.pointsToNextLevel(7, 0.5F));
        assertEquals(1, XpCollector.pointsToNextLevel(7, 1.0F));
    }

    @Test
    void brokenCollectorsSpillEverything() {
        assertEquals(List.of(), XpCollector.spill(0));
        assertEquals(List.of(250), XpCollector.spill(250));
        assertEquals(List.of(1000, 1000, 500), XpCollector.spill(2500));
        assertEquals(2500, XpCollector.spill(2500).stream().mapToInt(Integer::intValue).sum());
    }
}
