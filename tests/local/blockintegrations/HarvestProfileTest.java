package local.blockintegrations;

import Vystorm.vystorm_core._coreMethods.dataStorage.AtomicFilesInterface;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.region.RegionService;
import Vystorm.vystorm_core.schedule.TaskGroup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HarvestProfileTest {
    @TempDir Path directory;

    @Test void profilesPartitionSupportedPlantsAndNeverAcceptDecoration() {
        for (Material material : Material.values()) {
            int accepted = 0;
            for (HarvestProfile profile : new HarvestProfile[] {HarvestProfile.REPLANTED, HarvestProfile.FRUIT, HarvestProfile.STALKS})
                if (profile.accepts(material)) accepted++;
            assertEquals(HarvestProfile.ALL.accepts(material) ? 1 : 0, accepted, material.name());
            assertFalse(HarvestProfile.PAUSED.accepts(material));
        }
        for (Material crop : new Material[] {Material.WHEAT, Material.CARROTS, Material.POTATOES,
                Material.BEETROOTS, Material.NETHER_WART, Material.COCOA}) assertTrue(HarvestProfile.REPLANTED.accepts(crop));
        assertTrue(HarvestProfile.FRUIT.accepts(Material.MELON));
        assertTrue(HarvestProfile.FRUIT.accepts(Material.PUMPKIN));
        assertTrue(HarvestProfile.STALKS.accepts(Material.BAMBOO));
        assertTrue(HarvestProfile.STALKS.accepts(Material.SUGAR_CANE));
        assertFalse(HarvestProfile.ALL.accepts(Material.OAK_LOG));
    }

    @Test void oldOrUnknownProfilesDefaultToAll() {
        assertEquals(HarvestProfile.ALL, HarvestProfile.parse(null));
        assertEquals(HarvestProfile.ALL, HarvestProfile.parse("future-profile"));
        for (HarvestProfile profile : HarvestProfile.values()) assertEquals(profile, HarvestProfile.parse(profile.key));
        assertEquals(HarvestProfile.ALL, combo().harvestProfile());
    }

    @Test void pausedPassDoesNotTouchWorldOrInventory() {
        Block control = mock(Block.class);
        Harvester.Particles particles = mock(Harvester.Particles.class);
        assertEquals(0, Harvester.run(TestCore.api(), control, 10, true, true, HarvestProfile.PAUSED, particles));
        verifyNoInteractions(control, particles);
    }

    @Test void excludedCropsAreNeverReadOrHarvested() {
        Block control = mock(Block.class), neighbour = mock(Block.class), wheat = mock(Block.class);
        World world = mock(World.class);
        when(control.getWorld()).thenReturn(world);
        when(control.getRelative(any(BlockFace.class))).thenReturn(neighbour);
        when(neighbour.getRelative(any(BlockFace.class))).thenReturn(neighbour);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(wheat);
        when(wheat.getType()).thenReturn(Material.WHEAT);
        RegionService regions = mock(RegionService.class);
        when(regions.top(any(), eq("city"))).thenReturn(Optional.empty());
        try (var service = mockStatic(RegionService.class)) {
            service.when(RegionService::get).thenReturn(regions);
            assertEquals(0, Harvester.run(TestCore.api(), control, 1, false, false, HarvestProfile.FRUIT, at -> fail()));
        }
        verify(wheat, never()).getBlockData();
        verify(wheat, never()).getDrops();
        verify(wheat, never()).setType(any(), anyBoolean());
        verify(world, never()).dropItem(any(), any());
    }

    @Test void profilesRoundTripAndOldFilesKeepUpgrades() throws Exception {
        Path file = directory.resolve("combos.json");
        var store = new StateFile<>(file, "BlockIntegrations/Test", ComboManager.CombosState.class,
                ComboManager.STATE_SCHEMA, Map.of(), ComboManager.CombosState::empty, AtomicFilesInterface.DEFAULT);
        for (HarvestProfile profile : HarvestProfile.values()) {
            var record = new ComboManager.ComboRecord("harvester", null, Map.of("range", 3), null, null, profile.key);
            store.save(new ComboManager.CombosState(Map.of("machine", record)));
            assertEquals(record, store.load().combos().get("machine"));
        }
        Files.writeString(file, """
                {"schema":1,"data":{"combos":{"machine":{"type":"harvester","levels":{"range":3}}}}}
                """);
        var old = store.load().combos().get("machine");
        assertEquals(3, old.levels().get("range"));
        assertEquals(HarvestProfile.ALL, HarvestProfile.parse(old.harvestProfile()));
    }

    @Test void actionsRecheckOwnerPermissionDistanceLoadedStateAndMachineIdentity() throws Exception {
        BlockIntegrations plugin = TestCore.plugin();
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.lang()).thenReturn(mock(Lang.class));
        Combo combo = combo();
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("blockintegrations.combos")).thenReturn(true);
        combo.owner(player.getUniqueId());
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block control = mock(Block.class), hay = mock(Block.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(control);
        when(control.getType()).thenReturn(Material.COMPOSTER);
        when(control.getRelative(0, -1, 0)).thenReturn(hay);
        when(control.getRelative(0, -2, 0)).thenReturn(hay);
        when(hay.getType()).thenReturn(Material.HAY_BLOCK);
        try (var groups = mockStatic(TaskGroup.class); var bukkit = mockStatic(Bukkit.class); var links = mockStatic(ChatLinks.class)) {
            groups.when(() -> TaskGroup.of(plugin, "BlockIntegrations/ComboManager")).thenReturn(mock(TaskGroup.class));
            bukkit.when(() -> Bukkit.getWorld(combo.key.worldId())).thenReturn(world);
            links.when(() -> ChatLinks.near(plugin, player, combo.key, "chat.too-far.combo")).thenReturn(true);
            ComboManager manager = new ComboManager(plugin);
            field(manager, "enabled").set(manager, true);
            @SuppressWarnings("unchecked") var machines = (Map<BlockKey, Combo>) field(manager, "combos").get(manager);
            machines.put(combo.key, combo);
            assertTrue(manager.setHarvestProfile(player, combo, HarvestProfile.FRUIT));
            assertEquals(HarvestProfile.FRUIT, combo.harvestProfile());
            combo.owner(UUID.randomUUID());
            assertFalse(manager.setHarvestProfile(player, combo, HarvestProfile.PAUSED));
            when(player.hasPermission(BlockIntegrations.ADMIN_PERMISSION)).thenReturn(true);
            assertTrue(manager.setHarvestProfile(player, combo, HarvestProfile.STALKS));
            when(player.hasPermission("blockintegrations.combos")).thenReturn(false);
            assertFalse(manager.setHarvestProfile(player, combo, HarvestProfile.PAUSED));
            when(player.hasPermission("blockintegrations.combos")).thenReturn(true);
            links.when(() -> ChatLinks.near(plugin, player, combo.key, "chat.too-far.combo")).thenReturn(false);
            assertFalse(manager.setHarvestProfile(player, combo, HarvestProfile.PAUSED));
            links.when(() -> ChatLinks.near(plugin, player, combo.key, "chat.too-far.combo")).thenReturn(true);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
            assertFalse(manager.setHarvestProfile(player, combo, HarvestProfile.PAUSED));
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(hay.getType()).thenReturn(Material.AIR);
            assertFalse(manager.setHarvestProfile(player, combo, HarvestProfile.PAUSED));
            when(hay.getType()).thenReturn(Material.HAY_BLOCK);
            machines.put(combo.key, new Combo(combo.key, ComboType.HARVESTER, Map.of(), 10));
            assertFalse(manager.setHarvestProfile(player, combo, HarvestProfile.PAUSED));
            assertEquals(HarvestProfile.STALKS, combo.harvestProfile());
        }
    }

    private Combo combo() {
        return new Combo(new BlockKey(UUID.randomUUID(), 0, 64, 0), ComboType.HARVESTER, Map.of(), 10);
    }

    private java.lang.reflect.Field field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
