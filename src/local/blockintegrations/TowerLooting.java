package local.blockintegrations;

final class TowerLooting {
    private TowerLooting() {
    }

    static int bonusAmount(int level, double roll) {
        int safeLevel = Math.max(0,
                Math.min(TowerUpgrade.LOOTING.maximumLevel, level));
        if (safeLevel == 0 || !Double.isFinite(roll)) {
            return 0;
        }
        double safeRoll = Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll));
        return Math.min(safeLevel, (int) Math.floor(safeRoll * (safeLevel + 1)));
    }
}
