package local.blockintegrations;

import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.view.AnvilView;

public final class ModernPlatformAccess implements PlatformAccess {
    public String rename(InventoryView view) { return ((AnvilView) view).getRenameText(); }
    public int cost(InventoryView view) { return ((AnvilView) view).getRepairCost(); }
    public void cost(InventoryView view, int cost) { ((AnvilView) view).setRepairCost(cost); }
    public void unlock(InventoryView view) {
        ((AnvilView) view).setMaximumRepairCost(Integer.MAX_VALUE);
        if (PlatformAccess.hasMethod(AnvilView.class, "bypassEnchantmentLevelRestriction", boolean.class))
            ((AnvilView) view).bypassEnchantmentLevelRestriction(true);
    }
    public void brew(BrewingStartEvent event, int level) {
        if (event.getBrewingTime() > 0) {
            event.setBrewingTime(WorkstationStats.ticks(event.getBrewingTime(), level));
            event.setRecipeBrewTime(WorkstationStats.ticks(event.getRecipeBrewTime(), level));
        }
    }
}
