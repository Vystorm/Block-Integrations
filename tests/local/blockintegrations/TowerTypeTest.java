package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TowerTypeTest {
    @Test
    void archerAcceptsFullCopperVariants() {
        assertTrue(TowerType.ARCHER.acceptsFoundation(Material.COPPER_BLOCK));
        assertTrue(TowerType.ARCHER.acceptsFoundation(Material.OXIDIZED_COPPER));
        assertTrue(TowerType.ARCHER.acceptsFoundation(Material.WAXED_EXPOSED_COPPER));
        assertTrue(TowerType.ARCHER.acceptsFoundation(Material.CUT_COPPER));
        assertTrue(TowerType.ARCHER.acceptsFoundation(Material.WAXED_WEATHERED_CHISELED_COPPER));
    }

    @Test
    void archerRejectsOreAndNonFullCopperBlocks() {
        assertFalse(TowerType.ARCHER.acceptsFoundation(Material.COPPER_ORE));
        assertFalse(TowerType.ARCHER.acceptsFoundation(Material.CUT_COPPER_STAIRS));
        assertFalse(TowerType.ARCHER.acceptsFoundation(Material.COPPER_DOOR));
        assertFalse(TowerType.ARCHER.acceptsFoundation(Material.STONE));
    }

    @Test
    void otherTowerFoundationsStayExact() {
        assertTrue(TowerType.CANNON.acceptsFoundation(Material.BLAST_FURNACE));
        assertFalse(TowerType.CANNON.acceptsFoundation(Material.COPPER_BLOCK));
        assertTrue(TowerType.FLAME.acceptsFoundation(Material.NETHER_BRICKS));
    }

    @Test
    void everyPlaceableMinecraftHeadHasExactlyOneTower() {
        Set<Material> expectedHeads = Set.of(
                Material.SKELETON_SKULL,
                Material.WITHER_SKELETON_SKULL,
                Material.ZOMBIE_HEAD,
                Material.PLAYER_HEAD,
                Material.CREEPER_HEAD,
                Material.DRAGON_HEAD,
                Material.PIGLIN_HEAD);

        assertEquals(expectedHeads, java.util.Arrays.stream(TowerType.values())
                .map(type -> type.core)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void exactlyTwoCheapStarterTowersHaveNoUpgrades() {
        assertFalse(TowerType.ZOMBIE_STARTER.upgradeable);
        assertFalse(TowerType.PLAYER_STARTER.upgradeable);
        assertEquals(2, java.util.Arrays.stream(TowerType.values())
                .filter(type -> !type.upgradeable)
                .count());
    }
}
