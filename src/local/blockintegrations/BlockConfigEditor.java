package local.blockintegrations;

import Vystorm.vystorm_core._uiMethods.settings.ConfigEditorInterface;
import Vystorm.vystorm_core.lang.Lang;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Makes the whole {@code config.yml} editable in Core's admin config editor ({@code /vadmin config}).
 * Labels reuse the texts of the settings sections ({@code settings.option.<path>.label/description}).
 */
final class BlockConfigEditor {
    /** A labelled key; {@code min} is NaN when the key has no slider range. */
    record Entry(String path, double min, double max, double step) {
        boolean ranged() { return !Double.isNaN(min); }
    }

    static final List<Entry> ENTRIES = List.of(
            range("workstations.extra-base-xp-cost", 1, 100000, 1),
            range("workstations.strength-base-xp-cost", 1, 100000, 1),
            range("workstations.duration-base-xp-cost", 1, 100000, 1),
            label("anvils.enabled"),
            range("anvils.base-xp-cost", 1, 100000, 1),
            range("anvils.unbreakable-xp-cost", 1, 1000000, 1),
            label("traps.enabled"),
            label("traps.default-automatic"),
            range("traps.default-cooldown-seconds", 10, 15, 5),
            range("traps.throw-power", 0.5, 3.0, 0.1),
            range("traps.sling-power", 0.5, 3.0, 0.1),
            range("traps.sling-lift", 0.2, 2.0, 0.1),
            range("traps.spike-damage", 0, 40, 0.5),
            range("radius", 1, 32, 1),
            range("cooldown-ticks", 0, 200, 1),
            label("include-trapped-chests"),
            label("messages.show-when-nothing-moved"),
            range("vacuum.radius", 1, 32, 1),
            range("vacuum.cooldown-ticks", 0, 200, 1),
            label("vacuum.messages.show-when-nothing-moved"),
            range("launcher.velocity", 0.1, 4.0, 0.01),
            range("launcher.minimum-upward-velocity", 0.0, 2.0, 0.01),
            range("launcher.cooldown-ticks", 0, 200, 1),
            label("launcher.play-sound"),
            range("booster.velocity", 0.1, 3.0, 0.01),
            range("booster.cooldown-ticks", 0, 200, 1),
            label("booster.play-sound"),
            label("booster.prevent-ice-melting"),
            range("float-launcher.velocity", 0.1, 4.0, 0.01),
            range("float-launcher.levitation-duration-ticks", 1, 1200, 1),
            range("float-launcher.levitation-amplifier", 0, 10, 1),
            range("float-launcher.cooldown-ticks", 0, 400, 1),
            label("float-launcher.play-sound"),
            label("combos.enabled"),
            range("combos.upgrades.maximum-level", 1, 10, 1),
            range("combos.scaling.cooldown-reduction-per-level", 0.0, 0.1, 0.001),
            range("combos.scaling.minimum-cooldown-ticks", 0, 100, 1),
            range("combos.heal.amount", 0.1, 20.0, 0.1),
            range("combos.heal.aura-radius", 1, 32, 1),
            range("combos.heal.pulse-cooldown-ticks", 1, 1200, 1),
            range("combos.heal.damage-cooldown-ticks", 0, 1200, 1),
            range("combos.heal.minimum-distance", 1, 64, 1),
            label("combos.heal.play-particles"),
            label("combos.heal.play-sound"),
            label("combos.warp-pad.enabled"),
            range("combos.warp-pad.base-distance", 8, 10000, 1),
            range("combos.warp-pad.distance-per-level", 0, 2000, 1),
            range("combos.warp-pad.cooldown-ticks", 0, 1200, 1),
            range("combos.warp-pad.max-per-player", 0, 100, 1),
            label("combos.warp-pad.play-particles"),
            label("combos.xp-collector.enabled"),
            range("combos.xp-collector.radius", 1, 16, 1),
            range("combos.xp-collector.capacity", 10, 100000, 1),
            range("combos.xp-collector.capacity-per-level", 0, 100000, 1),
            range("combos.xp-collector.player-safe-radius", 0.0, 16.0, 0.5),
            label("combos.xp-collector.play-particles"),
            label("towers.enabled"),
            range("towers.upgrades.maximum-level", 1, 10, 1),
            range("towers.max-per-player", 0, 100, 1),
            range("towers.attack-task-period-ticks", 1, 20, 1),
            label("towers.pause-when-loot-full"),
            label("towers.experience.require-nearby-player"),
            range("towers.experience.player-radius", 8.0, 128.0, 0.5),
            label("towers.experience.spawner-mobs"),
            label("towers.targeting.line-of-sight"),
            label("towers.targeting.ignore-named-mobs"),
            label("towers.loot.capture-nearby-plugin-drops"),
            label("towers.loot.drop-contents-when-broken"),
            label("furnaces.enabled"),
            range("furnaces.upgrades.maximum-level", 1, 10, 1),
            range("furnaces.speed-multiplier-per-level", 0.0, 1.0, 0.005),
            range("furnaces.fuel-efficiency-per-level", 0.0, 2.0, 0.005),
            range("furnaces.bonus-yield-chance-per-level", 0.0, 1.0, 0.001),
            range("furnaces.xp-boost-per-level", 0.0, 2.0, 0.01));

    private BlockConfigEditor() {}

    private static Entry range(String path, double min, double max, double step) { return new Entry(path, min, max, step); }
    private static Entry label(String path) { return new Entry(path, Double.NaN, Double.NaN, Double.NaN); }

    static String labelKey(String path) { return "settings.option." + path.replace('.', '-') + ".label"; }
    static String descriptionKey(String path) { return "settings.option." + path.replace('.', '-') + ".description"; }

    /** {@code reload} is the same path as {@code /blockintegrations reload}: re-read config.yml, apply to all managers. */
    static void register(ConfigEditorInterface config, Plugin plugin, Lang lang, Runnable reload) {
        config.register(plugin, "config.yml", reload);
        for (Entry entry : ENTRIES) {
            config.label(plugin, entry.path(), lang.ref(labelKey(entry.path())), lang.ref(descriptionKey(entry.path())));
            if (entry.ranged()) config.range(plugin, entry.path(), entry.min(), entry.max(), entry.step());
        }
    }
}
