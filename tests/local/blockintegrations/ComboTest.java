package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComboTest {
    @Test
    void levelsAreClampedAndUnsupportedPathsRemainZero() {
        Combo combo = new Combo(new BlockKey(UUID.randomUUID(), 1, 2, 3), ComboType.SORTER,
                Map.of(ComboUpgrade.RANGE, 99, ComboUpgrade.POWER, 5), 10);

        assertEquals(10, combo.level(ComboUpgrade.RANGE));
        assertEquals(0, combo.level(ComboUpgrade.POWER));
        assertFalse(combo.upgrade(ComboUpgrade.RANGE));
        assertFalse(combo.upgrade(ComboUpgrade.POWER));
    }

    @Test
    void supportedPathCanBeUpgraded() {
        Combo combo = new Combo(new BlockKey(UUID.randomUUID(), 1, 2, 3),
                ComboType.FLOAT_LAUNCHER, Map.of(), 10);

        assertTrue(combo.upgrade(ComboUpgrade.DURATION));
        assertEquals(1, combo.level(ComboUpgrade.DURATION));
    }
}
