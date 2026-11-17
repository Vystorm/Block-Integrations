package local.blockintegrations;

import java.util.*;
import java.util.random.RandomGenerator;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;

/** One roll per finished potion, retained through subsequent vanilla brewing steps. */
final class BrewingBonuses {
    static final NamespacedKey ROLLED = new NamespacedKey("blockintegrations", "brewing_bonus_rolled");
    static double chance(String upgrade, int level) {
        return upgrade.equals("extra") ? Math.clamp(level, 0, 5) / 100.0 : Math.clamp(level, 0, 10) / 10.0;
    }
    static int duration(int ticks, boolean instant, int level) {
        return instant || ticks < 0 ? ticks : (int) Math.min(Integer.MAX_VALUE, (long) ticks + 500L * Math.clamp(level, 0, 10));
    }
    static boolean rolled(ItemStack stack) {
        return stack != null && stack.getItemMeta() instanceof PotionMeta meta
                && meta.getPersistentDataContainer().has(ROLLED, PersistentDataType.BYTE);
    }
    static ItemStack apply(ItemStack source, ItemStack output, int extra, int strength, int duration, RandomGenerator random) {
        if (output == null || !(output.getItemMeta() instanceof PotionMeta original) || rolled(output)) return output;
        if (rolled(source)) {
            var carried = output.clone(); var meta = carried.getItemMeta();
            meta.getPersistentDataContainer().set(ROLLED, PersistentDataType.BYTE, (byte) 1);
            carried.setItemMeta(meta); return carried;
        }
        if (extra + strength + duration <= 0) return output;
        Map<PotionEffectType, PotionEffect> effects = new LinkedHashMap<>();
        if (original.getBasePotionType() != null)
            for (var effect : original.getBasePotionType().getPotionEffects()) effects.put(effect.getType(), effect);
        for (var effect : original.getCustomEffects()) effects.put(effect.getType(), effect);
        if (effects.isEmpty()) return output; // Water/awkward bottles must not consume a roll.
        ItemStack result = output.clone();
        PotionMeta meta = (PotionMeta) result.getItemMeta();
        boolean stronger = random.nextDouble() < chance("strength", strength);
        for (var effect : effects.values()) {
            int amplifier = stronger ? Math.min(255, effect.getAmplifier() + 1) : effect.getAmplifier();
            meta.addCustomEffect(new PotionEffect(effect.getType(), duration(effect.getDuration(), effect.getType().isInstant(), duration),
                    amplifier, effect.isAmbient(), effect.hasParticles(), effect.hasIcon()), true);
        }
        if (random.nextDouble() < chance("extra", extra)) {
            var candidates = new ArrayList<>(List.of(PotionEffectType.SPEED, PotionEffectType.REGENERATION,
                    PotionEffectType.FIRE_RESISTANCE, PotionEffectType.WATER_BREATHING,
                    PotionEffectType.NIGHT_VISION, PotionEffectType.JUMP_BOOST, PotionEffectType.RESISTANCE));
            candidates.removeAll(effects.keySet());
            if (!candidates.isEmpty()) meta.addCustomEffect(new PotionEffect(candidates.get(random.nextInt(candidates.size())),
                    duration(600, false, duration), 0), true);
        }
        // Stamp failed rolls too: adding redstone/gunpowder cannot reroll the same bottle.
        meta.getPersistentDataContainer().set(ROLLED, PersistentDataType.BYTE, (byte) 1);
        result.setItemMeta(meta);
        return result;
    }
}
