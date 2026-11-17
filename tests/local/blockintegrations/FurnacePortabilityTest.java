package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class FurnacePortabilityTest {
    @Test
    void dropDecoratesOneMatchingNativeItemWithoutReadingRecipeExperience() throws Exception {
        BlockIntegrations plugin = TestCore.plugin();
        PortableBlockData portable = mock(PortableBlockData.class);
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.namespace()).thenReturn("blockintegrations");
        when(plugin.portableBlocks()).thenReturn(portable);
        FurnaceManager manager = manager(plugin);
        Furnace furnace = furnace(Material.BLAST_FURNACE, new HashMap<>());
        BlockDropItemEvent event = mock(BlockDropItemEvent.class);
        Item matching = mock(Item.class), other = mock(Item.class);
        ItemStack nativeFurnace = mock(ItemStack.class), nonNative = mock(ItemStack.class);
        ItemStack decorated = mock(ItemStack.class);
        when(event.getBlockState()).thenReturn(furnace);
        when(event.getItems()).thenReturn(java.util.List.of(other, matching));
        when(other.getItemStack()).thenReturn(nonNative);
        when(nonNative.getType()).thenReturn(Material.IRON_BLOCK);
        when(matching.getItemStack()).thenReturn(nativeFurnace);
        when(nativeFurnace.getType()).thenReturn(Material.BLAST_FURNACE);
        when(nativeFurnace.getAmount()).thenReturn(1);
        when(portable.write(eq(nativeFurnace), any(), eq("furnace"), eq("BLAST_FURNACE"),
                any(Map.class), any(), eq(0.0D))).thenReturn(decorated);

        manager.onFurnaceDrop(event);

        verify(portable).write(eq(nativeFurnace), any(), eq("furnace"), eq("BLAST_FURNACE"),
                eq(Map.of("speed", 0, "fuel", 0, "yield", 0, "multismelt", 0,
                        "xp-boost", 0)), eq(null), eq(0.0D));
        verify(matching).setItemStack(decorated);
        verify(other, never()).setItemStack(any());
        verify(furnace, never()).getRecipesUsed();
    }

    @Test
    void finalCancellationDoesNotChangeThePlacedFurnace() throws Exception {
        BlockIntegrations plugin = TestCore.plugin();
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.namespace()).thenReturn("blockintegrations");
        FurnaceManager manager = manager(plugin);
        Block block = mock(Block.class);
        Player player = mock(Player.class);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.isCancelled()).thenReturn(true);
        when(event.canBuild()).thenReturn(true);
        when(event.getBlockPlaced()).thenReturn(block);
        when(block.getType()).thenReturn(Material.FURNACE);
        when(event.getPlayer()).thenReturn(player);
        when(plugin.isEnabled()).thenReturn(true);

        // Core's stage dispatcher does not call an OBSERVE handler for a cancelled placement ...
        stages(manager).fire(event);
        verify(event, never()).getBlockPlaced();
        // ... and the handler itself ignores an event that is cancelled all the same
        manager.onFurnacePlace(event);
        verify(block, never()).getState();
    }

    @Test
    void portablePlacementRestoresOwnerLevelsAndRemainderWithoutUsePermission()
            throws Exception {
        BlockIntegrations plugin = TestCore.plugin();
        PortableBlockData portable = mock(PortableBlockData.class);
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.namespace()).thenReturn("blockintegrations");
        when(plugin.portableBlocks()).thenReturn(portable);
        FurnaceManager manager = manager(plugin);
        Map<NamespacedKey, Object> values = new HashMap<>();
        Furnace furnace = furnace(Material.SMOKER, values);
        Block block = mock(Block.class);
        Player player = mock(Player.class);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        ItemStack source = mock(ItemStack.class), sourceCopy = mock(ItemStack.class);
        UUID owner = UUID.randomUUID();
        when(event.canBuild()).thenReturn(true);
        when(event.getBlockPlaced()).thenReturn(block);
        when(block.getType()).thenReturn(Material.SMOKER);
        when(block.getState()).thenReturn(furnace);
        when(event.getPlayer()).thenReturn(player);
        when(player.hasPermission("blockintegrations.furnaces")).thenReturn(false);
        when(event.getItemInHand()).thenReturn(source);
        when(source.clone()).thenReturn(sourceCopy);
        when(portable.read(sourceCopy)).thenReturn(new PortableBlockData.State(Material.SMOKER,
                "furnace", "SMOKER", Map.of("speed", 99, "fuel", 2, "xp-boost", 8),
                owner, 0.75D));
        when(plugin.isEnabled()).thenReturn(true);

        // called the way Vystorm Core's stage dispatcher calls the handler
        stages(manager).fire(event);

        assertEquals(owner.toString(), value(values, "furnace_fixed_owner"));
        assertEquals(owner.toString(), value(values, "owner"));
        assertEquals(10, value(values, "furnace_speed_level"));
        assertEquals(2, value(values, "furnace_fuel_level"));
        assertEquals(5, value(values, "furnace_xp-boost_level"));
        assertEquals(0.75D, value(values, "furnace_experience_remainder"));
        assertFalse(values.containsValue(99));
        assertFalse(values.containsValue(8));
        verify(player, never()).hasPermission("blockintegrations.furnaces");
    }

    private FurnaceManager manager(BlockIntegrations plugin) throws Exception {
        FurnaceManager manager = new FurnaceManager(plugin);
        field(FurnaceManager.class, "enabled").set(manager, true);
        field(FurnaceManager.class, "maximumLevel").set(manager, 10);
        return manager;
    }

    private Furnace furnace(Material type, Map<NamespacedKey, Object> values) {
        Furnace furnace = mock(Furnace.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(furnace.getType()).thenReturn(type);
        when(furnace.getPersistentDataContainer()).thenReturn(data);
        when(furnace.update(false, false)).thenReturn(true);
        when(data.get(any(NamespacedKey.class), any())).thenAnswer(
                invocation -> values.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(data).set(any(NamespacedKey.class), any(), any());
        return furnace;
    }

    private Object value(Map<NamespacedKey, Object> values, String key) {
        return values.entrySet().stream().filter(entry -> entry.getKey().getKey().equals(key))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** The manager's block handlers the way Vystorm Core's stage dispatcher calls them. */
    private static Vystorm.vystorm_core._coreMethods.eventRegulator.EventStageTesting stages(FurnaceManager manager) {
        var stages = Vystorm.vystorm_core._coreMethods.eventRegulator.EventStageTesting.detached();
        manager.handleBlockEvents(stages.stages());
        return stages;
    }
}
