package local.blockintegrations;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitTask;

/** Tower-specific palettes composed through Core's shared particle budget and animation scheduler. */
final class TowerEffects {
    private final BlockIntegrations plugin;
    private final java.util.Set<BukkitTask> active = new java.util.HashSet<>();
    private static final int FRAMES = 4;
    private static final int MAX_ANIMATIONS = 128;
    private long nextWarning;

    TowerEffects(BlockIntegrations plugin) { this.plugin = plugin; }

    record Palette(Particle trail, Particle muzzle, Particle hit) {}
    static Palette palette(TowerType type) {
        return switch (type) {
            case ZOMBIE_STARTER -> new Palette(Particle.CRIT, Particle.SMOKE, Particle.HAPPY_VILLAGER);
            case PLAYER_STARTER -> new Palette(Particle.END_ROD, Particle.CRIT, Particle.CRIT);
            case ARCHER -> new Palette(Particle.CRIT, Particle.SWEEP_ATTACK, Particle.CRIT);
            case CANNON -> new Palette(Particle.SMOKE, Particle.LARGE_SMOKE, Particle.EXPLOSION);
            case FLAME -> new Palette(Particle.SMALL_FLAME, Particle.FLAME, Particle.FLAME);
            case PIGLIN -> new Palette(Particle.ELECTRIC_SPARK, Particle.ELECTRIC_SPARK, Particle.ELECTRIC_SPARK);
            case DRAGON -> new Palette(Particle.DRAGON_BREATH, Particle.REVERSE_PORTAL, Particle.DRAGON_BREATH);
        };
    }

    void shot(TowerType type, Location source, Location destination) {
        safely(() -> drawShot(type, source, destination));
    }

    private void drawShot(TowerType type, Location source, Location destination) {
        if (!enabled() || !loaded(source) || !loaded(destination)
                || !Objects.equals(source.getWorld(), destination.getWorld())) return;
        Palette palette = palette(type);
        var particles = plugin.core().particle();
        particles.spawn(palette.muzzle(), source, type == TowerType.ARCHER ? 1 : 5, .10, .10, .10, .01, null);
        Location start = source.clone(), end = destination.clone();
        animate(frame -> {
            Location from = point(start, end, (frame - 1.0) / FRAMES, type);
            Location to = point(start, end, (double) frame / FRAMES, type);
            if (loaded(from) && loaded(to)) {
                int points = Math.clamp((int) Math.ceil(from.distance(to) / .35) + 1, 2, 24);
                plugin.core().particle().line(palette.trail(), from, to, points, null);
            }
        });
    }

    void impact(TowerType type, Location target) {
        safely(() -> drawImpact(type, target));
    }

    private void drawImpact(TowerType type, Location target) {
        if (!enabled() || !loaded(target)) return;
        Palette palette = palette(type);
        Location at = target.clone();
        plugin.core().particle().spawn(palette.hit(), at, type == TowerType.CANNON ? 1 : 10,
                .20, .25, .20, .025, null);
        if (type.attackStyle == TowerType.AttackStyle.AREA || type == TowerType.FLAME) {
            animate(frame -> {
                if (loaded(at)) plugin.core().particle().circle(palette.trail(), at, .20 + frame * .22, 12, null);
            });
        }
    }

    static Location point(Location from, Location to, double fraction, TowerType type) {
        Location point = from.clone().add(to.toVector().subtract(from.toVector()).multiply(fraction));
        if (type == TowerType.CANNON) point.add(0, 4 * fraction * (1 - fraction) * 1.25, 0);
        return point;
    }

    private void animate(java.util.function.IntConsumer frame) {
        if (active.size() >= MAX_ANIMATIONS) return;
        int[] index = {0};
        BukkitTask[] scheduled = {null};
        BukkitTask task = plugin.core().particle().repeat(plugin, () -> {
            try {
                int current = ++index[0];
                if (enabled() && current <= FRAMES) frame.accept(current);
            } catch (RuntimeException | LinkageError failure) {
                if (scheduled[0] != null) { active.remove(scheduled[0]); scheduled[0].cancel(); }
                throw failure;
            } finally {
                if (index[0] >= FRAMES) active.remove(scheduled[0]);
            }
        }, 1, FRAMES);
        scheduled[0] = task;
        if (task != null && index[0] < FRAMES) active.add(task);
    }

    void close() {
        for (BukkitTask task : active) task.cancel();
        active.clear();
    }

    private void safely(Runnable effect) {
        try { effect.run(); }
        catch (RuntimeException | LinkageError failure) {
            long now = System.currentTimeMillis();
            if (now >= nextWarning) {
                nextWarning = now + 30_000;
                Vystorm.vystorm_core._parsersDebuggerEtc.debugger.DebugMessages.mid(
                        "[TowerEffects] Cosmetic effect unavailable: " + failure.getClass().getSimpleName());
            }
        }
    }

    private boolean enabled() { return plugin.isEnabled() && plugin.getConfig().getBoolean("towers.visual-effects", true); }
    private static boolean loaded(Location location) {
        return location.getWorld() != null && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
