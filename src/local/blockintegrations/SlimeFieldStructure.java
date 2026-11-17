package local.blockintegrations;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Resolves a slime-field block to the launcher block that controls it. */
final class SlimeFieldStructure {
    private static final int FIELD_RADIUS = 1;

    private SlimeFieldStructure() {
    }

    /**
     * Returns the launcher anchor for a slime block, without causing a chunk to load.
     */
    static Block controlFor(Block touched) {
        if (touched == null || touched.getType() != Material.SLIME_BLOCK) {
            return null;
        }

        World world = touched.getWorld();
        if (world == null) {
            return null;
        }
        int y = touched.getY();
        // Existing independently upgraded launchers always keep their own identity.
        if (isRedstone(blockAt(world, touched.getX(), y - 1, touched.getZ()))) return touched;

        return completeControlNear(touched, world);
    }

    private static Block completeControlNear(Block touched, World world) {
        int x = touched.getX();
        int y = touched.getY();
        int z = touched.getZ();
        for (int centerX = x - FIELD_RADIUS; centerX <= x + FIELD_RADIUS; centerX++) {
            for (int centerZ = z - FIELD_RADIUS; centerZ <= z + FIELD_RADIUS; centerZ++) {
                Block center = blockAt(world, centerX, y, centerZ);
                if (complete(center)) {
                    return center;
                }
            }
        }
        return null;
    }

    /** Returns whether {@code center} is the redstone-anchored centre of a complete 3x3 slime field. */
    static boolean complete(Block center) {
        if (center == null || center.getType() != Material.SLIME_BLOCK) {
            return false;
        }

        World world = center.getWorld();
        if (world == null || !isRedstone(blockAt(world, center.getX(), center.getY() - 1, center.getZ()))) {
            return false;
        }

        int y = center.getY();
        for (int x = center.getX() - FIELD_RADIUS; x <= center.getX() + FIELD_RADIUS; x++) {
            for (int z = center.getZ() - FIELD_RADIUS; z <= center.getZ() + FIELD_RADIUS; z++) {
                Block fieldBlock = blockAt(world, x, y, z);
                if (fieldBlock == null || fieldBlock.getType() != Material.SLIME_BLOCK) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isRedstone(Block block) {
        return block != null && block.getType() == Material.REDSTONE_BLOCK;
    }

    private static Block blockAt(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return world.getBlockAt(x, y, z);
    }
}
