package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class StructurePortabilityTest {
    @Test
    void incompleteButStillNativeControlsRetainUpgradesUntilTheirMainBlockIsGone() throws Exception {
        BlockIntegrations plugin = plugin();
        ComboManager combos = new ComboManager(plugin);
        field(ComboManager.class, "maximumLevel").set(combos, 10);
        BlockKey comboKey = new BlockKey(UUID.randomUUID(), 4, 70, 9);
        Combo upgraded = new Combo(comboKey, ComboType.LAUNCHER,
                Map.of(ComboUpgrade.POWER, 6), 10);
        map(combos, "combos").put(comboKey, upgraded);
        Block incompleteCombo = block(comboKey, Material.SLIME_BLOCK);

        assertNull(invoke(combos, "recognize", incompleteCombo));
        assertEquals(upgraded, map(combos, "combos").get(comboKey));
        World comboWorld = mock(World.class);
        when(comboWorld.isChunkLoaded(comboKey.chunkX(), comboKey.chunkZ())).thenReturn(true);
        when(comboWorld.getBlockAt(comboKey.x(), comboKey.y(), comboKey.z())).thenReturn(incompleteCombo);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(comboKey.worldId())).thenReturn(comboWorld);
            invoke(combos, "validateLoadedCombos");
        }
        assertEquals(upgraded, map(combos, "combos").get(comboKey));

        TowerManager towers = new TowerManager(plugin);
        field(TowerManager.class, "maximumUpgradeLevel").set(towers, 10);
        field(TowerManager.class, "attackTaskPeriod").set(towers, 2L);
        BlockKey towerKey = new BlockKey(UUID.randomUUID(), 8, 70, 12);
        Tower upgradedTower = new Tower(towerKey, TowerType.ARCHER, 5, 3, 2, 1, 10, 0,
                new ItemStack[0]);
        map(towers, "towers").put(towerKey, upgradedTower);
        World towerWorld = mock(World.class);
        Block incompleteTower = block(towerKey, Material.SKELETON_SKULL);
        when(towerWorld.isChunkLoaded(towerKey.chunkX(), towerKey.chunkZ())).thenReturn(true);
        when(towerWorld.getBlockAt(towerKey.x(), towerKey.y(), towerKey.z())).thenReturn(incompleteTower);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(towerKey.worldId())).thenReturn(towerWorld);
            invoke(towers, "validateLoadedTowers");
            assertEquals(upgradedTower, map(towers, "towers").get(towerKey));
            invoke(towers, "attackTick");
        }
        verify(towerWorld, never()).getNearbyEntities(any(), anyDouble(), anyDouble(), anyDouble());

        when(incompleteTower.getType()).thenReturn(Material.AIR);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(towerKey.worldId())).thenReturn(towerWorld);
            invoke(towers, "validateLoadedTowers");
        }
        assertFalse(map(towers, "towers").containsKey(towerKey));

        when(incompleteCombo.getType()).thenReturn(Material.AIR);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(comboKey.worldId())).thenReturn(comboWorld);
            invoke(combos, "validateLoadedCombos");
        }
        assertFalse(map(combos, "combos").containsKey(comboKey));
    }

    @Test
    void nativeDropsDecorateExactlyOneMatchingItemAndForgetTheOldRecords() throws Exception {
        BlockIntegrations plugin = plugin();
        PortableBlockData portable = mock(PortableBlockData.class);
        when(plugin.portableBlocks()).thenReturn(portable);

        ComboManager combos = new ComboManager(plugin);
        field(ComboManager.class, "enabled").set(combos, true);
        BlockKey comboKey = new BlockKey(UUID.randomUUID(), 1, 64, 1);
        map(combos, "combos").put(comboKey, new Combo(comboKey, ComboType.LAUNCHER,
                Map.of(ComboUpgrade.POWER, 4), 10));
        BlockDropItemEvent comboEvent = drop(comboKey, Material.SLIME_BLOCK);
        Item comboFirst = mock(Item.class), comboSecond = mock(Item.class);
        ItemStack wrong = stack(Material.DIRT), nativeDrop = stack(Material.SLIME_BLOCK), secondNative = stack(Material.SLIME_BLOCK);
        ItemStack decoratedCombo = stack(Material.SLIME_BLOCK);
        when(comboFirst.getItemStack()).thenReturn(wrong);
        when(comboSecond.getItemStack()).thenReturn(nativeDrop);
        Item extraComboNative = item(secondNative);
        when(comboEvent.getItems()).thenReturn(java.util.List.of(comboFirst, comboSecond, extraComboNative));
        when(portable.write(eq(nativeDrop), any(), eq("combo"), eq("launcher"), any(Map.class), eq(null), eq(0.0D)))
                .thenReturn(decoratedCombo);
        combos.onBlockDrop(comboEvent);
        verify(comboSecond).setItemStack(decoratedCombo);
        verify(comboFirst, never()).setItemStack(any());
        verify(extraComboNative, never()).setItemStack(any());
        assertFalse(map(combos, "combos").containsKey(comboKey));

        TowerManager towers = new TowerManager(plugin);
        field(TowerManager.class, "enabled").set(towers, true);
        BlockKey towerKey = new BlockKey(UUID.randomUUID(), 2, 64, 2);
        map(towers, "towers").put(towerKey, new Tower(towerKey, TowerType.ARCHER,
                3, 2, 1, 1, 10, 17, new ItemStack[0]));
        BlockDropItemEvent towerEvent = drop(towerKey, Material.SKELETON_SKULL);
        Item towerNative = mock(Item.class);
        ItemStack towerDrop = stack(Material.SKELETON_SKULL), decoratedTower = stack(Material.SKELETON_SKULL);
        when(towerNative.getItemStack()).thenReturn(towerDrop);
        Item extraTowerNative = item(stack(Material.SKELETON_SKULL));
        when(towerEvent.getItems()).thenReturn(java.util.List.of(towerNative, extraTowerNative));
        when(portable.write(eq(towerDrop), any(), eq("tower"), eq("archer"), any(Map.class), eq(null), eq(0.0D)))
                .thenReturn(decoratedTower);
        towers.onBlockDrop(towerEvent);
        verify(towerNative).setItemStack(decoratedTower);
        verify(extraTowerNative, never()).setItemStack(any());
        assertFalse(map(towers, "towers").containsKey(towerKey));
        verify(portable, org.mockito.Mockito.times(2)).write(any(), any(), any(), any(), any(), eq(null), eq(0.0D));
    }

    private BlockIntegrations plugin() {
        BlockIntegrations plugin = TestCore.plugin();
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.isEnabled()).thenReturn(false); // Keeps record-removal tests free of delayed save work.
        return plugin;
    }

    private BlockDropItemEvent drop(BlockKey key, Material type) {
        BlockDropItemEvent event = mock(BlockDropItemEvent.class);
        Block block = block(key, type);
        BlockState state = mock(BlockState.class);
        when(state.getType()).thenReturn(type);
        when(event.getBlock()).thenReturn(block);
        when(event.getBlockState()).thenReturn(state);
        return event;
    }

    private Block block(BlockKey key, Material type) {
        Block block = mock(Block.class);
        World world = mock(World.class);
        when(world.getUID()).thenReturn(key.worldId());
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(key.x());
        when(block.getY()).thenReturn(key.y());
        when(block.getZ()).thenReturn(key.z());
        when(block.getType()).thenReturn(type);
        Block missingPart = mock(Block.class);
        when(missingPart.getType()).thenReturn(Material.AIR);
        when(block.getRelative(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(missingPart);
        return block;
    }

    private ItemStack stack(Material type) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(1);
        return stack;
    }

    private Item item(ItemStack stack) {
        Item item = mock(Item.class);
        when(item.getItemStack()).thenReturn(stack);
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<BlockKey, Object> map(Object target, String name) throws Exception {
        return (Map<BlockKey, Object>) field(target.getClass(), name).get(target);
    }

    private Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private Object invoke(Object target, String name, Object... args) throws Exception {
        Method method = java.util.Arrays.stream(target.getClass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)
                        && candidate.getParameterCount() == args.length)
                .findFirst().orElseThrow();
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
