package local.blockintegrations;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Personal, persistent exclusions for the sorter’s main-inventory source slots. */
final class SortSlotLocks {
    static final int FIRST_SLOT = 9;
    static final int LAST_SLOT = 35;

    private final NamespacedKey key;

    SortSlotLocks(NamespacedKey key) {
        this.key = key;
    }

    Set<Integer> read(PersistentDataContainer data) {
        int[] stored;
        try { stored = data.get(key, PersistentDataType.INTEGER_ARRAY); }
        catch (IllegalArgumentException malformed) { return Set.of(); }
        TreeSet<Integer> valid = new TreeSet<>();
        if (stored != null) {
            for (int slot : stored) {
                if (isValidSlot(slot)) valid.add(slot);
            }
        }
        return Collections.unmodifiableSet(valid);
    }

    boolean toggle(PersistentDataContainer data, int slot) {
        requireValidSlot(slot);
        TreeSet<Integer> updated = new TreeSet<>(read(data));
        boolean locked;
        if (updated.remove(slot)) {
            locked = false;
        } else {
            updated.add(slot);
            locked = true;
        }
        write(data, updated);
        return locked;
    }

    void clear(PersistentDataContainer data) {
        data.remove(key);
    }

    static boolean isValidSlot(int slot) {
        return slot >= FIRST_SLOT && slot <= LAST_SLOT;
    }

    private static void requireValidSlot(int slot) {
        if (!isValidSlot(slot)) {
            throw new IllegalArgumentException("slot must be between 9 and 35");
        }
    }

    private void write(PersistentDataContainer data, Set<Integer> slots) {
        if (slots.isEmpty()) {
            data.remove(key);
            return;
        }
        data.set(key, PersistentDataType.INTEGER_ARRAY, slots.stream().mapToInt(Integer::intValue).toArray());
    }
}
