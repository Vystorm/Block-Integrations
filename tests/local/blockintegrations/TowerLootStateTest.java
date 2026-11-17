package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class TowerLootStateTest {
    @Test
    void storesAndWithdrawsKillExperienceWithoutOverflow() {
        Tower tower = tower();

        tower.addStoredExperience(25);
        tower.addStoredExperience(17);
        assertEquals(42, tower.storedExperience());
        assertEquals(42, tower.withdrawStoredExperience());
        assertEquals(0, tower.storedExperience());

        tower.addStoredExperience(Integer.MAX_VALUE);
        tower.addStoredExperience(100);
        assertEquals(Integer.MAX_VALUE, tower.storedExperience());
    }

    @Test
    void resizesPersistentLootArrayToConfiguredInventorySize() {
        Tower tower = tower();
        tower.setLootContents(new ItemStack[9]);

        assertEquals(54, tower.lootContents(54).length);
        assertEquals(9, tower.lootContents(9).length);
    }

    @Test
    void starterTowerCannotGainUpgradeLevels() {
        Tower tower = new Tower(new BlockKey(UUID.randomUUID(), 1, 64, 2),
                TowerType.ZOMBIE_STARTER, 10, 10, 10, 10);

        tower.upgrade(TowerUpgrade.DAMAGE);

        assertEquals(0, tower.level(TowerUpgrade.DAMAGE));
        assertEquals(0, tower.level(TowerUpgrade.RANGE));
        assertEquals(0, tower.level(TowerUpgrade.SPEED));
        assertEquals(0, tower.level(TowerUpgrade.LOOTING));
    }

    @Test
    void regularTowerClampsPersistedLootingToLevelThree() {
        Tower tower = new Tower(new BlockKey(UUID.randomUUID(), 1, 64, 2),
                TowerType.ARCHER, 0, 0, 0, 10, 10, 0, new ItemStack[0]);

        assertEquals(3, tower.level(TowerUpgrade.LOOTING));
        tower.upgrade(TowerUpgrade.LOOTING);
        assertEquals(3, tower.level(TowerUpgrade.LOOTING));
    }

    private Tower tower() {
        return new Tower(new BlockKey(UUID.randomUUID(), 1, 64, 2), TowerType.ARCHER,
                0, 0, 0, 10);
    }
}
