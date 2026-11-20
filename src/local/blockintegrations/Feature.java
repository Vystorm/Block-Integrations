package local.blockintegrations;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Every feature of the plugin with its on/off key in config.yml. All are on by default, so a config written before a
 * key existed behaves as before. The description shown in {@code /vadmin features} is the language key
 * {@code switches.<id>} ({@link #description()}). Keys that existed before 2.44.0 keep their names; {@code combos.enabled}
 * now means only the combo upgrades (see {@link #migrateComboGate}).
 */
enum Feature {
    /** Master switch: no block combo, tower, trap, furnace, workstation or anvil reacts. */
    BLOCKS("blocks.enabled", null),
    SORTER("sorter.enabled", null),
    SORT_LOCKS("sort-locks.enabled", null),
    VACUUM("vacuum.enabled", null),
    LAUNCHER("launcher.enabled", null),
    BOOSTER("booster.enabled", null),
    FLOAT_LIFT("float-launcher.enabled", null),
    /** Older key; since 2.44.0 only the upgrade menus and upgrade effects of the combos. */
    COMBO_UPGRADES("combos.enabled", null),
    HEAL("combos.heal.enabled", null),
    HARVESTER("combos.harvester.enabled", null),
    /** Older key that keeps its name. */
    WARP_PADS("combos.warp-pad.enabled", null),
    /** Older key that keeps its name. */
    XP_COLLECTOR("combos.xp-collector.enabled", null),
    TOWERS("towers.enabled", null),
    /** Older key that keeps its name; declared as a feature. */
    TOWER_EFFECTS("towers.visual-effects", TOWERS),
    TRAPS("traps.enabled", null),
    FURNACES("furnaces.enabled", null),
    /** Older key that keeps its name; declared as a feature. */
    BREWING("workstations.brewing-stands", null),
    /** Older key that keeps its name; declared as a feature. */
    CAMPFIRES("workstations.campfires", null),
    ANVILS("anvils.enabled", null),
    BLUEPRINTS("machine-blueprints.enabled", null),
    DASHBOARD("dashboard.enabled", null),
    MENU("menu.enabled", null),
    WEB("web.enabled", null),
    /** Older key that keeps its name; declared as a feature. */
    TOWER_MARKERS("client.tower-markers", TOWERS),
    /** Older key that keeps its name; declared as a feature. */
    PORTABLE_TOOLTIPS("client.portable-tooltips", null),
    /** Read once when the plugin starts. */
    TUTORIAL("tutorial.enabled", null);

    final String path;
    /** The switch this one needs besides blocks.enabled, or null. */
    final Feature needs;

    Feature(String path, Feature needs) {
        this.path = path;
        this.needs = needs;
    }

    /** Language key of the one-line description. */
    String description() { return "switches." + name().toLowerCase(Locale.ROOT).replace('_', '-'); }

    /** Language key of the readable name in {@code /vadmin features} and in "This needs ..." (2.44.1). */
    String label() { return "switch-names." + name().toLowerCase(Locale.ROOT).replace('_', '-'); }

    /** Applies only after a server restart. */
    boolean needsRestart() { return this == TUTORIAL; }

    /**
     * Until 2.43.0 {@code combos.enabled: false} also switched off the heal block, the harvester, warp pads and the XP
     * collector. A config from then (no {@code combos.heal.enabled} written yet) with {@code combos.enabled: false} gets
     * those four switched off explicitly once, so nothing that was off turns on. Keys the owner already wrote are kept.
     *
     * @return whether the config was changed (and should be saved)
     */
    static boolean migrateComboGate(ConfigurationSection config) {
        if (config == null || config.contains(HEAL.path, true) || !config.contains(COMBO_UPGRADES.path, true)
                || config.getBoolean(COMBO_UPGRADES.path, true)) return false;
        for (Feature feature : List.of(HEAL, HARVESTER, WARP_PADS, XP_COLLECTOR))
            if (!config.contains(feature.path, true) || feature == WARP_PADS || feature == XP_COLLECTOR) config.set(feature.path, false);
        return true;
    }

    /** The switches as read from a config; a missing key counts as on. */
    static final class Switches {
        private final Set<Feature> off = EnumSet.noneOf(Feature.class);

        static Switches read(ConfigurationSection config) {
            Switches switches = new Switches();
            if (config != null) for (Feature feature : values()) if (!config.getBoolean(feature.path, true)) switches.off.add(feature);
            return switches;
        }

        /** Everything on, except the given features. */
        static Switches without(Feature... off) {
            Switches switches = new Switches();
            switches.off.addAll(List.of(off));
            return switches;
        }

        /** On when its own switch, the switch it needs and blocks.enabled are on. */
        boolean on(Feature feature) {
            if (off.contains(feature)) return false;
            if (feature != BLOCKS && off.contains(BLOCKS)) return false;
            return feature.needs == null || on(feature.needs);
        }

        Set<Feature> off() { return off.isEmpty() ? EnumSet.noneOf(Feature.class) : EnumSet.copyOf(off); }

        @Override public boolean equals(Object other) { return other instanceof Switches s && s.off.equals(off); }

        @Override public int hashCode() { return off.hashCode(); }
    }
}
