package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class SortItemLocksTest {
    private final NamespacedKey key = new NamespacedKey("blockintegrations", "sort-locked-items");
    private final SortItemLocks locks = new SortItemLocks(key);

    @Test
    void missingAndMalformedDataAreEmpty() {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        assertEquals(Set.of(), locks.read(data));
        when(data.get(key, PersistentDataType.STRING)).thenThrow(new IllegalArgumentException());
        assertEquals(Set.of(), locks.read(data));
    }

    @Test
    void unknownMaterialsAndDuplicatesDoNotLoseValidLocks() {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.get(key, PersistentDataType.STRING)).thenReturn("TORCH,REMOVED_ITEM,AIR,TORCH,BREAD");
        assertEquals(Set.of(Material.TORCH, Material.BREAD), locks.read(data));
    }

    @Test
    void togglesPersistAcrossInstancesAndRemoveLastEntry() {
        String[] stored = {null};
        PersistentDataContainer data = mutableData(stored);
        assertTrue(locks.toggle(data, Material.TORCH));
        assertTrue(locks.toggle(data, Material.BREAD));
        SortItemLocks reloaded = new SortItemLocks(key);
        assertEquals(Set.of(Material.TORCH, Material.BREAD), reloaded.read(data));
        assertFalse(reloaded.toggle(data, Material.TORCH));
        assertEquals(Set.of(Material.BREAD), locks.read(data));
        assertFalse(locks.toggle(data, Material.BREAD));
        assertNull(stored[0]);
    }

    @Test
    void clearOnlyRemovesItsOwnKeyAndEmptyHandCannotBeLocked() {
        String[] stored = {"TORCH"};
        PersistentDataContainer data = mutableData(stored);
        assertThrows(IllegalArgumentException.class, () -> locks.toggle(data, Material.AIR));
        assertEquals("TORCH", stored[0]);
        locks.clear(data);
        assertEquals(Set.of(), locks.read(data));
        verify(data).remove(key);
        verify(data, never()).remove(new NamespacedKey("blockintegrations", "sort-locked-slots"));
    }

    @Test
    void sorterExcludesEveryStackOfLockedTypeWithoutInspectingMetadata() {
        ItemStack torch = item(Material.TORCH, 64);
        ItemStack singleTorch = item(Material.TORCH, 1);
        ItemStack bread = item(Material.BREAD, 16);
        Set<Material> excluded = Set.of(Material.TORCH);
        assertFalse(SortItemLocks.canSort(torch, excluded));
        assertFalse(SortItemLocks.canSort(singleTorch, excluded));
        assertTrue(SortItemLocks.canSort(bread, excluded));
        assertTrue(SortItemLocks.canSort(torch, Set.of()));
        verify(torch, never()).getItemMeta();
    }

    @Test
    void sorterPreflightCombinesSlotAndItemLocksAndNeverUsesHotbar() throws Exception {
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        ItemStack bread = item(Material.BREAD, 16);
        ItemStack torch = item(Material.TORCH, 64);
        when(inventory.getItem(0)).thenReturn(bread);
        when(inventory.getItem(9)).thenReturn(torch);
        when(inventory.getItem(35)).thenReturn(bread);
        var plugin = mock(BlockIntegrations.class);
        var preflight = BlockIntegrations.class.getDeclaredMethod("hasItemsToSort",
                org.bukkit.inventory.PlayerInventory.class, Set.class, Set.class);
        preflight.setAccessible(true);
        assertEquals(false, preflight.invoke(plugin, inventory, Set.of(35), Set.of(Material.TORCH)));
        assertEquals(true, preflight.invoke(plugin, inventory, Set.of(), Set.of(Material.TORCH)));
        assertEquals(true, preflight.invoke(plugin, inventory, Set.of(35), Set.of()));
        verify(inventory, never()).getItem(0);
    }

    @Test
    void sorterIgnoresEmptyStacks() {
        assertFalse(SortItemLocks.canSort(null, Set.of()));
        assertFalse(SortItemLocks.canSort(item(Material.AIR, 1), Set.of()));
        assertFalse(SortItemLocks.canSort(item(Material.BREAD, 0), Set.of()));
    }

    private ItemStack item(Material material, int count) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(count);
        return item;
    }

    private PersistentDataContainer mutableData(String[] stored) {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.get(key, PersistentDataType.STRING)).thenAnswer(i -> stored[0]);
        doAnswer(i -> { stored[0] = i.getArgument(2); return null; })
                .when(data).set(eq(key), eq(PersistentDataType.STRING), anyString());
        doAnswer(i -> { stored[0] = null; return null; }).when(data).remove(key);
        return data;
    }
}
