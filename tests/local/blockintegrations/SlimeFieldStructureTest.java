package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class SlimeFieldStructureTest {
    @Test
    void everyFieldBlockResolvesToTheSameCenter() {
        Field field = new Field(8, 70, 8, true);

        for (int x = 7; x <= 9; x++) {
            for (int z = 7; z <= 9; z++) {
                assertSame(field.block(8, 70, 8), SlimeFieldStructure.controlFor(field.block(x, 70, z)));
            }
        }
        assertTrue(SlimeFieldStructure.complete(field.block(8, 70, 8)));
    }

    @Test
    void fieldMustBeHorizontalRatherThanVertical() {
        Field field = new Field(8, 70, 8, true);
        field.put(9, 70, 8, Material.AIR);
        field.put(8, 71, 8, Material.SLIME_BLOCK);

        assertFalse(SlimeFieldStructure.complete(field.block(8, 70, 8)));
        assertNull(SlimeFieldStructure.controlFor(field.block(8, 71, 8)));
    }

    @Test
    void outerBlockOfAnIncompleteFieldDoesNotResolve() {
        Field field = new Field(8, 70, 8, true);
        field.put(9, 70, 9, Material.AIR);

        assertNull(SlimeFieldStructure.controlFor(field.block(7, 70, 7)));
    }

    @Test
    void standaloneLauncherIsRetained() {
        Field field = new Field(8, 70, 8, false);
        field.put(8, 70, 8, Material.SLIME_BLOCK);
        field.put(8, 69, 8, Material.REDSTONE_BLOCK);

        assertSame(field.block(8, 70, 8), SlimeFieldStructure.controlFor(field.block(8, 70, 8)));
    }

    @Test
    void existingLauncherInTheFieldKeepsItsOwnUpgradesAnchor() {
        Field field = new Field(8, 70, 8, true);
        field.put(7, 69, 7, Material.REDSTONE_BLOCK);
        assertSame(field.block(7, 70, 7), SlimeFieldStructure.controlFor(field.block(7, 70, 7)));
    }

    @Test
    void fieldWithoutRedstoneHasNoAnchor() {
        Field field = new Field(8, 70, 8, false);

        assertNull(SlimeFieldStructure.controlFor(field.block(8, 70, 8)));
        assertFalse(SlimeFieldStructure.complete(field.block(8, 70, 8)));
    }

    @Test
    void unloadedNeighborIsNeverRead() {
        Field field = new Field(15, 70, 8, true);
        field.unload(16, 8);

        assertNull(SlimeFieldStructure.controlFor(field.block(14, 70, 8)));
        verify(field.world, never()).getBlockAt(16, 70, 7);
    }

    private static final class Field {
        private final World world = mock(World.class);
        private final Map<Coordinate, Block> blocks = new HashMap<>();
        private final Map<Coordinate, Material> materials = new HashMap<>();
        private final Map<Coordinate, Boolean> loaded = new HashMap<>();

        Field(int centerX, int y, int centerZ, boolean redstone) {
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                    put(x, y, z, Material.SLIME_BLOCK);
                }
            }
            if (redstone) {
                put(centerX, y - 1, centerZ, Material.REDSTONE_BLOCK);
            }
            when(world.isChunkLoaded(org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> loaded.getOrDefault(
                            new Coordinate(invocation.<Integer>getArgument(0), 0,
                                    invocation.<Integer>getArgument(1)), true));
            when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> block(
                            invocation.<Integer>getArgument(0), invocation.<Integer>getArgument(1),
                            invocation.<Integer>getArgument(2)));
        }

        void put(int x, int y, int z, Material material) {
            materials.put(new Coordinate(x, y, z), material);
        }

        void unload(int x, int z) {
            loaded.put(new Coordinate(x >> 4, 0, z >> 4), false);
        }

        Block block(int x, int y, int z) {
            Coordinate coordinate = new Coordinate(x, y, z);
            return blocks.computeIfAbsent(coordinate, ignored -> {
                Block block = mock(Block.class);
                when(block.getWorld()).thenReturn(world);
                when(block.getX()).thenReturn(x);
                when(block.getY()).thenReturn(y);
                when(block.getZ()).thenReturn(z);
                when(block.getType()).thenAnswer(invocation -> materials.getOrDefault(coordinate, Material.AIR));
                return block;
            });
        }
    }

    private record Coordinate(int x, int y, int z) {
    }
}
