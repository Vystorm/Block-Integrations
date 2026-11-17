package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.lang.Lang;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class MachineDiagnosticsTest {
    @Test
    void diagnosticMessagesAreLocalizedAndRenderExactFindingValues() throws Exception {
        Lang lang = Lang.create("BlockIntegrations", Map.of(
                "en", Files.readString(Path.of("lang/en.yml")),
                "de", Files.readString(Path.of("lang/de.yml"))));
        assertEquals("lower base", lang.plain("en", "machine-diagnostic.part.lower-base"));
        assertEquals("untere Basis", lang.plain("de", "machine-diagnostic.part.lower-base"));
        assertEquals("• pedestal: expected Crafting Table, found Air at 3, 62, -1.",
                lang.plain("en", "machine-diagnostic.finding", "part", "pedestal", "expected", "Crafting Table",
                        "actual", "Air", "x", 3, "y", 62, "z", -1));
    }

    @Test
    void comboReportUsesCurrentMatcherForAcceptedMaterialsAndShowsWrongBlockCoordinates() {
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block control = block(world, 7, 64, -3, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        Block base = block(world, 7, 63, -3, Material.STONE);
        when(control.getRelative(0, -1, 0)).thenReturn(base);

        MachineDiagnostics.Report report = MachineDiagnostics.combo(ComboType.SORTER, control);

        assertEquals(MachineDiagnostics.State.INCOMPLETE, report.state());
        assertEquals(1, report.findings().size());
        MachineDiagnostics.Finding finding = report.findings().getFirst();
        assertEquals("base", finding.part());
        assertEquals(Material.COPPER_BLOCK, finding.expected());
        assertEquals(Material.STONE, finding.actual());
        assertEquals(7, finding.x());
        assertEquals(63, finding.y());
        assertEquals(-3, finding.z());
        assertFalse(MachineDiagnostics.hasComboEvidence(ComboType.SORTER, control),
                "a common pressure plate with no valid base is not enough to identify a machine");
        verify(control, never()).setType(any(Material.class));
        verify(base, never()).setType(any(Material.class));
    }

    @Test
    void comboMatcherAcceptsCopperVariantsInsteadOfReportingCanonicalExampleAsMandatory() {
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block control = block(world, 0, 64, 0, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        Block base = block(world, 0, 63, 0, Material.WAXED_OXIDIZED_COPPER);
        when(control.getRelative(0, -1, 0)).thenReturn(base);

        MachineDiagnostics.Report report = MachineDiagnostics.combo(ComboType.SORTER, control);

        assertEquals(MachineDiagnostics.State.COMPLETE, report.state());
        assertTrue(report.findings().isEmpty());
    }

    @Test
    void towerReportNamesMissingRingAndNeverReadsUnloadedNeighborChunk() {
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block core = block(world, 15, 64, 0, Material.ZOMBIE_HEAD);
        put(world, core, 0, -1, 0, Material.CRAFTING_TABLE);
        put(world, core, 0, -2, 0, Material.COBBLESTONE);
        put(world, core, -1, -2, 0, Material.OAK_PLANKS);
        put(world, core, 0, -2, 1, Material.OAK_PLANKS);
        put(world, core, 0, -2, -1, Material.OAK_PLANKS);
        Block missing = block(world, 16, 62, 0, Material.AIR);
        when(core.getRelative(1, -2, 0)).thenReturn(missing);

        MachineDiagnostics.Report report = MachineDiagnostics.tower(TowerType.ZOMBIE_STARTER, core);
        assertEquals(MachineDiagnostics.State.INCOMPLETE, report.state());
        assertEquals(1, report.findings().size());
        MachineDiagnostics.Finding finding = report.findings().getFirst();
        assertEquals("ring", finding.part());
        assertEquals(Material.OAK_PLANKS, finding.expected());
        assertEquals(Material.AIR, finding.actual());
        assertEquals(16, finding.x());
        verify(core, never()).setType(any(Material.class));

        when(world.isChunkLoaded(1, 0)).thenReturn(false);
        clearInvocations(core);
        MachineDiagnostics.Report unloaded = MachineDiagnostics.tower(TowerType.ZOMBIE_STARTER, core);
        assertEquals(MachineDiagnostics.State.UNLOADED, unloaded.state());
        assertTrue(unloaded.findings().isEmpty());
        verify(core, never()).getRelative(anyInt(), anyInt(), anyInt());
    }

    @Test
    void bareDecorativeHeadDoesNotCountAsEnoughEvidenceToGuessATower() {
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block core = block(world, 4, 64, 4, Material.ZOMBIE_HEAD);
        put(world, core, 0, -1, 0, Material.AIR);
        put(world, core, 0, -2, 0, Material.AIR);
        put(world, core, 1, -2, 0, Material.AIR);
        put(world, core, -1, -2, 0, Material.AIR);
        put(world, core, 0, -2, 1, Material.AIR);
        put(world, core, 0, -2, -1, Material.AIR);

        assertFalse(MachineDiagnostics.hasTowerEvidence(TowerType.ZOMBIE_STARTER, core));
        verify(core, never()).setType(any(Material.class));
    }

    private static Block block(World world, int x, int y, int z, Material material) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getType()).thenReturn(material);
        return block;
    }

    private static void put(World world, Block core,
            int dx, int dy, int dz, Material material) {
        Block block = block(world, core.getX() + dx, core.getY() + dy, core.getZ() + dz, material);
        when(core.getRelative(dx, dy, dz)).thenReturn(block);
    }
}
