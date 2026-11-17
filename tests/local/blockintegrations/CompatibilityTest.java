package local.blockintegrations;

import Vystorm.vystorm_core.compat.ServerCompat;
import Vystorm.vystorm_core.compat.InventoryCompat;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompatibilityTest {
    @Test void selectsAdapterFromAvailableClasses() {
        assertInstanceOf(PlatformAccess.Legacy.class, PlatformAccess.select(false));
        boolean modern = ServerCompat.hasClass("org.bukkit.inventory.view.AnvilView");
        assertEquals(!modern, PlatformAccess.INSTANCE instanceof PlatformAccess.Legacy);
        assertFalse(ServerCompat.hasClass("org.bukkit.inventory.view.DoesNotExist"));
        if (Boolean.getBoolean("compat1206")) {
            assertFalse(modern);
            assertFalse(ServerCompat.hasDialogs());
        } else {
            assertTrue(modern);
            assertTrue(ServerCompat.hasDialogs());
        }
    }

    @Test void legacyAnvilPreservesRenameCostsAndMaximum() {
        InventoryView view = mock(InventoryView.class);
        AnvilInventory inventory = mock(AnvilInventory.class);
        try (var access = mockStatic(InventoryCompat.class)) {
            access.when(() -> InventoryCompat.top(view)).thenReturn(inventory);
            when(inventory.getRenameText()).thenReturn("Tower tool");
            when(inventory.getRepairCost()).thenReturn(7);
            PlatformAccess legacy = PlatformAccess.select(false);
            assertEquals("Tower tool", legacy.rename(view));
            assertEquals(7, legacy.cost(view));
            legacy.cost(view, 4); legacy.unlock(view);
            verify(inventory).setRepairCost(4);
            verify(inventory).setMaximumRepairCost(Integer.MAX_VALUE);
        }
    }

    @Test void viewAccessWorksAcrossTheClassToInterfaceChange() {
        AnvilInventory inventory = mock(AnvilInventory.class);
        var player = mock(org.bukkit.entity.Player.class);
        InventoryView view = mock(InventoryView.class, invocation -> switch (invocation.getMethod().getName()) {
            case "getTopInventory" -> inventory;
            case "getPlayer" -> player;
            default -> null;
        });
        assertSame(inventory, InventoryCompat.top(view));
        assertSame(player, PlatformAccess.player(view));
        assertDoesNotThrow(() -> PlatformAccess.title(view, "Tower loot"));
    }

    @Test void legacyBrewingScalesOriginalDurationAndLeavesIdleBrewsAlone() {
        BrewingStartEvent event = mock(BrewingStartEvent.class);
        when(event.getTotalBrewTime()).thenReturn(400);
        PlatformAccess.select(false).brew(event, 10);
        verify(event).setTotalBrewTime(182);
        reset(event);
        PlatformAccess.select(false).brew(event, 10);
        verify(event, never()).setTotalBrewTime(anyInt());
        assertEquals(!PlatformAccess.hasMethod(BrewingStartEvent.class, "getBrewingTime"),
                PlatformAccess.brewing() instanceof PlatformAccess.Legacy);
    }

    @Test void mainClassesLinkWithoutModernPaperTypes() throws Exception {
        Path root = Path.of("build/classes/java/main");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = root.relativize(path).toString().replace('\\', '.').replace('/', '.').replaceAll("\\.class$", "");
                Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                type.getDeclaredMethods(); type.getDeclaredConstructors(); type.getDeclaredFields();
            }
        }
    }
}
