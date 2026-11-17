package local.blockintegrations;

record FurnaceUpgradeStats(
        double speedPerLevel,
        double fuelEfficiencyPerLevel,
        double yieldChancePerLevel
) {
    double speedMultiplier(int level) {
        return 1.0D + Math.max(0, level) * speedPerLevel;
    }

    int cookTime(int vanillaCookTime, int level) {
        return Math.max(1, (int) Math.round(vanillaCookTime / speedMultiplier(level)));
    }

    double fuelMultiplier(int level) {
        return 1.0D + Math.max(0, level) * fuelEfficiencyPerLevel;
    }

    double yieldChance(int level) {
        return Math.max(0.0D, Math.min(1.0D,
                Math.max(0, level) * yieldChancePerLevel));
    }
}
