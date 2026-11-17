package local.blockintegrations;

import java.util.EnumMap;
import java.util.Map;

final class Combo {
    final BlockKey key;
    final ComboType type;
    private final Map<ComboUpgrade, Integer> levels = new EnumMap<>(ComboUpgrade.class);
    private final int maximumLevel;
    /** Builder (since 2.20.0; {@code null} for older combos until someone uses them). Only the web page uses it. */
    private java.util.UUID owner;

    Combo(BlockKey key, ComboType type, Map<ComboUpgrade, Integer> storedLevels,
            int maximumLevel) {
        this.key = key;
        this.type = type;
        this.maximumLevel = Math.max(1, maximumLevel);
        for (ComboUpgrade upgrade : ComboUpgrade.values()) {
            int stored = storedLevels.getOrDefault(upgrade, 0);
            levels.put(upgrade, type.supports(upgrade)
                    ? Math.max(0, Math.min(this.maximumLevel, stored)) : 0);
        }
    }

    /** Player-given warp pad name; {@code null} shows the coordinates. */
    private String name;
    /** XP points held by an XP collector. */
    private int storedXp;
    private HarvestProfile harvestProfile = HarvestProfile.ALL;

    HarvestProfile harvestProfile() {
        return harvestProfile;
    }

    void harvestProfile(HarvestProfile profile) {
        harvestProfile = profile == null ? HarvestProfile.ALL : profile;
    }

    String name() {
        return name;
    }

    void name(String name) {
        this.name = name;
    }

    int storedXp() {
        return storedXp;
    }

    void storedXp(int storedXp) {
        this.storedXp = Math.max(0, storedXp);
    }

    java.util.UUID owner() {
        return owner;
    }

    void owner(java.util.UUID owner) {
        this.owner = owner;
    }

    int level(ComboUpgrade upgrade) {
        return levels.getOrDefault(upgrade, 0);
    }

    boolean upgrade(ComboUpgrade upgrade) {
        if (!type.supports(upgrade) || level(upgrade) >= maximumLevel) {
            return false;
        }
        levels.put(upgrade, level(upgrade) + 1);
        return true;
    }
}
