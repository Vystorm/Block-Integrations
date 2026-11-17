package local.blockintegrations;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * XP collectors leave a player's death drop alone: orbs within {@link #RADIUS} blocks of a place where a player died
 * and dropped XP stay for {@link #MILLIS} (orbs merge with others nearby, so the orb's spawn reason alone is not
 * enough). Bounded, oldest deaths first out. Main thread only.
 */
final class DeathOrbGuard {
    static final double RADIUS = 8.0D;
    static final long MILLIS = 5L * 60L * 1000L;
    static final int MAX_PLACES = 512;

    private record Place(UUID world, double x, double y, double z, long until) {}

    private final Deque<Place> places = new ArrayDeque<>();

    /** A player died here and dropped XP. */
    void died(UUID world, double x, double y, double z, long now) {
        prune(now);
        places.addLast(new Place(world, x, y, z, now + MILLIS));
        while (places.size() > MAX_PLACES) places.removeFirst();
    }

    /** Whether an orb at this spot may belong to a recent death drop. */
    boolean protects(UUID world, double x, double y, double z, long now) {
        prune(now);
        for (Place place : places) {
            if (!place.world().equals(world)) continue;
            double dx = place.x() - x, dy = place.y() - y, dz = place.z() - z;
            if (dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS) return true;
        }
        return false;
    }

    boolean isEmpty() { return places.isEmpty(); }

    void clear() { places.clear(); }

    private void prune(long now) {
        // Entries are added in time order with the same lifetime, so the expired ones are at the front.
        while (!places.isEmpty() && places.peekFirst().until() <= now) places.removeFirst();
    }
}
