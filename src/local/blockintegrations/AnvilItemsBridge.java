package local.blockintegrations;

import Vystorm.vystorm_core.item.ItemsInterface;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** All item mutations belong to the item provider; no dependency on VystormItems classes. */
final class AnvilItemsBridge {
    static boolean managed(ItemsInterface items, ItemStack item) { return item != null && items.managed(item); }
    static ItemStack prepare(ItemsInterface items, ItemStack left, ItemStack right, ItemStack result, String rename, Player player) {
        if (!managed(items, left)) return result;
        return items.prepareAnvil(left, right, result, rename, player).orElse(null);
    }
    static boolean commit(ItemsInterface items, ItemStack result, Player player) { return items.commitAnvil(result, player); }
}
