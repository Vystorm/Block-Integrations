package local.blockintegrations;

import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerLootCapacityTest {
    @Test
    void givenAnEmptySlot_whenCheckingCapacity_thenInventoryIsNotFull() {
        assertFalse(TowerLootCapacity.isFull(new ItemStack[]{stack(64, 64), null}));
    }

    @Test
    void givenAnAirItem_whenCheckingCapacity_thenInventoryIsNotFull() {
        ItemStack air = stack(64, 64);
        when(air.getType()).thenReturn(Material.AIR);
        assertFalse(TowerLootCapacity.isFull(new ItemStack[]{stack(64, 64), air}));
    }

    @Test
    void givenRoomInAnExistingStack_whenCheckingCapacity_thenInventoryIsNotFull() {
        assertFalse(TowerLootCapacity.isFull(new ItemStack[]{stack(63, 64)}));
    }

    @Test
    void givenEverySlotAtItsStackLimit_whenCheckingCapacity_thenInventoryIsFull() {
        assertTrue(TowerLootCapacity.isFull(new ItemStack[]{stack(64, 64), stack(1, 1)}));
    }

    @Test
    void givenNoSlots_whenCheckingCapacity_thenInventoryIsNotConsideredFull() {
        assertFalse(TowerLootCapacity.isFull(new ItemStack[0]));
    }

    private ItemStack stack(int amount, int maxStackSize) {
        ItemStack item = mock(ItemStack.class);
        when(item.getAmount()).thenReturn(amount);
        when(item.getMaxStackSize()).thenReturn(maxStackSize);
        when(item.getType()).thenReturn(Material.STONE);
        return item;
    }
}
