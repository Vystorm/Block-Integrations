package local.blockintegrations;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.EntityType;

/** Restrictive targeting and explicit per-player rights, separate from ownership and demolition. */
final class TowerControls {
    enum Right { USE(1), LOOT(2), UPGRADE(4); final int bit; Right(int bit) { this.bit = bit; } }
    enum TargetProfile {
        ALL, UNDEAD, ARTHROPODS, RAIDERS, NONE;
        boolean includes(EntityType type) {
            return switch (this) {
                case ALL -> true;
                case NONE -> false;
                case UNDEAD -> switch (type) {
                    case ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER, SKELETON, STRAY, WITHER_SKELETON, BOGGED, PHANTOM, ZOMBIFIED_PIGLIN, WITHER -> true;
                    default -> false;
                };
                case ARTHROPODS -> switch (type) { case SPIDER, CAVE_SPIDER, SILVERFISH, ENDERMITE -> true; default -> false; };
                case RAIDERS -> switch (type) { case PILLAGER, VINDICATOR, EVOKER, RAVAGER, WITCH, VEX -> true; default -> false; };
            };
        }
        static TargetProfile load(String text) {
            if (text == null) return ALL;
            try { return valueOf(text); } catch (IllegalArgumentException invalid) { return NONE; }
        }
    }
    enum RedstoneMode {
        ALWAYS, POWERED, UNPOWERED, DISABLED;
        boolean active(boolean signal) { return this == ALWAYS || this == POWERED && signal || this == UNPOWERED && !signal; }
        static RedstoneMode load(String text) {
            if (text == null) return ALWAYS;
            try { return valueOf(text); } catch (IllegalArgumentException invalid) { return DISABLED; }
        }
    }
    private final Map<UUID, Integer> grants = new LinkedHashMap<>();
    boolean allows(UUID player, Right right) { return (grants.getOrDefault(player, 0) & right.bit) != 0; }
    boolean toggle(UUID player, Right right) {
        int current = grants.getOrDefault(player, 0);
        if (current == 0 && grants.size() >= 32) return false;
        int changed = current ^ right.bit;
        if (changed == 0) grants.remove(player); else grants.put(player, changed);
        return true;
    }
    void remove(UUID player) { grants.remove(player); }
    Map<String, Integer> snapshot() {
        Map<String, Integer> result = new LinkedHashMap<>();
        grants.forEach((id, bits) -> result.put(id.toString(), bits));
        return Map.copyOf(result);
    }
    void load(Map<String, Integer> saved) {
        grants.clear();
        if (saved == null) return;
        for (var entry : saved.entrySet()) {
            if (grants.size() == 32) break;
            try {
                if (entry.getValue() != null && entry.getValue() > 0 && (entry.getValue() & ~7) == 0)
                    grants.put(UUID.fromString(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException | NullPointerException invalid) { /* malformed entry grants nothing */ }
        }
    }
}
