package local.blockintegrations;

import Vystorm.vystorm_core.compat.InventoryCompat;
import Vystorm.vystorm_core.compat.ServerCompat;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;

interface PlatformAccess {
    PlatformAccess INSTANCE = select(ServerCompat.hasClass("org.bukkit.inventory.view.AnvilView"));

    static boolean hasMethod(Class<?> type, String name, Class<?>... arguments) {
        try { type.getMethod(name, arguments); return true; }
        catch (NoSuchMethodException missing) { return false; }
    }

    static PlatformAccess brewing() {
        return hasMethod(BrewingStartEvent.class, "getBrewingTime") ? INSTANCE : new Legacy();
    }

    static PlatformAccess select(boolean modern) {
        if (!modern) return new Legacy();
        try {
            return (PlatformAccess) Class.forName("local.blockintegrations.ModernPlatformAccess").getConstructor().newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot load modern Paper adapter", failure);
        }
    }

    String rename(InventoryView view);
    int cost(InventoryView view);
    void cost(InventoryView view, int cost);
    void unlock(InventoryView view);
    void brew(BrewingStartEvent event, int level);

    static Object viewCall(InventoryView view, String method, Class<?>[] types, Object... args) {
        try {
            return InventoryView.class.getMethod(method, types).invoke(view, args);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot access inventory view", failure);
        }
    }

    static HumanEntity player(InventoryView view) {
        return (HumanEntity) viewCall(view, "getPlayer", new Class<?>[0]);
    }

    static void title(InventoryView view, String title) {
        viewCall(view, "setTitle", new Class<?>[]{String.class}, title);
    }

    final class Legacy implements PlatformAccess {
        private AnvilInventory inventory(InventoryView view) { return (AnvilInventory) InventoryCompat.top(view); }
        public String rename(InventoryView view) { return inventory(view).getRenameText(); }
        public int cost(InventoryView view) { return inventory(view).getRepairCost(); }
        public void cost(InventoryView view, int cost) { inventory(view).setRepairCost(cost); }
        public void unlock(InventoryView view) { inventory(view).setMaximumRepairCost(Integer.MAX_VALUE); }
        public void brew(BrewingStartEvent event, int level) {
            if (event.getTotalBrewTime() > 0) event.setTotalBrewTime(WorkstationStats.ticks(event.getTotalBrewTime(), level));
        }
    }
}
