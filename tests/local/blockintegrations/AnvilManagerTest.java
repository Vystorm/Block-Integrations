package local.blockintegrations;

import Vystorm.vystorm_core.API;
import Vystorm.vystorm_core._coreMethods.playerHandler.PlayerHandlerInterface;
import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnvilManagerTest {
    AnvilManager manager; Player player; PlayerHandlerInterface xp; World world;
    StateFile<AnvilManager.State> store; Map<BlockKey, AnvilManager.Anvil> anvils;
    UUID worldId = UUID.randomUUID(), owner = UUID.randomUUID();
    BlockKey key = new BlockKey(worldId, 0, 0, 0);
    AnvilManager.Anvil quote = new AnvilManager.Anvil(UUID.randomUUID(), owner, Map.of());
    @SuppressWarnings("unchecked") @BeforeEach void setup() throws Exception {
        var plugin = mock(BlockIntegrations.class);
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.getDataFolder()).thenReturn(new File("build/test-anvil"));
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        var core = TestCore.api(); xp = mock(PlayerHandlerInterface.class);
        when(plugin.core()).thenReturn(core); when(core.player()).thenReturn(xp);
        manager = new AnvilManager(plugin); manager.reloadSettings();
        var field = AnvilManager.class.getDeclaredField("anvils"); field.setAccessible(true);
        anvils = (Map<BlockKey, AnvilManager.Anvil>) field.get(manager); anvils.put(key, quote);
        store = mock(StateFile.class);
        field = AnvilManager.class.getDeclaredField("store"); field.setAccessible(true); field.set(manager, store);
        when(store.saveChecked(any())).thenReturn(true);
        world = mock(World.class); when(world.getUID()).thenReturn(worldId);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        var block = mock(Block.class); when(block.getType()).thenReturn(Material.ANVIL);
        when(world.getBlockAt(0, 0, 0)).thenReturn(block);
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(owner);
        when(player.hasPermission(AnvilManager.PERMISSION)).thenReturn(true);
        when(player.isOnline()).thenReturn(true); when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, .5, .5, .5));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(xp.withdrawExperience(player, 100)).thenReturn(true);
    }
    String buy() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            return manager.purchase(player, key, quote, "efficiency", 100);
        }
    }
    @Test void savesOnceAndRejectsDoublePurchaseFromStaleMenu() {
        assertEquals("upgraded", buy());
        assertEquals(1, anvils.get(key).level("efficiency"));
        assertEquals("denied", buy());
        verify(xp, times(1)).withdrawExperience(player, 100);
    }
    @Test void refundsAndRestoresRankAfterStorageFailure() {
        when(store.saveChecked(any())).thenReturn(false);
        assertEquals("unavailable", buy());
        assertEquals(quote, anvils.get(key));
        verify(xp).depositExperience(player, 100);
    }
    @Test void insufficientXpDoesNotChangeRankOrWriteStorage() {
        when(xp.withdrawExperience(player, 100)).thenReturn(false);
        assertEquals("need-xp", buy());
        assertEquals(quote, anvils.get(key)); verifyNoInteractions(store);
    }
    @Test void otherOwnersCannotBuyUpgrades() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        assertEquals("denied", buy()); verifyNoInteractions(xp, store);
    }
    @Test void durabilityUpgradePreventsUseDamageOnlyAfterPurchase() {
        var view = mock(org.bukkit.inventory.view.AnvilView.class);
        var inventory = mock(org.bukkit.inventory.AnvilInventory.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        var event = mock(com.destroystokyo.paper.event.block.AnvilDamagedEvent.class);
        when(event.getView()).thenReturn(view);
        new PaperAnvilDamage(manager).damage(event); verify(event, never()).setCancelled(true);
        anvils.put(key, quote.upgraded("durability"));
        new PaperAnvilDamage(manager).damage(event); verify(event).setCancelled(true);
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void bukkitDurabilityOnlyRestoresAfterAnUncancelledCompletedTake(boolean cancelled)throws Exception {
        anvils.put(key,quote.upgraded("durability"));
        var view=mock(org.bukkit.inventory.view.AnvilView.class);var inventory=mock(org.bukkit.inventory.AnvilInventory.class);
        when(view.getTopInventory()).thenReturn(inventory);when(inventory.getLocation()).thenReturn(new Location(world,0,0,0));
        var input=mock(org.bukkit.inventory.ItemStack.class);when(input.clone()).thenReturn(input);when(inventory.getItem(0)).thenReturn(input);
        Block block=world.getBlockAt(0,0,0);var data=mock(org.bukkit.block.data.BlockData.class);
        when(block.getBlockData()).thenReturn(data);when(data.clone()).thenReturn(data);when(data.getMaterial()).thenReturn(Material.ANVIL);
        var event=mock(org.bukkit.event.inventory.InventoryClickEvent.class);when(event.getView()).thenReturn(view);
        when(event.getRawSlot()).thenReturn(2);when(event.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        List<Runnable> scheduled=new ArrayList<>();var tasks=mock(Vystorm.vystorm_core.schedule.TaskGroup.class);
        doAnswer(a->{scheduled.add(a.getArgument(0));return null;}).when(tasks).now(any(Runnable.class));
        var field=AnvilManager.class.getDeclaredField("tasks");field.setAccessible(true);field.set(manager,tasks);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld(worldId)).thenReturn(world);
            manager.protectBukkitUse(event);
            assertEquals(1,scheduled.size());
            when(inventory.getItem(0)).thenReturn(null);when(block.getType()).thenReturn(Material.CHIPPED_ANVIL);when(event.isCancelled()).thenReturn(cancelled);
            scheduled.getFirst().run();
            verify(block,times(cancelled?0:1)).setBlockData(data,false);
        }
    }
}
