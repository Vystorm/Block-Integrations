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
import org.bukkit.ExplosionResult;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * The handlers of the other shared events (clicks, explosions, pistons, fluids, inventory clicks), registered as in
 * the managers' {@code start()} and called by Vystorm Core's real stage dispatcher together with a second plugin.
 */
class SharedEventStagesTest {
    private final BlockIntegrations plugin = mock(BlockIntegrations.class);
    private final Plugin other = EventStageTesting.plugin("AProtectionPlugin");
    private final Block block = mock(Block.class);
    private final List<String> calls = new ArrayList<>();
    private boolean towerBlock;

    // every manager with its real registration; the handlers are silent except the tower protection
    private final ComboManager combos = mock(ComboManager.class, CALLS_REAL_METHODS);
    private final TowerManager towers = mock(TowerManager.class, CALLS_REAL_METHODS);
    private final TrapManager traps = mock(TrapManager.class, CALLS_REAL_METHODS);
    private final AnvilManager anvils = mock(AnvilManager.class, CALLS_REAL_METHODS);
    private final FurnaceManager furnaces = mock(FurnaceManager.class, CALLS_REAL_METHODS);
    private final WorkstationManager workstations = mock(WorkstationManager.class, CALLS_REAL_METHODS);

    @BeforeEach
    void setUp() throws Exception {
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.isEnabled()).thenReturn(true);
        for (var manager : java.util.Map.<Class<?>, Object>of(ComboManager.class, combos, TowerManager.class, towers,
                TrapManager.class, traps, AnvilManager.class, anvils, FurnaceManager.class, furnaces,
                WorkstationManager.class, workstations).entrySet()) {
            Field field = manager.getKey().getDeclaredField("plugin");
            field.setAccessible(true);
            field.set(manager.getValue(), plugin);
        }
        doAnswer(call -> { if (towerBlock) call.<Cancellable>getArgument(0).setCancelled(true); return null; }).when(towers).onPistonExtend(any());
        doAnswer(call -> { if (towerBlock) call.<Cancellable>getArgument(0).setCancelled(true); return null; }).when(towers).onFluid(any());
        doAnswer(call -> { if (towerBlock) call.<BlockExplodeEvent>getArgument(0).blockList().clear(); return null; }).when(towers).onBlockExplode(any());
        doNothing().when(towers).onEntityExplode(any());
        doNothing().when(towers).onPistonRetract(any());
        doNothing().when(towers).onTowerInteract(any());
        doNothing().when(towers).onEntityDamaged(any());
        doNothing().when(combos).onComboInteract(any());
        doNothing().when(combos).onPlayerDamaged(any());
        doNothing().when(combos).onPlayerDeath(any());
        doNothing().when(traps).interact(any());
        doNothing().when(traps).piston(any());
        doNothing().when(anvils).interact(any());
        doNothing().when(anvils).take(any());
        doNothing().when(anvils).protectUse(any());
        doNothing().when(anvils).falling(any());
        doNothing().when(anvils).explode(any());
        doNothing().when(anvils).entityExplode(any());
        doNothing().when(furnaces).onFurnaceInteract(any());
        doNothing().when(furnaces).onFurnaceInventoryClick(any());
        doNothing().when(furnaces).onEntityExplode(any());
        doNothing().when(furnaces).onBlockExplode(any());
        doNothing().when(workstations).interact(any());
    }

    /** The managers in the order BlockIntegrations starts them. */
    private EventStageTesting everyManager() {
        EventStageTesting harness = EventStageTesting.detached();
        EventStages stages = harness.stages();
        combos.handleSharedEvents(stages);
        towers.handleSharedEvents(stages);
        traps.handleSharedEvents(stages);
        anvils.handleSharedEvents(stages);
        furnaces.handleSharedEvents(stages);
        workstations.handleSharedEvents(stages);
        return harness;
    }

    private PlayerInteractEvent click() {
        return new PlayerInteractEvent(mock(Player.class), Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP);
    }

    private InventoryClickEvent inventoryClick() {
        return new InventoryClickEvent(mock(InventoryView.class), InventoryType.SlotType.RESULT, 2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    @Test
    void givenTowerBlock_whenAPistonWouldPushIt_thenItIsDeniedBeforeTheTrapRuleAndAnyFeature() {
        towerBlock = true;
        EventStageTesting harness = everyManager();
        harness.handle(other, BlockPistonExtendEvent.class, EventStage.ACT, event -> calls.add("act"));
        harness.handle(other, BlockPistonExtendEvent.class, EventStage.OBSERVE, event -> calls.add("observe"));

        assertTrue(harness.fire(new BlockPistonExtendEvent(block, List.of(block), BlockFace.UP)).isCancelled());
        assertTrue(harness.fire(new BlockFromToEvent(block, block)).isCancelled());

        verify(traps, never()).piston(any());   // this plugin's own ADJUST handler of the same event
        assertEquals(List.of(), calls);
    }

    @Test
    void givenOrdinaryBlocks_whenAPistonPushesThem_thenTheTowerDecidesBeforeTheTrapRule() {
        EventStageTesting harness = everyManager();

        BlockPistonExtendEvent extend = harness.fire(new BlockPistonExtendEvent(block, List.of(block), BlockFace.UP));

        assertFalse(extend.isCancelled());
        InOrder order = inOrder(towers, traps);
        order.verify(towers).onPistonExtend(extend);   // PROTECT
        order.verify(traps).piston(extend);            // ADJUST
    }

    @Test
    void givenExplosion_whenFired_thenTowerBlocksAreTakenOutBeforeFeaturesAndTheBookkeepingSeesTheRest() {
        towerBlock = true;
        EventStageTesting harness = everyManager();
        harness.handle(other, BlockExplodeEvent.class, EventStage.ADJUST, event -> calls.add("adjust sees " + event.blockList().size()));
        doAnswer(call -> { calls.add("anvils see " + call.<BlockExplodeEvent>getArgument(0).blockList().size()); return null; }).when(anvils).explode(any());

        BlockExplodeEvent explosion = harness.fire(new BlockExplodeEvent(block, mock(BlockState.class),
                new ArrayList<>(List.of(block)), 1f, ExplosionResult.DESTROY));

        assertEquals(List.of("adjust sees 0", "anvils see 0"), calls);
        InOrder order = inOrder(towers, anvils, furnaces);
        order.verify(towers).onBlockExplode(explosion);     // PROTECT
        order.verify(anvils).explode(explosion);            // OBSERVE, in the order the managers start
        order.verify(furnaces).onBlockExplode(explosion);
    }

    @Test
    void givenExplosionDeniedByAnotherPlugin_whenFired_thenNoHandlerOfBlockIntegrationsRuns() {
        EventStageTesting harness = everyManager();
        // "AProtectionPlugin" sorts before "BlockIntegrations" inside PROTECT
        harness.handle(other, BlockExplodeEvent.class, EventStage.PROTECT, event -> event.setCancelled(true));

        harness.fire(new BlockExplodeEvent(block, mock(BlockState.class), new ArrayList<>(List.of(block)), 1f, ExplosionResult.DESTROY));

        verify(towers, never()).onBlockExplode(any());
        verify(anvils, never()).explode(any());
        verify(furnaces, never()).onBlockExplode(any());
    }

    @Test
    void givenAllowedClick_whenFired_thenTheMachinesActInTheOrderTheManagersStartAfterProtection() {
        EventStageTesting harness = everyManager();
        harness.handle(other, PlayerInteractEvent.class, EventStage.PROTECT, event -> calls.add("protect"));
        doAnswer(call -> { calls.add("combo"); return null; }).when(combos).onComboInteract(any());

        PlayerInteractEvent click = harness.fire(click());

        assertEquals(List.of("protect", "combo"), calls);
        InOrder order = inOrder(combos, towers, traps, anvils, furnaces, workstations);
        order.verify(combos).onComboInteract(click);
        order.verify(towers).onTowerInteract(click);
        order.verify(traps).interact(click);
        order.verify(anvils).interact(click);
        order.verify(furnaces).onFurnaceInteract(click);
        order.verify(workstations).interact(click);
    }

    @Test
    void givenClickDeniedByAProtection_whenFired_thenNoMachineActs() {
        EventStageTesting harness = everyManager();
        harness.handle(other, PlayerInteractEvent.class, EventStage.PROTECT, event -> event.setCancelled(true));

        assertTrue(harness.fire(click()).isCancelled());

        verify(combos, never()).onComboInteract(any());
        verify(towers, never()).onTowerInteract(any());
        verify(traps, never()).interact(any());
        verify(anvils, never()).interact(any());
        verify(furnaces, never()).onFurnaceInteract(any());
        verify(workstations, never()).interact(any());
    }

    @Test
    void givenClickConsumedByOneMachine_whenFired_thenTheLaterMachinesDoNotActOnIt() {
        EventStageTesting harness = everyManager();
        doAnswer(call -> { call.<Cancellable>getArgument(0).setCancelled(true); return null; }).when(towers).onTowerInteract(any());

        harness.fire(click());

        verify(combos).onComboInteract(any());
        verify(traps, never()).interact(any());
        verify(anvils, never()).interact(any());
        verify(furnaces, never()).onFurnaceInteract(any());
        verify(workstations, never()).interact(any());
    }

    @Test
    void givenInventoryClick_whenFired_thenTakingActsBeforeTheBookkeepingAndNothingRunsForADeniedClick() {
        EventStageTesting harness = everyManager();
        boolean[] deny = {false};
        harness.handle(other, InventoryClickEvent.class, EventStage.PROTECT, event -> event.setCancelled(deny[0]));

        InventoryClickEvent click = harness.fire(inventoryClick());

        InOrder order = inOrder(anvils, furnaces);
        order.verify(anvils).take(click);                        // ACT
        order.verify(anvils).protectUse(click);                  // OBSERVE, in the order the managers start
        order.verify(furnaces).onFurnaceInventoryClick(click);

        deny[0] = true;
        InventoryClickEvent denied = harness.fire(inventoryClick());
        verify(anvils, never()).take(denied);
        verify(anvils, never()).protectUse(denied);
        verify(furnaces, never()).onFurnaceInventoryClick(denied);
    }
}
