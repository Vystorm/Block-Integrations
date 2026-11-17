package local.blockintegrations;

import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.region.RegionService;
import Vystorm.vystorm_core.schedule.TaskGroup;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TrapManagerTest {
    @TempDir Path directory;

    @Test void automaticAndButtonShareCooldownAndCancelledEffectsDoNotConsumeIt() {
        exercise(Material.PISTON, new Vector(0, 2, 0));
    }

    @Test void slingAppliesItsArcOnceAndRespectsProtectionAndCooldown() {
        exercise(Material.SLIME_BLOCK, new Vector(-2, 1.1, 0));
    }

    private void exercise(Material material, Vector velocity) {
        var f = new TrapStructureTest.Fixture(BlockFace.UP, material);
        var plugin = mock(BlockIntegrations.class, RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.lang()).thenReturn(mock(Lang.class));
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.isEnabled()).thenReturn(true);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(TrapManager.PERMISSION)).thenReturn(true);
        var event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(f.button);
        Zombie mob = mock(Zombie.class);
        when(mob.isValid()).thenReturn(true);
        when(mob.getScoreboardTags()).thenReturn(Set.of());
        when(mob.getLocation()).thenReturn(new Location(f.world, .5, 65, .5));
        when(mob.getBoundingBox()).thenReturn(new BoundingBox(.2, 65, .2, .8, 66.8, .8));
        when(f.world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        RegionService regions = mock(RegionService.class);
        when(regions.at(any())).thenReturn(List.of());
        when(regions.top(any(), eq("city"))).thenReturn(Optional.empty());
        try (var groups = mockStatic(TaskGroup.class); var service = mockStatic(RegionService.class); var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(f.world.getUID())).thenReturn(f.world);
            groups.when(() -> TaskGroup.of(plugin, "BlockIntegrations/Traps")).thenReturn(mock(TaskGroup.class));
            service.when(RegionService::get).thenReturn(regions);
            TrapManager manager = new TrapManager(plugin);
            manager.reloadSettings();
            // the click reaches the trap the way Vystorm Core's stage dispatcher calls it (ACT)
            var stages = Vystorm.vystorm_core._coreMethods.eventRegulator.EventStageTesting.detached();
            manager.handleSharedEvents(stages.stages());
            stages.fire(event); // registers; no target = no cooldown
            when(f.world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(mob));
            var events = plugin.getServer().getPluginManager();
            doAnswer(call -> { ((TrapTriggerEvent) call.getArgument(0)).setCancelled(true); return null; })
                    .when(events).callEvent(any(TrapTriggerEvent.class));
            manager.tick();
            verify(mob, never()).setVelocity(any());
            doNothing().when(events).callEvent(any(TrapTriggerEvent.class));
            when(f.button.getType()).thenReturn(Material.AIR);
            manager.tick();
            verify(mob, never()).setVelocity(any());
            when(f.button.getType()).thenReturn(Material.STONE_BUTTON);
            manager.tick();
            verify(mob).setVelocity(velocity);
            stages.fire(event);
            manager.tick();
            verify(mob, times(1)).setVelocity(any());
        }
    }
}
