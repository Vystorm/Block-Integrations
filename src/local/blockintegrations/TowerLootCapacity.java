package local.blockintegrations;

import org.bukkit.inventory.ItemStack;

/** Strict capacity check: an inventory is full only when every slot is occupied to that item's stack limit. */
final class TowerLootCapacity {
    private TowerLootCapacity() { }

    static boolean isFull(ItemStack[] contents) {
        if (contents == null || contents.length == 0) return false;
        for (ItemStack item : contents) {
            if (item == null || isAir(item.getType()) || item.getAmount() < item.getMaxStackSize()) return false;
        }
        return true;
    }

    private static boolean isAir(org.bukkit.Material material) {
        return material == org.bukkit.Material.AIR || material == org.bukkit.Material.CAVE_AIR
                || material == org.bukkit.Material.VOID_AIR;
    }
}
