package local.blockintegrations;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NearbyTowersTest {
    @Test
    void filtersWorldAndRadiusAndSortsNearestFirstWithStableTies() {
        UUID worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        Tower far = tower(worldId, 48, 64, 0);
        Tower upper = tower(worldId, 4, 80, 0);
        Tower lower = tower(worldId, 4, 60, 0);
        Tower close = tower(worldId, 1, 64, 0);
        assertEquals(List.of(close, lower, upper, far), TowerManager.nearbyTowers(
                List.of(far, upper, tower(worldId, 49, 64, 0), close,
                        tower(UUID.randomUUID(), 0, 64, 0), lower), new Location(world, 0, 64, 0)));
    }

    private static Tower tower(UUID world, int x, int y, int z) {
        return new Tower(new BlockKey(world, x, y, z), TowerType.ARCHER, 0, 0, 0, 10);
    }
}
