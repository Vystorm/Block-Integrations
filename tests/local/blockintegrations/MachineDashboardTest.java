package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class MachineDashboardTest {
    @Test void probesActualFurnaceProgressAndBottlenecksWithoutChangingInventory() {
        Furnace furnace = mock(Furnace.class); FurnaceInventory inventory = mock(FurnaceInventory.class);
        when(furnace.getInventory()).thenReturn(inventory); when(inventory.getMaxStackSize()).thenReturn(64);
        assertEquals("idle", MachineDashboard.furnaceState(furnace));
        ItemStack input = mock(ItemStack.class), output = mock(ItemStack.class);
        when(input.getType()).thenReturn(Material.RAW_IRON); when(input.getAmount()).thenReturn(1);
        when(inventory.getSmelting()).thenReturn(input);
        assertEquals("fuel", MachineDashboard.furnaceState(furnace));
        when(furnace.getBurnTime()).thenReturn((short) 80); when(furnace.getCookTime()).thenReturn((short) 30);
        assertEquals("running", MachineDashboard.furnaceState(furnace));
        when(output.getType()).thenReturn(Material.IRON_INGOT); when(output.getAmount()).thenReturn(64); when(output.getMaxStackSize()).thenReturn(64);
        when(inventory.getResult()).thenReturn(output);
        assertEquals("output", MachineDashboard.furnaceState(furnace));
        verify(inventory, never()).setResult(any()); verify(furnace, never()).update();
    }
    @Test void watchListIsBoundedAndAlertsAreOptInTransitionsWithCooldown() {
        String id = "furnace:" + UUID.randomUUID() + ",1,2,3";
        assertEquals(java.util.Set.of(id), MachineDashboard.decode(id + "\n" + id + "\nbad"));
        assertTrue(MachineDashboard.decode("x".repeat(5000)).isEmpty());
        assertFalse(MachineDashboard.shouldNotify(null, "fuel", 1_000_000));
        assertFalse(MachineDashboard.shouldNotify(new MachineDashboard.Observation("fuel", 0), "fuel", 1_000_000));
        assertTrue(MachineDashboard.shouldNotify(new MachineDashboard.Observation("ready", 0), "fuel", 1_000_000));
        assertFalse(MachineDashboard.shouldNotify(new MachineDashboard.Observation("ready", 999_000), "fuel", 1_000_000));
    }
}
