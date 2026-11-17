package local.blockintegrations;

record TowerCombatStats(
        double baseDamage,
        double damagePerLevel,
        double baseRange,
        double rangePerLevel,
        int baseCooldownTicks,
        double speedMultiplierPerLevel,
        double areaRadius,
        int fireTicks
) {
    double damage(int level) {
        return baseDamage + damagePerLevel * level;
    }

    double range(int level) {
        return baseRange + rangePerLevel * level;
    }

    int cooldownTicks(int level) {
        return Math.max(2, (int) Math.round(baseCooldownTicks
                * Math.pow(speedMultiplierPerLevel, level)));
    }
}
