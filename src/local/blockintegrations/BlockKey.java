package local.blockintegrations;

import java.util.UUID;
import org.bukkit.block.Block;

record BlockKey(UUID worldId, int x, int y, int z) {
    static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    static BlockKey parse(String serialized) {
        String[] parts = serialized.split(",", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid block key: " + serialized);
        }
        return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    /** URL-safe id for the web page ({@code <world-uuid>_<x>_<y>_<z>}). */
    String webId() {
        return worldId + "_" + x + "_" + y + "_" + z;
    }

    /** Parses {@link #webId()}; {@code null} for anything else (the value comes from a browser). */
    static BlockKey parseWebId(String id) {
        if (id == null || id.length() > 100) return null;
        String[] parts = id.split("_", -1);
        if (parts.length != 4 || parts[0].length() != 36) return null;
        try {
            return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    String serialize() {
        return worldId + "," + x + "," + y + "," + z;
    }

    int chunkX() {
        return x >> 4;
    }

    int chunkZ() {
        return z >> 4;
    }
}
