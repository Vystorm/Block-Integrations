package local.blockintegrations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntBiFunction;

/**
 * Machine blueprints: a player saves the upgrade levels of one machine and copies them onto other machines of the same
 * type. Copying only raises levels (nothing is ever lowered or refunded) and each step is a normal paid upgrade. Pure
 * planning helpers, no Bukkit access.
 */
final class MachineBlueprints {
    private MachineBlueprints() {}

    /** Upgrade steps that bring {@code current} up to {@code target}, path by path in enum order; higher levels stay. */
    static <E extends Enum<E>> List<E> steps(Map<E, Integer> target, Map<E, Integer> current) {
        List<E> out = new ArrayList<>();
        target.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            int have = Math.max(0, current.getOrDefault(entry.getKey(), 0));
            for (int level = have; level < entry.getValue(); level++) out.add(entry.getKey());
        });
        return out;
    }

    /** XP for all steps, with {@code cost(upgrade, levelBeforeTheStep)} as the price of one step. */
    static <E extends Enum<E>> long cost(List<E> steps, Map<E, Integer> current, ToIntBiFunction<E, Integer> cost) {
        java.util.HashMap<E, Integer> levels = new java.util.HashMap<>(current);
        long total = 0;
        for (E step : steps) {
            int level = levels.getOrDefault(step, 0);
            total += Math.max(0, cost.applyAsInt(step, level));
            levels.put(step, level + 1);
        }
        return total;
    }
}
