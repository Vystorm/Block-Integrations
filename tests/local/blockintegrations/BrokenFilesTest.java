package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.lang.Lang;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A config.yml with a YAML error is never saved over, neither at start nor on /bi reload or from the settings menu. */
class BrokenFilesTest {
    /** A pre-2.44.0 config (combos.enabled: false) whose migration would save, with a YAML error in line 4. */
    static final String BROKEN = "combos:\n  enabled: false\n  upgrades: false\n broken here\nradius: 10\n";

    @TempDir Path folder;
    final List<LogRecord> logged = new ArrayList<>();

    static YamlConfiguration bukkitLoad(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.load(file); } catch (Exception ignored) { }
        return config;
    }

    BlockIntegrations plugin(Lang lang) throws Exception {
        BlockIntegrations plugin = mock(BlockIntegrations.class, CALLS_REAL_METHODS);
        FileConfiguration[] config = {null};
        File file = folder.resolve("config.yml").toFile();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logged.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        doReturn(folder.toFile()).when(plugin).getDataFolder();
        doReturn(logger).when(plugin).getLogger();
        doAnswer(call -> { config[0] = bukkitLoad(file); return null; }).when(plugin).reloadConfig();
        doAnswer(call -> { if (config[0] == null) config[0] = bukkitLoad(file); return config[0]; }).when(plugin).getConfig();
        doAnswer(call -> { config[0].save(file); return null; }).when(plugin).saveConfig();
        Field field = BlockIntegrations.class.getDeclaredField("lang");
        field.setAccessible(true);
        field.set(plugin, lang);
        return plugin;
    }

    @Test void givenBrokenConfig_whenStartAndReloadReadIt_thenTheFileStaysAndTheReloadAnswerNamesLineAndColumn() throws Exception {
        Path file = folder.resolve("config.yml");
        Files.writeString(file, BROKEN, StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(file);
        Lang lang = mock(Lang.class);
        BlockIntegrations plugin = plugin(lang);
        assertNotNull(BlockIntegrations.readConfig(plugin, false));
        assertArrayEquals(before, Files.readAllBytes(file), "start changed config.yml");
        logged.clear();
        CommandSender admin = mock(CommandSender.class);
        plugin.reloadCommand(admin);
        verify(lang).send(eq(admin), eq("config-error"), eq("problem"), argThat(p -> p.toString().startsWith("line 4, column ")));
        verify(lang, never()).send(admin, "general.reloaded");
        verify(plugin, never()).reloadConfig();
        verify(plugin, never()).saveConfig();
        assertArrayEquals(before, Files.readAllBytes(file), "reload changed config.yml");
        assertEquals(1, logged.stream().filter(r -> r.getLevel() == Level.SEVERE).count());
        assertFalse(YamlCheck.saveConfig(plugin), "the settings menu does not save over it");
        assertArrayEquals(before, Files.readAllBytes(file));
        for (String code : List.of("en", "de", "fr", "it", "pt_br", "ru", "zh_cn"))
            assertTrue(YamlConfiguration.loadConfiguration(new File("lang/" + code + ".yml")).getString("config-error", "").contains("{problem}"), code);
    }

    @Test void givenValidOldConfig_whenRead_thenTheComboMigrationIsStillSaved() throws Exception {
        Path file = folder.resolve("config.yml");
        Files.writeString(file, "combos:\n  enabled: false\n  upgrades: false\n", StandardCharsets.UTF_8);
        assertNull(BlockIntegrations.readConfig(plugin(mock(Lang.class)), false));
        assertTrue(YamlConfiguration.loadConfiguration(file.toFile()).contains(Feature.HEAL.path));
    }

    @Test void givenBrokenYaml_whenDescribed_thenLineColumnAndProblemAreNamed() throws Exception {
        Path file = folder.resolve("x.yml");
        Files.writeString(file, BROKEN, StandardCharsets.UTF_8);
        assertTrue(YamlCheck.problem(file.toFile()).matches("line 4, column \\d+: .+"), YamlCheck.problem(file.toFile()));
    }
}
