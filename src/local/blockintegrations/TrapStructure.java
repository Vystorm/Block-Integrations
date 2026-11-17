package local.blockintegrations;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.util.Vector;

/** Eight obsidian blocks around one actuator; the button sits on the front of the frame. */
final class TrapStructure {
    static final List<BlockFace> FACES = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);
    enum Type { THROW, SLING, SPIKES, TAR }
    record Shape(Block core, BlockFace facing, Type type, List<Block> frame, Block button) {}

    static Type type(Material material) {
        return switch (material) {
            case PISTON, STICKY_PISTON -> Type.THROW;
            case SLIME_BLOCK -> Type.SLING;
            case IRON_BLOCK -> Type.SPIKES;
            case HONEY_BLOCK -> Type.TAR;
            default -> null;
        };
    }

    static Vector side(BlockFace facing) {
        return facing.getModY() == 0 ? new Vector(-facing.getModZ(), 0, facing.getModX()) : new Vector(1, 0, 0);
    }

    static Vector vertical(BlockFace facing) { return facing.getDirection().crossProduct(side(facing)); }

    static Shape detect(Block core, BlockFace facing) {
        Type type = type(core.getType());
        if (type == null || !FACES.contains(facing)) return null;
        if (type == Type.THROW && (!(core.getBlockData() instanceof org.bukkit.block.data.type.Piston piston)
                || piston.isExtended() || piston.getFacing() != facing)) return null;
        Vector u = side(facing), v = vertical(facing);
        List<Block> frame = new ArrayList<>();
        Block button = null;
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            Block ring = loaded(core, a * u.getBlockX() + b * v.getBlockX(), a * u.getBlockY() + b * v.getBlockY(), a * u.getBlockZ() + b * v.getBlockZ());
            if (ring == null || ring.getType() != Material.OBSIDIAN) return null;
            frame.add(ring);
            Block candidate = loaded(ring, facing.getModX(), facing.getModY(), facing.getModZ());
            if (candidate != null && isButton(candidate) && outward(candidate) == facing) {
                if (type == Type.SLING && button != null) return null;
                button = candidate;
            }
        }
        return button == null ? null : new Shape(core, facing, type, List.copyOf(frame), button);
    }

    static BlockFace outward(Block button) {
        if (!(button.getBlockData() instanceof FaceAttachable attach) || !(button.getBlockData() instanceof Directional direction)) return null;
        return switch (attach.getAttachedFace()) {
            case FLOOR -> BlockFace.UP;
            case CEILING -> BlockFace.DOWN;
            case WALL -> direction.getFacing();
        };
    }

    static boolean isButton(Block block) { return block.getType().name().endsWith("_BUTTON"); }

    static Shape fromButton(Block button) {
        BlockFace face = outward(button);
        if (face == null || !isButton(button)) return null;
        Block support = loaded(button, -face.getModX(), -face.getModY(), -face.getModZ());
        if (support == null) return null;
        Vector u = side(face), v = vertical(face);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            Block center = loaded(support, a * u.getBlockX() + b * v.getBlockX(), a * u.getBlockY() + b * v.getBlockY(), a * u.getBlockZ() + b * v.getBlockZ());
            if (center == null) continue;
            Shape shape = detect(center, face);
            if (shape != null && shape.frame().contains(support)) return shape;
        }
        return null;
    }

    static Block loaded(Block origin, int dx, int dy, int dz) {
        int x = origin.getX() + dx, y = origin.getY() + dy, z = origin.getZ() + dz;
        var world = origin.getWorld();
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) return null;
        return world.getBlockAt(x, y, z);
    }

    /** Three blocks wide/high, extending from the front face into the room. */
    static boolean inFront(Vector relative, BlockFace face, double depth) {
        double forward = relative.dot(face.getDirection());
        return forward >= .45 && forward <= depth + .5
                && Math.abs(relative.dot(side(face))) <= 1.5 && Math.abs(relative.dot(vertical(face))) <= 1.5;
    }

    static Vector launch(BlockFace face, double power) {
        Vector velocity = face.getDirection().multiply(power);
        if (face.getModY() == 0) velocity.setY(.65);
        return velocity;
    }

    /** Floor/ceiling: away from the single frame button; wall: out into the room. */
    static Vector sling(BlockFace face, Vector buttonOffset, double power, double lift) {
        Vector direction = face.getModY() == 0 ? face.getDirection()
                : new Vector(-buttonOffset.getX(), 0, -buttonOffset.getZ());
        if (direction.lengthSquared() == 0) throw new IllegalArgumentException("Sling button must be on the frame");
        return direction.normalize().multiply(power).setY(face == BlockFace.DOWN ? -lift : lift);
    }
}
