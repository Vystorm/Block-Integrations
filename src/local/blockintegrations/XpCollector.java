package local.blockintegrations;

import Vystorm.vystorm_core.API;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;

/**
 * Enchanting table on a lapis block: pulls experience orbs around it into the block, up to its capacity. Orbs right
 * next to a player stay for that player, orbs in another city stay where they are. Owners take the XP out in the menu.
 */
final class XpCollector {
    /** Largest orb spawned when a full collector is broken (vanilla merges them further). */
    static final int MAX_DROP_ORB = 1000;

    private XpCollector() {
    }

    /** Storage limit in XP points for a POWER (capacity) level. */
    static int capacity(int base, int perLevel, int level) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE / 2, (long) base + (long) Math.max(0, level) * Math.max(0, perLevel)));
    }

    /** How many orbs of a merged orb stack fit into the remaining space; worthless orbs are left alone. */
    static int orbsToTake(int stored, int capacity, int experiencePerOrb, int count) {
        if (experiencePerOrb <= 0 || count <= 0 || stored >= capacity) return 0;
        return (int) Math.min(count, (capacity - (long) stored) / experiencePerOrb);
    }

    /** Points a player needs to reach the next level from his current progress (at least 1). */
    static int pointsToNextLevel(int expToLevel, float progress) {
        return Math.max(1, Math.round(expToLevel * (1.0F - Math.max(0.0F, Math.min(1.0F, progress)))));
    }

    /** Orb values for spilling {@code total} points when the collector is broken. */
    static List<Integer> spill(int total) {
        List<Integer> orbs = new ArrayList<>();
        for (int left = Math.max(0, total); left > 0; left -= MAX_DROP_ORB) orbs.add(Math.min(MAX_DROP_ORB, left));
        return orbs;
    }

    /**
     * One pull; returns the new stored amount. {@code playerSafeRadius} keeps orbs near (non-spectator) players for
     * them; a player's death drop is never taken (orbs spawned by a player's death, and any orb near a recent death
     * place in {@code deaths}). Loaded chunks only (nearby entity search), main thread only.
     */
    static int run(API core, Block table, int radius, int stored, int capacity, double playerSafeRadius, DeathOrbGuard deaths, Particles effects) {
        if (stored >= capacity) return stored;
        World world = table.getWorld();
        Location center = table.getLocation().add(0.5D, 0.5D, 0.5D);
        Optional<String> city = core.area().regions().top(center, "city").map(region -> region.id());
        long now = System.currentTimeMillis();
        int before = stored;
        for (ExperienceOrb orb : core.compat().platform().getNearbyEntitiesByType(world, ExperienceOrb.class, center, radius)) {
            if (stored >= capacity) break;
            if (!orb.isValid() || nearPlayer(core, orb.getLocation(), playerSafeRadius)) continue;
            if (BukkitFeatures.deathOrb(orb)) continue;
            Location at = orb.getLocation();
            if (deaths != null && deaths.protects(world.getUID(), at.getX(), at.getY(), at.getZ(), now)) { BukkitFeatures.markDeathOrb(orb); continue; }
            if (!core.area().regions().top(orb.getLocation(), "city").map(region -> region.id()).equals(city)) continue;
            int count = Math.max(1, BukkitFeatures.orbCount(orb));
            int taken = orbsToTake(stored, capacity, orb.getExperience(), count);
            if (taken <= 0) continue;
            stored += taken * orb.getExperience();
            if (effects != null) effects.spawn(orb.getLocation());
            if (taken >= count) orb.remove();
            else BukkitFeatures.orbCount(orb, count - taken);
        }
        if (stored > before) world.playSound(center, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25F, 0.7F);
        return stored;
    }

    /** Orbs a player dropped on death belong to that player, not to a collector. */
    static boolean deathDrop(Object reason) {
        return reason != null && reason.toString().equals("PLAYER_DEATH");
    }

    private static boolean nearPlayer(API core, Location at, double radius) {
        if (radius <= 0.0D) return false;
        for (Player player : core.compat().platform().getNearbyPlayers(at, radius)) {
            if (player.getGameMode() != GameMode.SPECTATOR && !player.isDead()) return true;
        }
        return false;
    }

    /** Spills stored XP as orbs on top of the collector (breaking it). */
    static void spillAt(Location at, int total) {
        World world = at.getWorld();
        if (world == null) return;
        for (int value : spill(total)) world.spawn(at, ExperienceOrb.class, orb -> orb.setExperience(value));
    }

    /** Particle hook so the pull can run without Core in tests. */
    interface Particles {
        void spawn(Location at);

        static Particles core(BlockIntegrations plugin) {
            return at -> plugin.core().particle().spawn(org.bukkit.Particle.ENCHANT, at, 6, .2, .2, .2, .5, null);
        }
    }
}
