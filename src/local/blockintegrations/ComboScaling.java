package local.blockintegrations;

final class ComboScaling {
    private ComboScaling() {
    }

    static int range(int base, int level, int blocksPerLevel) {
        return Math.max(1, Math.min(32, base + Math.max(0, level) * Math.max(0, blocksPerLevel)));
    }

    static double power(double base, int level, double amountPerLevel) {
        return Math.max(0.1D, base + Math.max(0, level) * Math.max(0.0D, amountPerLevel));
    }

    static int duration(int baseTicks, int level, int ticksPerLevel) {
        return Math.max(1, baseTicks + Math.max(0, level) * Math.max(0, ticksPerLevel));
    }

    static long cooldown(long baseTicks, int level, double reductionPerLevel,
            long minimumTicks) {
        double multiplier = Math.max(0.0D,
                1.0D - Math.max(0, level) * Math.max(0.0D, reductionPerLevel));
        return Math.max(Math.max(0L, minimumTicks), Math.round(Math.max(0L, baseTicks) * multiplier));
    }

    static boolean tooClose(int firstX, int firstY, int firstZ,
            int secondX, int secondY, int secondZ, int minimumDistance) {
        long x = (long) firstX - secondX;
        long y = (long) firstY - secondY;
        long z = (long) firstZ - secondZ;
        long minimum = Math.max(1, minimumDistance);
        return x * x + y * y + z * z < minimum * minimum;
    }
}
