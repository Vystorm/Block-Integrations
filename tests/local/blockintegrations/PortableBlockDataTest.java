package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PortableBlockDataTest {
    @Test void workstationPayloadsKeepOwnershipAndRejectIncompatibleBonuses() throws Exception {
        for (Material material : new Material[]{Material.BREWING_STAND, Material.CAMPFIRE, Material.SOUL_CAMPFIRE}) {
            String bonus = material == Material.BREWING_STAND ? "fuel" : "yield";
            var state = new PortableBlockData.State(material, "workstation", material.name(),
                    Map.of("speed", 4, bonus, 2), UUID.randomUUID(), 0);
            assertEquals(state, PortableBlockData.decode(PortableBlockData.encode(state)));
            assertThrows(IllegalArgumentException.class, () -> PortableBlockData.encode(
                    new PortableBlockData.State(material, "workstation", material.name(),
                            Map.of(bonus.equals("fuel") ? "yield" : "fuel", 1), state.owner(), 0)));
        }
    }
    @Test
    void validPayloadsRoundTripAndReadOnlyAcceptsMatchingNativeMaterial() throws Exception {
        UUID owner = UUID.randomUUID();
        PortableBlockData.State source = new PortableBlockData.State(
                Material.SKELETON_SKULL, "tower", "archer",
                Map.of("damage", 4, "range", 2), owner, 0.0D);
        byte[] payload = PortableBlockData.encode(source);

        assertEquals(source, PortableBlockData.decode(payload));

        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.namespace()).thenReturn("blockintegrations");
        PortableBlockData codec = new PortableBlockData(plugin);
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(item.getType()).thenReturn(Material.SKELETON_SKULL);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(), any())).thenReturn(payload);
        assertEquals(source, codec.read(item));

        when(item.getType()).thenReturn(Material.ZOMBIE_HEAD);
        assertNull(codec.read(item));
    }

    @Test
    void rejectsUnknownVersionOversizeAndInvalidTypeCombinations() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(2);
        }
        assertThrows(IOException.class, () -> PortableBlockData.decode(bytes.toByteArray()));
        assertThrows(IOException.class, () -> PortableBlockData.decode(new byte[4097]));
        assertThrows(IllegalArgumentException.class, () -> PortableBlockData.encode(
                new PortableBlockData.State(Material.STONE, "combo", "sorter", Map.of(), null, 0.0D)));
        assertThrows(IllegalArgumentException.class, () -> PortableBlockData.encode(
                new PortableBlockData.State(Material.FURNACE, "tower", "archer", Map.of(), null, 0.0D)));
    }

    @Test
    void loreWithoutPersistentPayloadIsIgnored() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.namespace()).thenReturn("blockintegrations");
        PortableBlockData codec = new PortableBlockData(plugin);
        ItemStack loreOnly = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(loreOnly.hasItemMeta()).thenReturn(true);
        when(loreOnly.getItemMeta()).thenReturn(meta);
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(java.util.List.of("§6BlockIntegrations §7– Schützenturm",
                "§7Schaden: §f10"));
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(), any())).thenReturn(null);

        assertNull(codec.read(loreOnly));
    }
}
