package local.blockintegrations;

import java.util.*;

/**
 * Named groups of a player's machines for the dashboard, stored as text in the player's data: one group per line,
 * {@code name<TAB>id|id|...} with machine ids as in {@link MachineDashboard.Machine#id()}. At most {@link #MAX_GROUPS}
 * groups of {@link #MAX_MEMBERS} machines; names are trimmed to {@link #MAX_NAME} characters and unique ignoring case.
 * Pure data, no Bukkit access.
 */
final class MachineGroups {
    static final int MAX_GROUPS = 8, MAX_MEMBERS = 32, MAX_NAME = 24;

    private final LinkedHashMap<String, LinkedHashSet<String>> groups = new LinkedHashMap<>();

    static MachineGroups decode(String text) {
        MachineGroups out = new MachineGroups();
        if (text == null || text.length() > 32_768) return out;
        for (String line : text.split("\n")) {
            int tab = line.indexOf('\t');
            String name = clean(tab < 0 ? line : line.substring(0, tab));
            if (name.isEmpty() || out.groups.size() >= MAX_GROUPS || out.find(name) != null) continue;
            LinkedHashSet<String> members = new LinkedHashSet<>();
            if (tab >= 0) for (String id : line.substring(tab + 1).split("\\|"))
                if (valid(id.trim()) && members.size() < MAX_MEMBERS) members.add(id.trim());
            out.groups.put(name, members);
        }
        return out;
    }

    String encode() {
        StringBuilder out = new StringBuilder();
        groups.forEach((name, members) -> {
            if (!out.isEmpty()) out.append('\n');
            out.append(name).append('\t').append(String.join("|", members));
        });
        return out.toString();
    }

    /** A valid machine id: kind {@code furnace|tower|combo}, a colon and a block key. */
    static boolean valid(String id) {
        if (id == null) return false;
        String[] parts = id.split(":", 2);
        if (parts.length != 2 || !Set.of("furnace", "tower", "combo").contains(parts[0])) return false;
        try { BlockKey.parse(parts[1]); return true; } catch (IllegalArgumentException invalid) { return false; }
    }

    /** Plain group name: no tabs, line breaks, commas or colour codes, at most {@link #MAX_NAME} characters. */
    static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("[\\t\\r\\n,|§&]", " ").replaceAll("\\s{2,}", " ").strip();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).strip() : s;
    }

    List<String> names() { return List.copyOf(groups.keySet()); }
    int size() { return groups.size(); }

    /** The stored name matching {@code name} ignoring case, or null. */
    String find(String name) {
        for (String existing : groups.keySet()) if (existing.equalsIgnoreCase(name == null ? "" : name.strip())) return existing;
        return null;
    }

    Set<String> members(String name) {
        String key = find(name);
        return key == null ? Set.of() : Collections.unmodifiableSet(groups.get(key));
    }

    /** Groups that contain the machine. */
    List<String> groupsOf(String id) {
        List<String> out = new ArrayList<>();
        groups.forEach((name, members) -> { if (members.contains(id)) out.add(name); });
        return out;
    }

    /** Result codes: {@code ok}, {@code name} (empty), {@code exists}, {@code limit}, {@code unknown}, {@code full}. */
    String create(String raw) {
        String name = clean(raw);
        if (name.isEmpty()) return "name";
        if (find(name) != null) return "exists";
        if (groups.size() >= MAX_GROUPS) return "limit";
        groups.put(name, new LinkedHashSet<>());
        return "ok";
    }

    String rename(String old, String raw) {
        String key = find(old), name = clean(raw);
        if (key == null) return "unknown";
        if (name.isEmpty()) return "name";
        String clash = find(name);
        if (clash != null && !clash.equals(key)) return "exists";
        LinkedHashMap<String, LinkedHashSet<String>> copy = new LinkedHashMap<>();
        groups.forEach((n, m) -> copy.put(n.equals(key) ? name : n, m));
        groups.clear();
        groups.putAll(copy);
        return "ok";
    }

    boolean delete(String name) {
        String key = find(name);
        return key != null && groups.remove(key) != null;
    }

    String add(String name, String id) {
        String key = find(name);
        if (key == null) return "unknown";
        if (!valid(id)) return "unknown";
        LinkedHashSet<String> members = groups.get(key);
        if (members.contains(id)) return "ok";
        if (members.size() >= MAX_MEMBERS) return "full";
        members.add(id);
        return "ok";
    }

    boolean remove(String name, String id) {
        String key = find(name);
        return key != null && groups.get(key).remove(id);
    }

    /** Drops ids the player no longer owns (machine broken or given away); true if something changed. */
    boolean retain(Set<String> owned) {
        boolean changed = false;
        for (LinkedHashSet<String> members : groups.values()) changed |= members.retainAll(owned);
        return changed;
    }

    /** How many members are in each state, most common first; ties keep the first seen order. */
    static List<Map.Entry<String, Integer>> summary(Collection<String> states) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String state : states) counts.merge(state, 1, Integer::sum);
        List<Map.Entry<String, Integer>> out = new ArrayList<>(counts.entrySet());
        out.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return out;
    }
}
