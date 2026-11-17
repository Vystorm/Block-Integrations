package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class SortSlotLocksTest {
    private final NamespacedKey key = new NamespacedKey("blockintegrations", "sort-locked-slots");
    private final SortSlotLocks locks = new SortSlotLocks(key);

    @Test
    void readIgnoresInvalidAndDuplicateValuesAndReturnsSortedSlots() {
        PersistentDataContainer data = data(new int[] {35, 8, 9, 35, 36, -1});

        assertEquals(Set.of(9, 35), locks.read(data));
        assertTrue(locks.read(data).contains(9));
    }

    @Test
    void togglePersistsDeduplicatedSortedSlotsAndReturnsNewLockState() {
        int[][] stored = {{27, 10, 27, 8}};
        PersistentDataContainer data = mutableData(stored);

        assertTrue(locks.toggle(data, 20));
        assertEquals(Set.of(10, 20, 27), locks.read(data));
        assertArrayEquals(new int[] {10, 20, 27}, stored[0]);

        assertFalse(locks.toggle(data, 10));
        assertEquals(Set.of(20, 27), locks.read(data));
        assertArrayEquals(new int[] {20, 27}, stored[0]);
    }

    @Test
    void clearRemovesThePlayerValueAndInvalidToggleIsRejected() {
        int[][] stored = {{9, 35}};
        PersistentDataContainer data = mutableData(stored);

        locks.clear(data);
        assertEquals(Set.of(), locks.read(data));
        assertEquals(null, stored[0]);
        assertThrows(IllegalArgumentException.class, () -> locks.toggle(data, 36));
    }

    private PersistentDataContainer data(int[] value) {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.get(key, PersistentDataType.INTEGER_ARRAY)).thenReturn(value);
        return data;
    }

    private PersistentDataContainer mutableData(int[][] stored) {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.get(key, PersistentDataType.INTEGER_ARRAY)).thenAnswer(invocation -> stored[0]);
        doAnswer(invocation -> {
            stored[0] = invocation.getArgument(2);
            return null;
        }).when(data).set(eq(key), eq(PersistentDataType.INTEGER_ARRAY), any(int[].class));
        doAnswer(invocation -> {
            stored[0] = null;
            return null;
        }).when(data).remove(key);
        return data;
    }
}
