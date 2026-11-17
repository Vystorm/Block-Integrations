package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class HarvesterTest {
    @Test void composterOnHayIsAHarvester() {
        assertTrue(ComboType.HARVESTER.matches(Material.COMPOSTER, Material.HAY_BLOCK, Material.AIR));
        assertFalse(ComboType.HARVESTER.matches(Material.COMPOSTER, Material.DIRT, Material.AIR));
        assertEquals(ComboType.HARVESTER, ComboType.byConfigKey("harvester"));
        assertTrue(ComboType.HARVESTER.supports(ComboUpgrade.RANGE));
        assertTrue(ComboType.HARVESTER.supports(ComboUpgrade.COOLDOWN));
        assertFalse(ComboType.HARVESTER.supports(ComboUpgrade.POWER));
    }

    @Test void everyReplantedCropHasItsSeed() {
        assertEquals(Material.WHEAT_SEEDS, Harvester.seed(Material.WHEAT));
        assertEquals(Material.CARROT, Harvester.seed(Material.CARROTS));
        assertEquals(Material.POTATO, Harvester.seed(Material.POTATOES));
        assertEquals(Material.BEETROOT_SEEDS, Harvester.seed(Material.BEETROOTS));
        assertEquals(Material.NETHER_WART, Harvester.seed(Material.NETHER_WART));
        assertEquals(Material.COCOA_BEANS, Harvester.seed(Material.COCOA));
        assertNull(Harvester.seed(Material.MELON));
        assertNull(Harvester.seed(Material.OAK_SAPLING));
    }

    @Test void replantingTakesExactlyOneSeedFromTheHarvest() {
        ItemStack wheat = stack(Material.WHEAT, 1), seeds = stack(Material.WHEAT_SEEDS, 2);
        List<ItemStack> drops = new ArrayList<>(List.of(wheat, seeds));
        assertTrue(Harvester.takeSeed(drops, Material.WHEAT_SEEDS));
        assertEquals(1, seeds.getAmount());
        assertTrue(Harvester.takeSeed(drops, Material.WHEAT_SEEDS));
        assertFalse(Harvester.takeSeed(drops, Material.WHEAT_SEEDS));
        assertEquals(1, wheat.getAmount());
    }

    @Test void onlyAContainerWithoutAnEmptySlotCountsAsFull() {
        ItemStack partial = stack(Material.WHEAT, 10), full = stack(Material.WHEAT, 64), air = stack(Material.AIR, 1);
        assertTrue(Harvester.noFreeSlot(new ItemStack[] {full, partial}));
        assertFalse(Harvester.noFreeSlot(new ItemStack[] {full, null}));
        assertFalse(Harvester.noFreeSlot(new ItemStack[] {full, air}));
        assertFalse(Harvester.noFreeSlot(new ItemStack[] {full, stack(Material.WHEAT, 0)}));
        assertFalse(Harvester.noFreeSlot(new ItemStack[0]));
        assertFalse(Harvester.noFreeSlot(null));
    }

    /** Item stacks need a server; a mock with a real amount field is enough here. */
    private static ItemStack stack(Material type, int amount) {
        ItemStack stack = org.mockito.Mockito.mock(ItemStack.class);
        int[] count = {amount};
        org.mockito.Mockito.when(stack.getType()).thenReturn(type);
        org.mockito.Mockito.when(stack.getAmount()).thenAnswer(call -> count[0]);
        org.mockito.Mockito.doAnswer(call -> { count[0] = call.getArgument(0); return null; }).when(stack).setAmount(org.mockito.ArgumentMatchers.anyInt());
        return stack;
    }
}
