package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class TowerDirectorMobTest {
    private static Zombie mob(Set<NamespacedKey> keys) {
        Zombie zombie = mock(Zombie.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.getKeys()).thenReturn(keys);
        when(zombie.getPersistentDataContainer()).thenReturn(data);
        return zombie;
    }

    @Test
    void spawnDirectorMarkersMakeNamedMobsTargetable() {
        assertTrue(TowerManager.directorMob(mob(Set.of(NamespacedKey.fromString("spawndirector:director_mob")))));
        assertTrue(TowerManager.directorMob(mob(Set.of(NamespacedKey.fromString("spawndirector:structure_slot")))));
        assertFalse(TowerManager.directorMob(mob(Set.of())));
        assertFalse(TowerManager.directorMob(mob(Set.of(NamespacedKey.fromString("other:director_mob")))));
    }
}
