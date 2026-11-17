package local.blockintegrations;

import Vystorm.vystorm_core._uiMethods.autoGui.GuiCallbacks;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ManagedGuiMigrationTest {
    @Test
    void defaultManagedCallbackCancelsDragBeforeAPluginCanMoveItems() {
        GuiCallbacks callbacks = new GuiCallbacks() {};
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        callbacks.drag(drag);
        verify(drag).setCancelled(true);
    }

    @Test
    void managedUpgradeButtonsAcceptNormalClicksWithoutCreativeSlotPackets() {
        Player player = mock(Player.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
        org.junit.jupiter.api.Assertions.assertTrue(BlockIntegrations.normalMenuClick(event, player));

        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.NUMBER_KEY);
        org.junit.jupiter.api.Assertions.assertFalse(BlockIntegrations.normalMenuClick(event, player));
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.SWAP_OFFHAND);
        org.junit.jupiter.api.Assertions.assertFalse(BlockIntegrations.normalMenuClick(event, player));
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.DOUBLE_CLICK);
        org.junit.jupiter.api.Assertions.assertFalse(BlockIntegrations.normalMenuClick(event, player));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.RIGHT);
        org.junit.jupiter.api.Assertions.assertTrue(BlockIntegrations.normalMenuClick(event, player));
        var creativePacket = mock(org.bukkit.event.inventory.InventoryCreativeEvent.class);
        when(creativePacket.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
        org.junit.jupiter.api.Assertions.assertFalse(BlockIntegrations.normalMenuClick(creativePacket, player));
    }

}
