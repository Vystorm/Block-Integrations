package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage;
import Vystorm.vystorm_core._coreMethods.eventRegulator.EventStageTesting;
import Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * The block break and place handlers of BlockIntegrations, registered as in the managers' {@code start()} and called
 * by Vystorm Core's real stage dispatcher together with the handlers of a second plugin: a tower of another owner
 * denies in PROTECT before any feature acts, and no stored machine follows a break or placement that was denied.
 */
class BlockStagesTest {
    private final BlockIntegrations plugin = mock(BlockIntegrations.class);
    private final Plugin other = EventStageTesting.plugin("AFeaturePlugin");
    private final Player player = mock(Player.class);
    private final Block block = mock(Block.class);
    private final List<String> calls = new ArrayList<>();
    private boolean foreignTower, healBlockTooClose;

    // every manager with its real registration; the handlers are silent except the two that deny
    private final ComboManager combos = mock(ComboManager.class, CALLS_REAL_METHODS);
    private final TowerManager towers = mock(TowerManager.class, CALLS_REAL_METHODS);
    private final AnvilManager anvils = mock(AnvilManager.class, CALLS_REAL_METHODS);
    private final FurnaceManager furnaces = mock(FurnaceManager.class, CALLS_REAL_METHODS);
    private final WorkstationManager workstations = mock(WorkstationManager.class, CALLS_REAL_METHODS);

    @BeforeEach
    void setUp() throws Exception {
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.isEnabled()).thenReturn(true);
        for (var manager : java.util.Map.<Class<?>, Object>of(ComboManager.class, combos, TowerManager.class, towers,
                AnvilManager.class, anvils, FurnaceManager.class, furnaces, WorkstationManager.class, workstations).entrySet()) {
            Field field = manager.getKey().getDeclaredField("plugin");
            field.setAccessible(true);
            field.set(manager.getValue(), plugin);
        }
        doAnswer(call -> { if (foreignTower) call.<Cancellable>getArgument(0).setCancelled(true); return null; }).when(towers).onBreakOthers(any());
        doAnswer(call -> { if (healBlockTooClose) call.<Cancellable>getArgument(0).setCancelled(true); return null; }).when(combos).onHealBlockPlace(any());
        doNothing().when(combos).onBlockPlace(any());
        doNothing().when(combos).onBlockBreak(any());
        doNothing().when(towers).onBlockPlace(any());
        doNothing().when(towers).onBlockBreak(any());
        doNothing().when(anvils).place(any());
        doNothing().when(anvils).breakBlock(any());
        doNothing().when(furnaces).onFurnacePlace(any());
        doNothing().when(furnaces).onFurnaceBreak(any());
        doNothing().when(workstations).place(any());
    }

    /** The managers in the order BlockIntegrations starts them, after a plugin with a feature and statistics. */
    private EventStageTesting everyManager() {
        EventStageTesting harness = EventStageTesting.detached();
        EventStages stages = harness.stages();
            stages.handle(other, BlockBreakEvent.class, EventStage.ACT, event -> calls.add("break act"));
            stages.handle(other, BlockBreakEvent.class, EventStage.OBSERVE, event -> calls.add("break observe"));
            stages.handle(other, BlockPlaceEvent.class, EventStage.ACT, event -> calls.add("place act"));
            stages.handle(other, BlockPlaceEvent.class, EventStage.OBSERVE, event -> calls.add("place observe"));
            combos.handleBlockEvents(stages);
            towers.handleBlockEvents(stages);
            anvils.handleBlockEvents(stages);
            furnaces.handleBlockEvents(stages);
            workstations.handleBlockEvents(stages);
        return harness;
    }

    private BlockPlaceEvent placing() {
        return new BlockPlaceEvent(block, mock(BlockState.class), block, mock(ItemStack.class), player, true, EquipmentSlot.HAND);
    }

    @Test
    void givenTowerOfAnotherOwner_whenBroken_thenItIsDeniedBeforeAnyFeatureAndNoMachineIsForgotten() {
        foreignTower = true;

        assertTrue(everyManager().fire(new BlockBreakEvent(block, player)).isCancelled());

        assertEquals(List.of(), calls);
        verify(towers, never()).onBlockBreak(any());
        verify(combos, never()).onBlockBreak(any());
        verify(anvils, never()).breakBlock(any());
        verify(furnaces, never()).onFurnaceBreak(any());
    }

    @Test
    void givenHealBlockTooCloseToAnother_whenPlaced_thenItIsRefusedBeforeAnyFeatureAndNoMachineIsRecorded() {
        healBlockTooClose = true;

        assertTrue(everyManager().fire(placing()).isCancelled());

        assertEquals(List.of(), calls);
        verify(combos, never()).onBlockPlace(any());
        verify(towers, never()).onBlockPlace(any());
        verify(anvils, never()).place(any());
        verify(furnaces, never()).onFurnacePlace(any());
        verify(workstations, never()).place(any());
    }

    @Test
    void givenAllowedBreakAndPlacement_whenFired_thenProtectionAndRulesComeFirstAndTheBookkeepingKeepsItsOrder() {
        EventStageTesting harness = everyManager();

        BlockBreakEvent broken = harness.fire(new BlockBreakEvent(block, player));
        BlockPlaceEvent placed = harness.fire(placing());

        assertFalse(broken.isCancelled());
        assertFalse(placed.isCancelled());
        InOrder breaking = inOrder(combos, towers, anvils, furnaces);
        breaking.verify(towers).onBreakOthers(broken);       // PROTECT
        breaking.verify(combos).onBlockBreak(broken);        // OBSERVE, in the order the managers start
        breaking.verify(towers).onBlockBreak(broken);
        breaking.verify(anvils).breakBlock(broken);
        breaking.verify(furnaces).onFurnaceBreak(broken);
        InOrder placing = inOrder(combos, towers, anvils, furnaces, workstations);
        placing.verify(combos).onHealBlockPlace(placed);     // ADJUST
        placing.verify(combos).onBlockPlace(placed);         // OBSERVE, in the order the managers start
        placing.verify(towers).onBlockPlace(placed);
        placing.verify(anvils).place(placed);
        placing.verify(furnaces).onFurnacePlace(placed);
        placing.verify(workstations).place(placed);
        // the other plugin's feature ran between the rules and the bookkeeping, its statistics with the bookkeeping
        assertEquals(List.of("break act", "break observe", "place act", "place observe"), calls);
    }
}
