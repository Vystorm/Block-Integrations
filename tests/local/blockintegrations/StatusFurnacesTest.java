package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import Vystorm.vystorm_core.lang.Lang;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

/** /bi status also reports how many upgraded furnaces the server has indexed. */
class StatusFurnacesTest {
    private static void set(Object target, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void statusShowsIndexedFurnaceCount() throws Exception {
        FurnaceManager furnaces = mock(FurnaceManager.class, CALLS_REAL_METHODS);
        FurnaceIndex index = new FurnaceIndex();
        UUID owner = UUID.randomUUID();
        index.put(new BlockKey(UUID.randomUUID(), 1, 64, 1), new FurnaceIndex.Entry(owner, Material.FURNACE, Map.of("speed", 1)));
        index.put(new BlockKey(UUID.randomUUID(), 2, 64, 2), new FurnaceIndex.Entry(owner, Material.SMOKER, Map.of()));
        set(furnaces, FurnaceManager.class, "index", index);
        assertEquals(2, furnaces.furnaceCount());

        BlockIntegrations plugin = mock(BlockIntegrations.class, CALLS_REAL_METHODS);
        Lang lang = mock(Lang.class);
        set(plugin, BlockIntegrations.class, "lang", lang);
        set(plugin, BlockIntegrations.class, "furnaceManager", furnaces);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        plugin.statusCommand(sender);
        verify(lang).send(sender, "status.furnaces", "furnaces", 2);
    }
}
