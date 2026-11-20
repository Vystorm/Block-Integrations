package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import Vystorm.vystorm_core.lang.Lang;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** /bi status names the switched-off features instead of always saying every combo is active (2.44.2). */
class StatusSwitchesTest {
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = BlockIntegrations.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static CommandSender status(Feature.Switches switches, Lang lang) throws Exception {
        BlockIntegrations plugin = mock(BlockIntegrations.class, CALLS_REAL_METHODS);
        set(plugin, "lang", lang);
        set(plugin, "switches", switches);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        plugin.statusCommand(sender);
        return sender;
    }

    @Test
    void switchedOffFeaturesAreNamedInOrder() throws Exception {
        Lang lang = mock(Lang.class);
        when(lang.plain(any(CommandSender.class), anyString())).thenAnswer(call -> "<" + call.getArgument(1) + ">");
        CommandSender sender = status(Feature.Switches.without(Feature.TOWERS, Feature.SORTER), lang);
        verify(lang).send(sender, "status.switches-off", "features", "<switch-names.sorter>, <switch-names.towers>");
        verify(lang, never()).send(eq(sender), eq("status.switches-on"));
    }

    @Test
    void allOnSaysSo() throws Exception {
        Lang lang = mock(Lang.class);
        CommandSender sender = status(Feature.Switches.without(), lang);
        verify(lang).send(sender, "status.switches-on");
    }

    @Test
    void statusLineNoLongerClaimsEveryComboIsActive() throws Exception {
        for (String language : new String[] {"en", "de", "fr", "it", "pt_br", "ru", "zh_cn"}) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    Files.newBufferedReader(Path.of("lang", language + ".yml"), StandardCharsets.UTF_8));
            String line = yaml.getString("status.line");
            assertTrue(line != null && line.endsWith("{combo_max}"), language + ": " + line);
            assertTrue(yaml.isString("status.switches-on"), language);
            assertTrue(yaml.getString("status.switches-off", "").contains("{features}"), language);
            assertFalse(line.toLowerCase(java.util.Locale.ROOT).contains("sorter/"), language);
        }
    }
}
