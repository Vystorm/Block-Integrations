package local.blockintegrations;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

final class AnvilRules {
    static boolean supports(Material material) { return material == Material.ANVIL || material == Material.CHIPPED_ANVIL || material == Material.DAMAGED_ANVIL; }
    static final List<String> UPGRADES = List.of("efficiency", "enchantments", "durability");
    static int maximum(String upgrade) { return switch (upgrade) { case "efficiency" -> 10; case "enchantments" -> 5; case "durability" -> 1; default -> 0; }; }
    static int cost(String upgrade, int level, int base, int durabilityCost) {
        return upgrade.equals("durability") ? durabilityCost : Math.multiplyExact(base, (level + 1) * (level + 1));
    }
    static int repairCost(int cost, int efficiency) { return cost <= 0 ? cost : Math.max(1, (int) Math.ceil(cost * (1 - .05 * Math.clamp(efficiency, 0, 10)))); }
    static int mergedLevel(int before, int incoming, int vanillaMax, int bonus) {
        int wanted = before > 0 && before == incoming ? before + 1 : Math.max(before, incoming);
        return Math.min(wanted, Math.max(before, Math.min(255, vanillaMax + Math.clamp(bonus, 0, 5))));
    }
    static Map<Enchantment, Integer> enchants(ItemMeta meta) { return meta instanceof EnchantmentStorageMeta book ? book.getStoredEnchants() : meta.getEnchants(); }
    static ItemStack enchant(ItemStack left, ItemStack right, ItemStack vanilla, int bonus) {
        ItemStack result = vanilla.clone();
        if (right == null || right.getType().isAir() || left == null) return result;
        ItemMeta meta = result.getItemMeta();
        var old = enchants(left.getItemMeta());
        var accepted = enchants(meta);
        for (var entry : enchants(right.getItemMeta()).entrySet()) {
            Enchantment enchant = entry.getKey();
            // Vanilla decides item compatibility and mutually exclusive enchantments.
            if (!accepted.containsKey(enchant)) continue;
            int next = mergedLevel(old.getOrDefault(enchant, 0), entry.getValue(), enchant.getMaxLevel(), bonus);
            if (meta instanceof EnchantmentStorageMeta book) book.addStoredEnchant(enchant, next, true);
            else meta.addEnchant(enchant, next, true);
        }
        result.setItemMeta(meta);
        return result;
    }
}
