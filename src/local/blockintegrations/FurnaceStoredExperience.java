package local.blockintegrations;

import java.util.Map;
import org.bukkit.inventory.CookingRecipe;

final class FurnaceStoredExperience {
    private FurnaceStoredExperience() {
    }

    static double total(double carriedRemainder,
            Map<? extends CookingRecipe<?>, Integer> recipesUsed) {
        double total = Double.isFinite(carriedRemainder)
                ? Math.max(0.0D, carriedRemainder) : 0.0D;
        for (Map.Entry<? extends CookingRecipe<?>, Integer> entry
                : recipesUsed.entrySet()) {
            CookingRecipe<?> recipe = entry.getKey();
            Integer count = entry.getValue();
            if (recipe != null && count != null && count > 0
                    && Float.isFinite(recipe.getExperience())) {
                total = addRecipeExperience(total, recipe.getExperience(), count);
            }
        }
        return total;
    }

    static double addRecipeExperience(double current, double experience, int count) {
        if (!Double.isFinite(current) || !Double.isFinite(experience) || count <= 0) {
            return Double.isFinite(current) ? Math.max(0.0D, current) : 0.0D;
        }
        return Math.max(0.0D, current) + Math.max(0.0D, experience) * count;
    }

    static int payablePoints(double storedExperience) {
        if (!Double.isFinite(storedExperience) || storedExperience < 1.0D) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(storedExperience));
    }

    static double remainder(double storedExperience, int paidPoints) {
        if (!Double.isFinite(storedExperience)) {
            return 0.0D;
        }
        return Math.max(0.0D, storedExperience - Math.max(0, paidPoints));
    }
}
