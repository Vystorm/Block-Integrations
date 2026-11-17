package local.blockintegrations;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.FaceAttachable.AttachedFace;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.Switch;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TrapStructureTest {
    static final class Fixture {
        final World world = mock(World.class);
        final Map<List<Integer>, Block> blocks = new HashMap<>();
        final Block core;
        Block button;
        Fixture(BlockFace face, Material material) {
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            core = block(0, 64, 0); when(core.getType()).thenReturn(material);
            Piston piston = mock(Piston.class); when(piston.getFacing()).thenReturn(face); when(core.getBlockData()).thenReturn(piston);
            Vector u = TrapStructure.side(face), v = TrapStructure.vertical(face);
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                var offset = u.clone().multiply(a).add(v.clone().multiply(b));
                when(block(offset.getBlockX(), 64 + offset.getBlockY(), offset.getBlockZ()).getType()).thenReturn(Material.OBSIDIAN);
            }
            Vector offset = u.clone().add(face.getDirection());
            button = block(offset.getBlockX(), 64 + offset.getBlockY(), offset.getBlockZ());
            when(button.getType()).thenReturn(Material.STONE_BUTTON);
            Switch data = mock(Switch.class);
            when(data.getAttachedFace()).thenReturn(face == BlockFace.UP ? AttachedFace.FLOOR : face == BlockFace.DOWN ? AttachedFace.CEILING : AttachedFace.WALL);
            when(data.getFacing()).thenReturn(face.getModY() == 0 ? face : BlockFace.NORTH);
            when(button.getBlockData()).thenReturn(data);
        }
        Block block(int x, int y, int z) {
            return blocks.computeIfAbsent(List.of(x,y,z), ignored -> {
                Block block = mock(Block.class);
                when(block.getWorld()).thenReturn(world); when(block.getX()).thenReturn(x); when(block.getY()).thenReturn(y); when(block.getZ()).thenReturn(z);
                when(block.getType()).thenReturn(Material.AIR);
                when(block.getLocation()).thenAnswer(c -> new Location(world, x, y, z));
                return block;
            });
        }
    }

    @Test void allFourTrapsWorkInAllSixDirectionsAndResolveFromTheirButton() {
        for (BlockFace face : TrapStructure.FACES) for (Material material : List.of(Material.PISTON, Material.SLIME_BLOCK, Material.IRON_BLOCK, Material.HONEY_BLOCK)) {
            Fixture f = new Fixture(face, material);
            var shape = TrapStructure.detect(f.core, face);
            assertNotNull(shape, face + " " + material);
            assertEquals(8, shape.frame().size());
            assertSame(f.core, TrapStructure.fromButton(f.button).core());
        }
    }

    @Test void missingFrameButtonWrongDirectionAndUnloadedChunkAreRejected() {
        Fixture f = new Fixture(BlockFace.EAST, Material.PISTON);
        assertNull(TrapStructure.detect(f.core, BlockFace.WEST));
        var ring = TrapStructure.detect(f.core, BlockFace.EAST).frame().getFirst();
        when(ring.getType()).thenReturn(Material.CRYING_OBSIDIAN);
        assertNull(TrapStructure.detect(f.core, BlockFace.EAST));
        when(ring.getType()).thenReturn(Material.OBSIDIAN);
        when(f.button.getType()).thenReturn(Material.AIR);
        assertNull(TrapStructure.detect(f.core, BlockFace.EAST));
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        clearInvocations(f.world);
        assertNull(TrapStructure.detect(f.core, BlockFace.EAST));
        verify(f.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test void targetsMustBeInFrontAndThrowDirectionMatchesEachMount() {
        for (BlockFace face : TrapStructure.FACES) {
            assertTrue(TrapStructure.inFront(face.getDirection().multiply(2), face, 3));
            assertFalse(TrapStructure.inFront(face.getDirection().multiply(-1), face, 3));
            assertFalse(TrapStructure.inFront(face.getDirection().multiply(4), face, 3));
            assertFalse(TrapStructure.inFront(face.getDirection().add(TrapStructure.side(face).multiply(2)), face, 3));
            assertTrue(TrapStructure.launch(face, 2).dot(face.getDirection()) >= 2);
        }
    }

    @Test void slingUsesButtonDirectionOnFloorAndCeilingAndNormalOnWalls() {
        assertEquals(new Vector(-2, 1.1, 0), TrapStructure.sling(BlockFace.UP, new Vector(1, 1, 0), 2, 1.1));
        assertEquals(new Vector(0, -1.1, 2), TrapStructure.sling(BlockFace.DOWN, new Vector(0, -1, -1), 2, 1.1));
        assertEquals(new Vector(2, 1.1, 0), TrapStructure.sling(BlockFace.EAST, new Vector(1, 1, 0), 2, 1.1));
        var diagonal = TrapStructure.sling(BlockFace.UP, new Vector(1, 1, 1), 2, 1.1);
        assertEquals(4, diagonal.getX() * diagonal.getX() + diagonal.getZ() * diagonal.getZ(), .00001);
        assertEquals(1.1, diagonal.getY());
    }

    @Test void slingRejectsAmbiguousMultipleButtons() {
        Fixture f = new Fixture(BlockFace.UP, Material.SLIME_BLOCK);
        Block second = f.block(-1, 65, 0);
        when(second.getType()).thenReturn(Material.STONE_BUTTON);
        var buttonData = f.button.getBlockData();
        when(second.getBlockData()).thenReturn(buttonData);
        assertNull(TrapStructure.detect(f.core, BlockFace.UP));
    }
}
