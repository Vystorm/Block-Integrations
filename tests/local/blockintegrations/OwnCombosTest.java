package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnCombosTest {
    private static final UUID HERE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static Combo combo(UUID world, int x, int y, int z) {
        return new Combo(new BlockKey(world, x, y, z), ComboType.SORTER, Map.of(), 10);
    }

    @Test
    void ownCombosAreListedNearestFirstThenOtherWorlds() {
        Combo far = combo(HERE, 100, 64, 0), near = combo(HERE, 3, 64, 4), nether = combo(NETHER, 0, 64, 0), middle = combo(HERE, -20, 70, 0);
        List<Combo> sorted = ComboManager.nearestFirst(List.of(nether, far, near, middle), HERE, 0.5, 64, 0.5);
        assertEquals(List.of(near, middle, far, nether), sorted);
    }

    @Test
    void emptyStaysEmpty() {
        assertEquals(List.of(), ComboManager.nearestFirst(List.of(), HERE, 0, 0, 0));
    }
}
