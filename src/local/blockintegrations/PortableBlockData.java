package local.blockintegrations;

import Vystorm.vystorm_core._parsersDebuggerEtc.debugger.DebugMessages;
import Vystorm.vystorm_core.lang.Lang;
import java.io.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Small versioned item payload; visible lore is descriptive, never parsed as authority. */
final class PortableBlockData {
    /**
     * Readable string tag {@code <kind>:<type>} next to the binary payload, only for client-mod tooltip rules (they
     * match string tags); never read back as authority.
     */
    static final String KIND_TAG = "portable_kind";

    private final NamespacedKey key;
    private final NamespacedKey kindKey;
    private final Lang lang;

    PortableBlockData(Plugin plugin) {
        this(plugin, null);
    }

    /** {@code lang} renders the descriptive lore in the language of the player who broke the block. */
    PortableBlockData(Plugin plugin, Lang lang) {
        key = new NamespacedKey(plugin, "portable_block_v1");
        kindKey = new NamespacedKey(plugin, KIND_TAG);
        this.lang = lang;
    }

    record State(Material material, String kind, String type, Map<String, Integer> levels,
                 UUID owner, double experience) {
        State { levels = Map.copyOf(levels); }
    }

    /** {@code viewer} is the player who broke the block (lore language); {@code null} uses the server default. */
    ItemStack write(ItemStack item, CommandSender viewer, String kind, String type, Map<String, Integer> levels,
                    UUID owner, double experience) {
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        State state = new State(item.getType(), kind, type, levels, owner, experience);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY, encode(state));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kindTag(kind, type));
        // Readable level tags for client tooltip placeholders ({data:PublicBukkitValues.blockintegrations:lvl_damage});
        // the binary payload above stays the only authority when the block is placed again.
        for (var entry : levels.entrySet())
            meta.getPersistentDataContainer().set(new NamespacedKey(key.getNamespace(), levelTag(entry.getKey())), PersistentDataType.INTEGER, entry.getValue());
        List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.of());
        lore.add(text(viewer, "portable.header", "title", text(viewer, titleKey(state))));
        levels.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                lore.add(text(viewer, "portable.level", "upgrade", text(viewer, upgradeKey(kind, entry.getKey())),
                        "level", entry.getValue())));
        if (owner != null) lore.add(text(viewer, "portable.owner", "owner", owner));
        if (experience > 0) lore.add(text(viewer, "portable.experience", "xp", experience));
        lore.add(text(viewer, "portable.footer"));
        meta.setLore(lore);
        result.setItemMeta(meta);
        return result;
    }

    /** Name of the readable level tag of one upgrade, e.g. {@code lvl_damage}. */
    static String levelTag(String upgradeKey) {
        return "lvl_" + upgradeKey.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    /** Upgrade config keys of a portable kind with their lang name keys, in display order. */
    static Map<String, String> upgrades(String kind) {
        var result = new java.util.LinkedHashMap<String, String>();
        switch (kind) {
            case "tower" -> { for (TowerUpgrade u : TowerUpgrade.values()) result.put(u.configKey, u.nameKey()); }
            case "combo" -> { for (ComboUpgrade u : ComboUpgrade.values()) result.put(u.configKey, u.nameKey()); }
            case "furnace" -> { for (FurnaceUpgrade u : FurnaceUpgrade.values()) result.put(u.configKey, u.nameKey()); }
            case "workstation" -> { for (String key : List.of("speed", "fuel", "yield", "extra", "strength", "duration")) result.put(key, "workstation.upgrade." + key); }
            case "anvil" -> { for (String key : AnvilRules.UPGRADES) result.put(key, "anvil.path." + key); }
            default -> { }
        }
        return result;
    }

    /** Value of the {@link #KIND_TAG} tag, e.g. {@code tower:archer}. */
    static String kindTag(String kind, String type) {
        return kind + ":" + type;
    }

    State read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        byte[] data = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        if (data == null) return null;
        try {
            State state = decode(data);
            return state.material() == item.getType() ? state : null;
        } catch (IOException | IllegalArgumentException failure) {
            DebugMessages.mid("[PortableBlockData] Invalid block data discarded: " + failure.getMessage());
            return null;
        }
    }

    static byte[] encode(State state) {
        validate(state);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1);
            out.writeUTF(state.material().name());
            out.writeUTF(state.kind());
            out.writeUTF(state.type());
            out.writeInt(state.levels().size());
            for (var entry : new TreeMap<>(state.levels()).entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeInt(entry.getValue());
            }
            out.writeUTF(state.owner() == null ? "" : state.owner().toString());
            out.writeDouble(state.experience());
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static State decode(byte[] bytes) throws IOException {
        if (bytes.length > 4096) throw new IOException("payload too large");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 1) throw new IOException("unknown version");
        Material material = Material.valueOf(in.readUTF());
        String kind = in.readUTF(), type = in.readUTF();
        int count = in.readInt();
        if (count < 0 || count > 5) throw new IOException("invalid level count");
        Map<String, Integer> levels = new HashMap<>();
        for (int i = 0; i < count; i++) {
            if (levels.put(in.readUTF(), in.readInt()) != null) throw new IOException("duplicate level");
        }
        String owner = in.readUTF();
        State state = new State(material, kind, type, levels,
                owner.isEmpty() ? null : UUID.fromString(owner), in.readDouble());
        if (in.available() != 0) throw new IOException("trailing data");
        validate(state);
        return state;
    }

    private static void validate(State state) {
        boolean matching = switch (state.kind()) {
            case "combo" -> ComboType.byConfigKey(state.type()) != null
                    && ComboType.byConfigKey(state.type()).isControl(state.material());
            case "tower" -> TowerType.byConfigKey(state.type()) != null
                    && TowerType.byConfigKey(state.type()).core == state.material();
            case "furnace" -> state.type().equals(state.material().name())
                    && Set.of(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER).contains(state.material());
            case "workstation" -> state.type().equals(state.material().name()) && WorkstationStats.supports(state.material())
                    && WorkstationStats.upgrades(state.material()).containsAll(state.levels().keySet()) && state.owner() != null
                    && state.experience() == 0 && state.levels().entrySet().stream().allMatch(e -> e.getValue() <= WorkstationStats.maximum(e.getKey()));
            case "anvil" -> state.type().equals(state.material().name()) && AnvilRules.supports(state.material()) && state.owner() != null
                    && state.experience() == 0 && AnvilRules.UPGRADES.containsAll(state.levels().keySet())
                    && state.levels().entrySet().stream().allMatch(e -> e.getValue() <= AnvilRules.maximum(e.getKey()));
            default -> false;
        };
        if (!matching || state.levels().size() > 5 || !Double.isFinite(state.experience())
                || state.experience() < 0 || state.levels().entrySet().stream().anyMatch(e ->
                e.getKey().length() > 32 || e.getValue() < 0 || e.getValue() > 10)) {
            throw new IllegalArgumentException("invalid block state");
        }
    }

    private String text(CommandSender viewer, String key, Object... placeholders) {
        if (lang == null) return key;
        return viewer == null ? lang.text(org.bukkit.Bukkit.getConsoleSender(), key, placeholders)
                : lang.text(viewer, key, placeholders);
    }

    private static String titleKey(State state) {
        return switch (state.kind()) {
            case "combo" -> ComboType.byConfigKey(state.type()).nameKey();
            case "tower" -> TowerType.byConfigKey(state.type()).nameKey();
            case "workstation" -> WorkstationManager.nameKey(state.material());
            case "anvil" -> "anvil.title";
            default -> FurnaceManager.furnaceNameKey(state.material());
        };
    }

    private static String upgradeKey(String kind, String key) {
        if (kind.equals("workstation")) return "workstation.upgrade." + key;
        if (kind.equals("anvil")) return "anvil.path." + key;
        if (kind.equals("combo")) for (ComboUpgrade u : ComboUpgrade.values()) if (u.configKey.equals(key)) return u.nameKey();
        if (kind.equals("tower")) for (TowerUpgrade u : TowerUpgrade.values()) if (u.configKey.equals(key)) return u.nameKey();
        if (kind.equals("furnace")) for (FurnaceUpgrade u : FurnaceUpgrade.values()) if (u.configKey.equals(key)) return u.nameKey();
        return key;
    }
}
