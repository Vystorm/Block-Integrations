package local.blockintegrations;

final class FurnaceSkillExperience {
    private static final double EPSILON = 0.0000001D;

    private FurnaceSkillExperience() {
    }

    static double total(int recipeExperience, int additionalInputs,
            int boostLevel, double bonusPerLevel) {
        int safeRecipeExperience = Math.max(0, recipeExperience);
        int processedInputs = 1 + Math.max(0, additionalInputs);
        return safeRecipeExperience * processedInputs
                * FurnaceExperienceBoost.multiplier(boostLevel, bonusPerLevel);
    }

    static double amountToTransfer(int recipeExperience, int additionalInputs,
            int boostLevel, double bonusPerLevel, boolean baseAlreadyAwarded) {
        double amount = total(recipeExperience, additionalInputs,
                boostLevel, bonusPerLevel);
        if (baseAlreadyAwarded) {
            amount -= Math.max(0, recipeExperience);
        }
        return Math.max(0.0D, amount);
    }

    static int payablePoints(double pendingExperience) {
        if (!Double.isFinite(pendingExperience) || pendingExperience <= 0.0D) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE,
                Math.floor(pendingExperience + EPSILON));
    }

    static double remainder(double pendingExperience, int paidExperience) {
        if (!Double.isFinite(pendingExperience) || pendingExperience <= 0.0D) {
            return 0.0D;
        }
        return Math.max(0.0D, pendingExperience - Math.max(0, paidExperience));
    }
}
