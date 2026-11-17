package local.blockintegrations;

import Vystorm.vystorm_core.API;
import Vystorm.vystorm_core._uiMethods.settings.Setting;
import Vystorm.vystorm_core._uiMethods.settings.SettingScope;
import Vystorm.vystorm_core._uiMethods.settings.SettingsInterface;
import Vystorm.vystorm_core._uiMethods.settings.SettingsSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettingsRegistrationTest {
    @Test
    void serverSectionsCoverExistingConfigKeysWithinTheirBounds() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("config.yml"));
        BlockIntegrations plugin = mock(BlockIntegrations.class, CALLS_REAL_METHODS);
        doReturn(config).when(plugin).getConfig();
        API core = TestCore.api();
        SettingsInterface settings = mock(SettingsInterface.class);
        when(core.settings()).thenReturn(settings);
        var field = BlockIntegrations.class.getDeclaredField("core");
        field.setAccessible(true);
        field.set(plugin, core);
        var register = BlockIntegrations.class.getDeclaredMethod("registerSettings");
        register.setAccessible(true);
        register.invoke(plugin);

        ArgumentCaptor<SettingsSection> captor = ArgumentCaptor.forClass(SettingsSection.class);
        verify(settings, times(6)).register(any(Plugin.class), captor.capture());
        List<SettingsSection> sections = captor.getAllValues();
        assertEquals(Arrays.asList(BlockIntegrations.SETTINGS_SECTIONS),
                sections.stream().map(SettingsSection::id).toList());
        java.util.Set<String> english = LangBundleTest.keys("en");
        for (SettingsSection section : sections) {
            assertTrue(english.contains(section.title()), "lang/en.yml fehlt " + section.title());
            assertTrue(english.contains(section.description()), "lang/en.yml fehlt " + section.description());
            assertTrue(english.contains("settings.section." + section.id() + ".title"), section.id());
            assertEquals(SettingScope.SERVER, section.scope());
            assertEquals(BlockIntegrations.ADMIN_PERMISSION, section.permission());
            for (Setting setting : section.settings()) {
                assertTrue(config.contains(setting.id()), "config.yml fehlt " + setting.id());
                assertTrue(english.contains(setting.label()), "lang/en.yml fehlt " + setting.label());
                assertTrue(english.contains(setting.description()), "lang/en.yml fehlt " + setting.description());
                if (setting.kind() == Setting.Kind.NUMBER) {
                    double value = (Double) setting.getter().apply(null);
                    assertTrue(value >= setting.min() && value <= setting.max(), setting.id() + "=" + value);
                }
            }
        }
    }
}
