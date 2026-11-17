package local.blockintegrations;

import Vystorm.vystorm_core.API;
import Vystorm.vystorm_core._coreMethods.playerHandler.PlayerHandlerInterface;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationManagerTest {
    BlockIntegrations plugin; WorkstationManager manager; World world; Block block; TileState tile;
    Player player; PlayerHandlerInterface xp; Map<NamespacedKey,Object> values;
    UUID worldId = UUID.randomUUID(), owner = UUID.randomUUID();
    BlockKey key;

    @BeforeEach void setup() {
        plugin = mock(BlockIntegrations.class);
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.namespace()).thenReturn("blockintegrations");
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        API core = TestCore.api(); xp = mock(PlayerHandlerInterface.class);
        when(plugin.core()).thenReturn(core); when(core.player()).thenReturn(xp);
        manager = new WorkstationManager(plugin);
        world = mock(World.class); when(world.getUID()).thenReturn(worldId);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        block = mock(Block.class); when(block.getWorld()).thenReturn(world);
        when(block.getType()).thenReturn(Material.BREWING_STAND);
        when(world.getBlockAt(0,0,0)).thenReturn(block);
        tile = mock(BrewingStand.class); when(block.getState()).thenReturn(tile);
        when(tile.getType()).thenReturn(Material.BREWING_STAND);
        when(tile.update(false,false)).thenReturn(true);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(tile.getPersistentDataContainer()).thenReturn(data);
        values = new HashMap<>();
        when(data.get(any(NamespacedKey.class), any())).thenAnswer(i -> values.get(i.getArgument(0)));
        doAnswer(i -> { values.put(i.getArgument(0),i.getArgument(2)); return null; }).when(data).set(any(),any(),any());
        put("owner",owner.toString()); put("identity","original");
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(owner);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.isOnline()).thenReturn(true); when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world,.5,.5,.5));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(xp.withdrawExperience(player,100)).thenReturn(true);
        key = new BlockKey(worldId,0,0,0);
    }
    void put(String name,Object value) { values.put(new NamespacedKey("blockintegrations","workstation_"+name),value); }
    void select(int slot, String id) {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            manager.selectBrewing(player, key, id, slot);
        }
    }
    @Test void normalBrewingRemainsAvailableToOtherPlayersWithoutUpgradePayment() {
        var inventory = mock(org.bukkit.inventory.BrewerInventory.class);
        when(((BrewingStand) tile).getInventory()).thenReturn(inventory);
        put("owner", UUID.randomUUID().toString());
        when(player.hasPermission("blockintegrations.workstations.manage-others")).thenReturn(false);
        select(WorkstationManager.BREW_USE_SLOT, "original");
        verify(player).openInventory(inventory); verifyNoInteractions(xp);
        verify(tile, never()).update(anyBoolean(), anyBoolean());
    }
    @Test void staleOrDistantBrewingMenuCannotOpenInventory() {
        select(WorkstationManager.BREW_USE_SLOT, "old-block");
        when(player.getLocation()).thenReturn(new Location(world, 30, 0, 0));
        select(WorkstationManager.BREW_USE_SLOT, "original");
        verify(player, never()).openInventory(any(org.bukkit.inventory.Inventory.class));
        verifyNoInteractions(xp);
    }
    String buy() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            return manager.purchase(player,key,"original","speed",0,100);
        }
    }
    @Test void chargesCoreOnceAndRejectsRepeatedClick() {
        assertEquals("upgraded",buy()); assertEquals(1,manager.level(tile,"speed"));
        assertEquals("changed",buy()); verify(xp,times(1)).withdrawExperience(player,100);
    }
    @Test void potionUpgradesUseSeparateQuadraticPricesAndExtraStopsAtFive() {
        manager.reloadSettings();
        put("strength", 9);
        when(xp.withdrawExperience(player, 1000000)).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            assertEquals("upgraded", manager.purchase(player, key, "original", "strength", 9, 1000000));
            assertEquals(10, manager.level(tile, "strength"));
            put("extra", 5);
            assertEquals("changed", manager.purchase(player, key, "original", "extra", 5, 180000));
        }
        verify(xp).withdrawExperience(player, 1000000);
        verify(xp, never()).withdrawExperience(player, 180000);
    }
    @Test void cancelledBrewNeverChangesResultsOrRolls() {
        var event = mock(org.bukkit.event.inventory.BrewEvent.class);
        when(event.isCancelled()).thenReturn(true);
        manager.brewFinished(event);
        verify(event, never()).getResults();
    }
    @Test void failedPersistenceRefundsCorePayment() {
        when(tile.update(false,false)).thenReturn(false);
        assertEquals("unavailable",buy()); verify(xp).depositExperience(player,100);
    }
    @Test void insufficientXpDoesNotChangeBlock() {
        when(xp.withdrawExperience(player,100)).thenReturn(false);
        assertEquals("need-xp",buy()); assertEquals(0,manager.level(tile,"speed"));
        verify(tile,never()).update(anyBoolean(),anyBoolean());
    }
    @Test void replacedBlockRejectsStaleMenuWithoutCharging() {
        put("identity","replacement"); assertEquals("changed",buy()); verifyNoInteractions(xp);
    }
    @Test void foreignOwnerCannotPurchase() {
        put("owner",UUID.randomUUID().toString());
        when(player.hasPermission("blockintegrations.workstations.manage-others")).thenReturn(false);
        assertEquals("not-owner",buy()); verifyNoInteractions(xp);
    }
    @Test void unloadedChunkIsNeverAccessed() {
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertEquals("unavailable",buy()); verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        verifyNoInteractions(xp);
    }
    @Test void distantPlayerCannotPurchase() {
        when(player.getLocation()).thenReturn(new Location(world,100,0,0));
        assertEquals("unavailable",buy()); verifyNoInteractions(xp);
    }
    @Test void creativeUpgradeDoesNotChargeXp() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertEquals("upgraded",buy()); verifyNoInteractions(xp);
    }
    @Test void brewingSpeedsBothTimersWithoutConsumingIngredients() {
        put("speed",10); BrewingStartEvent event = mock(BrewingStartEvent.class);
        when(event.getBlock()).thenReturn(block); when(event.getBrewingTime()).thenReturn(400);
        when(event.getRecipeBrewTime()).thenReturn(400); manager.brew(event);
        verify(event).setBrewingTime(182); verify(event).setRecipeBrewTime(182);
    }
    @Test void cancelledFuelEventRemainsUntouched() {
        BrewingStandFuelEvent event = mock(BrewingStandFuelEvent.class);
        when(event.isCancelled()).thenReturn(true); manager.fuel(event);
        verify(event,never()).setFuelPower(anyInt());
    }
    @Test void disabledBrewingPreservesVanillaTimers() {
        put("speed",10); plugin.getConfig().set("workstations.brewing-stands",false); manager.reloadSettings();
        BrewingStartEvent event = mock(BrewingStartEvent.class); when(event.getBlock()).thenReturn(block);
        manager.brew(event); verify(event,never()).setBrewingTime(anyInt());
    }
    @Test void bonusesAreBoundedAndPreserveZeroTimers() {
        assertEquals(182,WorkstationStats.ticks(400,100)); assertEquals(0,WorkstationStats.ticks(0,10));
        assertEquals(40,WorkstationStats.fuel(20,10)); assertEquals(Integer.MAX_VALUE,WorkstationStats.fuel(Integer.MAX_VALUE,10));
        assertEquals(.25,WorkstationStats.yieldChance(100)); assertEquals(10000,WorkstationStats.cost(9,100));
    }
    @Test void bothCampfiresSpeedCookingAndLeaveFullStacksAlone() {
        for (Material material : List.of(Material.CAMPFIRE,Material.SOUL_CAMPFIRE)) {
            when(block.getType()).thenReturn(material); when(tile.getType()).thenReturn(material);
            put("speed",10); put("yield",10);
            CampfireStartEvent start = mock(CampfireStartEvent.class);
            when(start.getBlock()).thenReturn(block); when(start.getTotalCookTime()).thenReturn(600);
            manager.cookStart(start); verify(start).setTotalCookTime(273);
            BlockCookEvent cooked = mock(BlockCookEvent.class);
            var result = mock(org.bukkit.inventory.ItemStack.class);
            when(cooked.getBlock()).thenReturn(block); when(cooked.getResult()).thenReturn(result);
            when(result.getAmount()).thenReturn(64); when(result.getMaxStackSize()).thenReturn(64);
            manager.cooked(cooked); verify(cooked,never()).setResult(any());
        }
    }
    @Test void portablePlacementRestoresOwnerEvenWhenFeatureDisabled() {
        plugin.getConfig().set("workstations.brewing-stands",false); manager.reloadSettings();
        var portable = mock(PortableBlockData.class); when(plugin.portableBlocks()).thenReturn(portable);
        var item = mock(org.bukkit.inventory.ItemStack.class);
        UUID savedOwner = UUID.randomUUID();
        when(portable.read(item)).thenReturn(new PortableBlockData.State(Material.BREWING_STAND,"workstation",
                "BREWING_STAND",Map.of("speed",7,"fuel",3),savedOwner,0));
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.canBuild()).thenReturn(true); when(event.getBlockPlaced()).thenReturn(block);
        when(event.getItemInHand()).thenReturn(item);
        when(plugin.isEnabled()).thenReturn(true);
        // called the way Vystorm Core's stage dispatcher calls the handler: OBSERVE, not for a cancelled placement
        var stages = Vystorm.vystorm_core._coreMethods.eventRegulator.EventStageTesting.detached();
        manager.handleBlockEvents(stages.stages());
        when(event.isCancelled()).thenReturn(true);
        stages.fire(event);
        verify(tile,never()).update(false,false);
        when(event.isCancelled()).thenReturn(false);
        stages.fire(event);
        assertEquals(savedOwner,manager.owner(tile)); assertEquals(7,manager.level(tile,"speed"));
        verify(tile).update(false,false);
    }
}
