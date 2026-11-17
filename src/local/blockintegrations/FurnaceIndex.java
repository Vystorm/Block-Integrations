package local.blockintegrations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;

/**
 * Where the owned furnaces are ({@code furnaces.json}). The furnace state itself stays in the block (owner, levels and
 * stored XP in its persistent data); this index only lets the web page list a player's furnaces without loading
 * chunks, with the last seen type and levels for furnaces whose chunk is not loaded. It is filled when a furnace is
 * placed, claimed, upgraded, used, moved or found in a loaded chunk, and pruned when a furnace is broken or found
 * missing.
 */
final class FurnaceIndex {
    record Entry(UUID owner, Material type, Map<String, Integer> levels) {
        Entry {
            levels = Map.copyOf(levels);
        }
    }

    private static final Comparator<Map.Entry<BlockKey, Entry>> ORDER = Comparator
            .<Map.Entry<BlockKey, Entry>, String>comparing(e -> e.getKey().worldId().toString())
            .thenComparingInt(e -> e.getKey().x()).thenComparingInt(e -> e.getKey().z())
            .thenComparingInt(e -> e.getKey().y());

    private final Map<BlockKey, Entry> entries = new HashMap<>();

    /** Stores or replaces an entry; {@code true} if anything changed. */
    boolean put(BlockKey key, Entry entry) {
        return !entry.equals(entries.put(key, entry));
    }

    Entry get(BlockKey key) {
        return entries.get(key);
    }

    Entry remove(BlockKey key) {
        return entries.remove(key);
    }

    int size() {
        return entries.size();
    }

    List<Map.Entry<BlockKey, Entry>> of(UUID owner) {
        List<Map.Entry<BlockKey, Entry>> list = new ArrayList<>();
        for (Map.Entry<BlockKey, Entry> entry : entries.entrySet())
            if (owner.equals(entry.getValue().owner())) list.add(Map.entry(entry.getKey(), entry.getValue()));
        list.sort(ORDER);
        return list;
    }

    List<Map.Entry<BlockKey, Entry>> all() {
        List<Map.Entry<BlockKey, Entry>> list = new ArrayList<>();
        for (Map.Entry<BlockKey, Entry> entry : entries.entrySet()) list.add(Map.entry(entry.getKey(), entry.getValue()));
        list.sort(ORDER);
        return list;
    }

    /** {@code furnaces.json}, schema 1: owned furnaces by block key. */
    record State(Map<String, Saved> furnaces) {
        State {
            furnaces = furnaces == null ? Map.of() : Map.copyOf(furnaces);
        }

        static State empty() {
            return new State(Map.of());
        }
    }

    record Saved(String owner, String type, Map<String, Integer> levels) {
        Saved {
            levels = levels == null ? Map.of() : Map.copyOf(levels);
        }
    }

    static final int SCHEMA = 1;

    State save() {
        Map<String, Saved> saved = new LinkedHashMap<>();
        for (Map.Entry<BlockKey, Entry> entry : entries.entrySet()) {
            Map<String, Integer> levels = new java.util.TreeMap<>();
            entry.getValue().levels().forEach((upgrade, level) -> { if (level > 0) levels.put(upgrade, level); });
            saved.put(entry.getKey().serialize(), new Saved(entry.getValue().owner().toString(), entry.getValue().type().name(), levels));
        }
        return new State(saved);
    }

    /** Replaces the contents with the saved index; invalid entries are skipped. */
    void load(State state) {
        entries.clear();
        for (Map.Entry<String, Saved> saved : state.furnaces().entrySet()) {
            try {
                BlockKey key = BlockKey.parse(saved.getKey());
                UUID owner = UUID.fromString(saved.getValue().owner());
                Material type = Material.valueOf(saved.getValue().type());
                if (!FurnaceManager.isFurnace(type)) continue;
                Map<String, Integer> levels = new LinkedHashMap<>();
                saved.getValue().levels().forEach((upgrade, level) -> levels.put(upgrade, Math.max(0, level == null ? 0 : level)));
                entries.put(key, new Entry(owner, type, levels));
            } catch (RuntimeException invalid) {
                // Skipped; the entry is rebuilt when the furnace is seen again.
            }
        }
    }
}
