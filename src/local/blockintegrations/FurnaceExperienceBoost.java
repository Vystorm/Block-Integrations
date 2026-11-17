package local.blockintegrations;

final class FurnaceExperienceBoost {
    private FurnaceExperienceBoost() {
    }

    static double multiplier(int level, double bonusPerLevel) {
        int safeLevel = Math.max(0,
                Math.min(FurnaceUpgrade.XP_BOOST.maximumLevel, level));
        double safeBonus = Double.isFinite(bonusPerLevel)
                ? Math.max(0.0D, bonusPerLevel) : 0.0D;
        return 1.0D + safeLevel * safeBonus;
    }

    static double additionalExperience(double recipeExperience, int additionalInputs,
            int boostLevel, double bonusPerLevel) {
        double safeRecipeExperience = Double.isFinite(recipeExperience)
                ? Math.max(0.0D, recipeExperience) : 0.0D;
        int safeAdditionalInputs = Math.max(0, additionalInputs);
        int processedInputs = 1 + safeAdditionalInputs;
        double boostBonus = multiplier(boostLevel, bonusPerLevel) - 1.0D;
        return safeRecipeExperience * (safeAdditionalInputs
                + processedInputs * boostBonus);
    }
}
