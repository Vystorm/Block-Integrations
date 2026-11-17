package local.blockintegrations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.Plugin;

class ChestAccessTest {
 @Test void loadedUnlockedChestAndMissingCityPluginAllowButLockedOrUnloadedDeny() {
  try (var bukkit=mockStatic(Bukkit.class)) {
   var pm=mock(PluginManager.class); bukkit.when(Bukkit::getPluginManager).thenReturn(pm);
   World world=mock(World.class); Player player=mock(Player.class); when(player.getWorld()).thenReturn(world);
   when(world.isChunkLoaded(0,0)).thenReturn(true);
   Block block=mock(Block.class); Chest chest=mock(Chest.class); when(block.getState()).thenReturn(chest);
   when(world.getBlockAt(1,64,1)).thenReturn(block);
   when(world.getBlockAt(any(Location.class))).thenReturn(block);
   Inventory inventory=mock(Inventory.class); when(inventory.getLocation()).thenReturn(new Location(world,1,64,1));
   assertTrue(ChestAccess.allowed(player,inventory));
   when(chest.isLocked()).thenReturn(true); assertFalse(ChestAccess.allowed(player,inventory));
   when(chest.isLocked()).thenReturn(false); when(world.isChunkLoaded(0,0)).thenReturn(false);
   assertFalse(ChestAccess.allowed(player,inventory));
  }
 }
 @Test void installedButDisabledCityProviderFailsClosed() {
  try(var bukkit=mockStatic(Bukkit.class)) {
   var pm=mock(PluginManager.class); bukkit.when(Bukkit::getPluginManager).thenReturn(pm);
   when(pm.getPlugin("VystormCityClaim")).thenReturn(mock(Plugin.class));
   assertFalse(ChestAccess.cityAllows(java.util.UUID.randomUUID(),new Location(mock(World.class),0,64,0)));
  }
 }
 @Test void doubleChestRequiresBothLocations() {
  var pair=mock(DoubleChestInventory.class); var half=mock(Inventory.class);
  when(pair.getLeftSide()).thenReturn(half); when(pair.getRightSide()).thenReturn(half);
  assertFalse(ChestAccess.allowed(mock(Player.class),pair));
 }
}
