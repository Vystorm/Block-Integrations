package local.blockintegrations;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Rules of the warp pad network (lodestone on crying obsidian): names, reach, which pads a player may travel between
 * and whether the space on top of a pad is free. Pure logic; {@link ComboManager} does the world access.
 */
final class WarpPads {
    static final int MAX_NAME_LENGTH = 24;
    /** Destinations shown in one travel dialog (nearest first). */
    static final int MAX_DESTINATIONS = 24;
    /** Players one owner can share his pads with. */
    static final int MAX_GUESTS = 32;

    private WarpPads() {
    }

    /**
     * Player-typed pad name: letters, digits, spaces and a few punctuation marks, collapsed spaces, at most
     * {@link #MAX_NAME_LENGTH} characters; {@code null} when nothing usable is left (formatting tags never survive).
     */
    static String cleanName(String raw) {
        if (raw == null) return null;
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < raw.length() && name.length() < MAX_NAME_LENGTH; ) {
            int point = raw.codePointAt(i);
            i += Character.charCount(point);
            boolean allowed = Character.isLetterOrDigit(point) || " -_'.#&!".indexOf(point) >= 0;
            if (!allowed || point == ' ' && (name.isEmpty() || name.charAt(name.length() - 1) == ' ')) continue;
            name.appendCodePoint(point);
        }
        String cleaned = name.toString().strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Maximum travel distance in blocks of a pad with the given RANGE level. */
    static int reach(int base, int perLevel, int level) {
        return (int) Math.max(1L, Math.min(100_000L, (long) base + (long) Math.max(0, level) * Math.max(0, perLevel)));
    }

    static double distance(BlockKey from, BlockKey to) {
        double x = from.x() - to.x(), y = from.y() - to.y(), z = from.z() - to.z();
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** Owner travels freely; anybody else needs to be on the owner's guest list. Pads without owner are closed. */
    static boolean mayUse(UUID player, UUID owner, Collection<UUID> guestsOfOwner) {
        return owner != null && (owner.equals(player) || guestsOfOwner != null && guestsOfOwner.contains(player));
    }

    /** Result of a share/unshare change. */
    enum ShareResult { ADDED, REMOVED, UNCHANGED, FULL, SELF }

    /** Adds or removes {@code guest} on the owner's (mutable) guest list, at most {@link #MAX_GUESTS}. */
    static ShareResult share(java.util.Set<UUID> guests, UUID owner, UUID guest, boolean allow) {
        if (owner.equals(guest)) return ShareResult.SELF;
        if (!allow) return guests.remove(guest) ? ShareResult.REMOVED : ShareResult.UNCHANGED;
        if (guests.contains(guest)) return ShareResult.UNCHANGED;
        if (guests.size() >= MAX_GUESTS) return ShareResult.FULL;
        guests.add(guest);
        return ShareResult.ADDED;
    }

    /**
     * Destinations from {@code source}: other pads in the same world within {@code reach}, nearest first, at most
     * {@link #MAX_DESTINATIONS}. {@code usable} decides access per pad.
     */
    static <P> List<P> destinations(BlockKey source, Collection<P> pads, java.util.function.Function<P, BlockKey> key,
            java.util.function.Predicate<P> usable, int reach) {
        return pads.stream()
                .filter(pad -> !key.apply(pad).equals(source) && key.apply(pad).worldId().equals(source.worldId()))
                .filter(pad -> distance(source, key.apply(pad)) <= reach && usable.test(pad))
                .sorted(Comparator.comparingDouble(pad -> distance(source, key.apply(pad))))
                .limit(MAX_DESTINATIONS).toList();
    }

    /** Feet standing on the top face of the pad block (same column, at most 0.6 blocks above it). */
    static boolean standingOn(Location feet, BlockKey pad) {
        if (feet == null || feet.getWorld() == null || !feet.getWorld().getUID().equals(pad.worldId())) return false;
        double above = feet.getY() - (pad.y() + 1);
        return feet.getBlockX() == pad.x() && feet.getBlockZ() == pad.z() && above >= -0.01D && above <= 0.6D;
    }

    /** A block a player can stand in when arriving: air or a non-solid block that does not hurt ({@code solid} from the server). */
    static boolean passable(Material material, boolean solid) {
        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) return true;
        if (solid) return false;
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, POWDER_SNOW, SWEET_BERRY_BUSH, WITHER_ROSE, NETHER_PORTAL, END_PORTAL,
                 END_GATEWAY, COBWEB -> false;
            default -> true;
        };
    }
}
