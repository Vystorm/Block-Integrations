package local.blockintegrations;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Personal, bounded chest ordering preferences stored on the player. */
final class ChestPreferences {
    static final int MAX_PREFERRED = 8;
    static final int MAX_ENCODED_LENGTH = 4096;
    private static final String VERSION = "v1";

    record Position(UUID world, int x, int y, int z) {
        Position {
            if (world == null) throw new IllegalArgumentException("world is required");
        }

        String key() { return world + ":" + x + ":" + y + ":" + z; }
    }

    record Preferences(List<Position> preferred, Position overflow) {
        Preferences {
            preferred = List.copyOf(preferred);
            if (preferred.size() > MAX_PREFERRED) throw new IllegalArgumentException("too many preferred chests");
        }

        static Preferences empty() { return new Preferences(List.of(), null); }
    }

    private final NamespacedKey key;

    ChestPreferences(NamespacedKey key) { this.key = key; }

    Preferences read(PersistentDataContainer data) {
        try { return decode(data.get(key, PersistentDataType.STRING)); }
        catch (IllegalArgumentException malformed) { return Preferences.empty(); }
    }

    void write(PersistentDataContainer data, Preferences preferences) {
        String encoded = encode(preferences);
        if (encoded.isEmpty()) data.remove(key);
        else data.set(key, PersistentDataType.STRING, encoded);
    }

    static Preferences add(Preferences prefs, Position position) {
        List<Position> next = new ArrayList<>(prefs.preferred());
        next.remove(position);
        if (next.size() == MAX_PREFERRED) throw new IllegalArgumentException("preference limit reached");
        next.add(position);
        return new Preferences(next, position.equals(prefs.overflow()) ? null : prefs.overflow());
    }

    static Preferences setOverflow(Preferences prefs, Position position) {
        List<Position> next = new ArrayList<>(prefs.preferred());
        next.remove(position);
        return new Preferences(next, position);
    }

    static Preferences remove(Preferences prefs, Position position) {
        List<Position> next = new ArrayList<>(prefs.preferred());
        next.remove(position);
        return new Preferences(next, position.equals(prefs.overflow()) ? null : prefs.overflow());
    }

    /** Smaller rank runs earlier; double chest keys match either half. */
    static int rank(String chestKey, Preferences prefs) {
        String[] halves = chestKey.split("\\|", -1);
        for (int i = 0; i < prefs.preferred().size(); i++) {
            String wanted = prefs.preferred().get(i).key();
            for (String half : halves) if (wanted.equals(half)) return i;
        }
        if (prefs.overflow() != null) {
            for (String half : halves) if (prefs.overflow().key().equals(half)) return MAX_PREFERRED + 1;
        }
        return MAX_PREFERRED;
    }

    static String encode(Preferences prefs) {
        if (prefs.preferred().isEmpty() && prefs.overflow() == null) return "";
        StringBuilder result = new StringBuilder(VERSION);
        for (Position position : prefs.preferred()) result.append(";P:").append(position.key());
        if (prefs.overflow() != null) result.append(";O:").append(prefs.overflow().key());
        if (result.length() > MAX_ENCODED_LENGTH) throw new IllegalArgumentException("encoded preferences too large");
        return result.toString();
    }

    static Preferences decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Preferences.empty();
        if (encoded.length() > MAX_ENCODED_LENGTH || !encoded.startsWith(VERSION + ";")) return Preferences.empty();
        String[] records = encoded.split(";", -1);
        if (records.length < 2 || records.length > MAX_PREFERRED + 2 || !VERSION.equals(records[0])) return Preferences.empty();
        List<Position> preferred = new ArrayList<>();
        Set<Position> seen = new LinkedHashSet<>();
        Position overflow = null;
        try {
            for (int i = 1; i < records.length; i++) {
                String record = records[i];
                if (record.startsWith("P:")) {
                    Position p = parsePosition(record.substring(2));
                    if (preferred.size() == MAX_PREFERRED || !seen.add(p)) return Preferences.empty();
                    preferred.add(p);
                } else if (record.startsWith("O:") && overflow == null) {
                    overflow = parsePosition(record.substring(2));
                    if (!seen.add(overflow)) return Preferences.empty();
                } else return Preferences.empty();
            }
        } catch (RuntimeException invalid) {
            return Preferences.empty();
        }
        return new Preferences(preferred, overflow);
    }

    private static Position parsePosition(String text) {
        String[] parts = text.split(":", -1);
        if (parts.length != 4) throw new IllegalArgumentException("invalid position");
        return new Position(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }
}
