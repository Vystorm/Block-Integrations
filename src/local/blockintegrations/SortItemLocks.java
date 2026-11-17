package local.blockintegrations;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Personal material exclusions, independent of inventory slots and item metadata. */
final class SortItemLocks {
    private final NamespacedKey key;

    SortItemLocks(NamespacedKey key) { this.key = key; }

    Set<Material> read(PersistentDataContainer data) {
        String stored;
        try { stored = data.get(key, PersistentDataType.STRING); }
        catch (IllegalArgumentException malformed) { return Set.of(); }
        Set<Material> result = new TreeSet<>();
        if (stored != null) for (String name : stored.split(",")) {
            try {
                Material material = Material.valueOf(name);
                if (usable(material)) result.add(material);
            } catch (IllegalArgumentException unknown) { /* Ignore removed or invalid materials. */ }
        }
        return Collections.unmodifiableSet(result);
    }

    boolean toggle(PersistentDataContainer data, Material material) {
        if (!usable(material))
            throw new IllegalArgumentException("an item material is required");
        Set<Material> updated = new TreeSet<>(read(data));
        boolean locked = !updated.remove(material);
        if (locked) updated.add(material);
        if (updated.isEmpty()) clear(data);
        else data.set(key, PersistentDataType.STRING, updated.stream().map(Material::name)
                .collect(java.util.stream.Collectors.joining(",")));
        return locked;
    }

    void clear(PersistentDataContainer data) { data.remove(key); }

    static boolean canSort(ItemStack item, Set<Material> locked) {
        return item != null && usable(item.getType()) && item.getAmount() > 0 && !locked.contains(item.getType());
    }

    private static boolean usable(Material material) {
        return material != null && material != Material.AIR && material != Material.CAVE_AIR
                && material != Material.VOID_AIR && !material.isLegacy();
    }
}
