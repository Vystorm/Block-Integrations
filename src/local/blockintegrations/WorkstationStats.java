package local.blockintegrations;

import org.bukkit.Material;

/** Small, bounded workstation bonuses. Vanilla still handles recipes and input consumption. */
final class WorkstationStats {
    static boolean supports(Material type) {
        return type == Material.BREWING_STAND || type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE;
    }

    static java.util.List<String> upgrades(Material type) {
        return type == Material.BREWING_STAND ? java.util.List.of("speed", "fuel", "extra", "strength", "duration") : java.util.List.of("speed", "yield");
    }

    static int maximum(String upgrade) { return upgrade.equals("extra") ? 5 : 10; }
    static int level(int value) { return Math.clamp(value, 0, 10); }
    static int ticks(int original, int level) {
        return original <= 0 ? original : Math.max(1, (int) Math.ceil(original / (1.0 + .12 * level(level))));
    }
    static int fuel(int original, int level) {
        return original <= 0 ? original : (int) Math.min(Integer.MAX_VALUE, Math.round(original * (1.0 + .10 * level(level))));
    }
    static double yieldChance(int level) { return .025 * level(level); }
    static int cost(int currentLevel, int baseCost) {
        return (int) Math.min(Integer.MAX_VALUE, (long) baseCost * (level(currentLevel) + 1) * (level(currentLevel) + 1));
    }
}
