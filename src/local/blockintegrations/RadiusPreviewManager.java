package local.blockintegrations;

import Vystorm.vystorm_core.schedule.TaskGroup;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Short, per-viewer radius rings; one shared task bounds the effect and is stopped when unused. */
final class RadiusPreviewManager implements Listener {
    static final int MAX_ACTIVE_VIEWERS = 16;
    static final int MAX_PARTICLES_PER_FRAME = 48;
    static final int MAX_FRAMES = 4;
    static final long REQUEST_COOLDOWN_MILLIS = 1_000L;
    private static final long FRAME_PERIOD_TICKS = 10L;
    private static final double MAX_VIEW_DISTANCE_SQUARED = 48.0D * 48.0D;

    private record Preview(Location center, double radius, int framesLeft, Predicate<Player> authorized) { }

    private final BlockIntegrations plugin;
    private final TaskGroup tasks;
    private final Map<UUID, Preview> active = new HashMap<>();
    private final Map<UUID, Long> nextRequest = new HashMap<>();
    private BukkitTask tickTask;

    RadiusPreviewManager(BlockIntegrations plugin) {
        this.plugin = plugin;
        tasks = plugin.core().tasks().of(plugin, "BlockIntegrations/RadiusPreview");
    }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    void show(Player player, Location center, double radius, Predicate<Player> authorized) {
        if (player == null || !player.isOnline() || center == null || center.getWorld() == null
                || !Double.isFinite(radius) || radius <= 0.0D || authorized == null) return;
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        if (now < nextRequest.getOrDefault(playerId, 0L)) return;
        if (!active.containsKey(playerId) && active.size() >= MAX_ACTIVE_VIEWERS) return;
        if (!authorized.test(player)) return;

        Location anchor = center.clone();
        active.put(playerId, new Preview(anchor, radius, MAX_FRAMES, authorized));
        nextRequest.put(playerId, now + REQUEST_COOLDOWN_MILLIS);
        ensureTask();
    }

    void shutdown() {
        active.clear();
        nextRequest.clear();
        stopTask();
        tasks.close();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        active.remove(playerId);
        nextRequest.remove(playerId);
        if (active.isEmpty()) stopTask();
    }

    private void ensureTask() {
        if (tickTask == null || tickTask.isCancelled()) {
            tickTask = tasks.timer(1L, FRAME_PERIOD_TICKS, this::tick);
        }
    }

    private void stopTask() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Preview>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Preview> entry = iterator.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Preview preview = entry.getValue();
            if (player == null || !player.isOnline() || preview.framesLeft() <= 0
                    || !player.getWorld().equals(preview.center().getWorld())
                    || player.getLocation().distanceSquared(preview.center()) > MAX_VIEW_DISTANCE_SQUARED
                    || !preview.authorized().test(player)) {
                iterator.remove();
                continue;
            }
            draw(player, preview.center(), preview.radius());
            if (preview.framesLeft() == 1) iterator.remove();
            else entry.setValue(new Preview(preview.center(), preview.radius(), preview.framesLeft() - 1,
                    preview.authorized()));
        }
        if (active.isEmpty()) stopTask();
    }

    private void draw(Player player, Location center, double radius) {
        int points = pointCountForRadius(radius);
        for (int i = 0; i < points; i++) {
            double angle = 2.0D * Math.PI * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            plugin.core().particle().spawn(player, Particle.END_ROD, point, 1, null);
        }
    }

    static int pointCountForRadius(double radius) {
        return (int) Math.min(MAX_PARTICLES_PER_FRAME, Math.max(16, Math.ceil(radius * 2.0D)));
    }
}
