package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import Vystorm.vystorm_core.lang.Lang;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The bundled language files are complete, consistent and cover every key the code uses. */
class LangBundleTest {
    private static final Pattern LITERAL = Pattern.compile("\"((?:general|menu|guide|status|chat|sorter|vacuum|upgrade|combo|"
            + "combo-upgrade|tower|tower-upgrade|furnace|furnace-upgrade|workstation|workstations|trap|portable|settings|web)\\.[a-z0-9._-]*[a-z0-9])\"");

    static Set<String> keys(String code) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(Path.of("lang", code + ".yml").toFile());
        Set<String> keys = new TreeSet<>();
        for (String key : yaml.getKeys(true)) if (!yaml.isConfigurationSection(key)) keys.add(key);
        return keys;
    }

    @Test
    void englishAndGermanDefineTheSameKeys() {
        Set<String> english = keys("en"), german = keys("de");
        assertFalse(english.isEmpty());
        Set<String> onlyEnglish = new TreeSet<>(english); onlyEnglish.removeAll(german);
        Set<String> onlyGerman = new TreeSet<>(german); onlyGerman.removeAll(english);
        assertEquals(Set.of(), onlyEnglish, "missing in de.yml");
        assertEquals(Set.of(), onlyGerman, "missing in en.yml");
    }

    @Test
    void simplifiedChineseMatchesEnglish() {
        YamlConfiguration english = YamlConfiguration.loadConfiguration(Path.of("lang", "en.yml").toFile());
        YamlConfiguration chinese = YamlConfiguration.loadConfiguration(Path.of("lang", "zh_cn.yml").toFile());
        Set<String> onlyEnglish = new TreeSet<>(keys("en")); onlyEnglish.removeAll(keys("zh_cn"));
        Set<String> onlyChinese = new TreeSet<>(keys("zh_cn")); onlyChinese.removeAll(keys("en"));
        assertEquals(Set.of(), onlyEnglish, "missing in zh_cn.yml");
        assertEquals(Set.of(), onlyChinese, "missing in en.yml");
        Pattern placeholder = Pattern.compile("\\{[a-z0-9_]+}|%[a-z0-9_]+%");
        Pattern rawArg = Pattern.compile("<arg[:>]");
        for (String key : keys("en")) {
            String en = String.join("\n", texts(english, key)), zh = String.join("\n", texts(chinese, key));
            assertEquals(tokens(placeholder, en), tokens(placeholder, zh), "placeholders differ: " + key);
            assertFalse(rawArg.matcher(zh).find(), "raw <arg> tag: " + key);
            if (english.isList(key) && !key.startsWith("commands.")) assertEquals(english.getStringList(key).size(), chinese.getStringList(key).size(), key);
        }
    }

    private static java.util.List<String> texts(YamlConfiguration yaml, String key) {
        return yaml.isList(key) ? yaml.getStringList(key) : java.util.List.of(String.valueOf(yaml.getString(key)));
    }

    private static Set<String> tokens(Pattern pattern, String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) found.add(matcher.group());
        return found;
    }

    @Test
    void noReservedYamlBooleanKeys() {
        for (String key : keys("en"))
            for (String part : key.split("\\."))
                assertFalse(Set.of("on", "off", "yes", "no", "true", "false").contains(part), key);
    }

    @Test
    void everyKeyUsedInTheSourcesExists() throws IOException {
        Set<String> english = keys("en");
        // Portable navigation uses Core's own translated button labels.
        english.addAll(Set.of("menu.home", "menu.back"));
        Set<String> configPaths = YamlConfiguration.loadConfiguration(Path.of("config.yml").toFile()).getKeys(true);
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = LITERAL.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!english.contains(key) && !configPaths.contains(key)) missing.add(key);
                }
            }
        }
        for (ComboType type : ComboType.values()) {
            check(english, missing, type.nameKey());
            check(english, missing, "combo." + type.configKey + ".structure");
        }
        for (TowerType type : TowerType.values()) check(english, missing, type.nameKey());
        for (TowerType.AttackStyle style : TowerType.AttackStyle.values())
            check(english, missing, "web.guide.tower.style." + style.name().toLowerCase(java.util.Locale.ROOT));
        for (ComboType type : ComboType.values()) check(english, missing, "web.guide.combo." + type.configKey);
        for (FurnaceUpgrade upgrade : FurnaceUpgrade.values()) check(english, missing, "web.guide.furnace." + upgrade.configKey);
        for (ComboUpgrade upgrade : ComboUpgrade.values()) check(english, missing, upgrade.nameKey());
        for (TowerUpgrade upgrade : TowerUpgrade.values()) check(english, missing, upgrade.nameKey());
        for (FurnaceUpgrade upgrade : FurnaceUpgrade.values()) check(english, missing, upgrade.nameKey());
        for (Material furnace : new Material[] {Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER})
            check(english, missing, FurnaceManager.furnaceNameKey(furnace));
        for (String section : BlockIntegrations.SETTINGS_SECTIONS)
            for (String part : new String[] {"title", "header", "description"})
                check(english, missing, "settings.section." + section + "." + part);
        assertEquals(Set.of(), missing);
    }

    @Test
    void placeholdersRenderInBothLanguages() throws IOException {
        Lang lang = Lang.create("BlockIntegrations", Map.of(
                "en", Files.readString(Path.of("lang", "en.yml")), "de", Files.readString(Path.of("lang", "de.yml"))));
        assertEquals("Sorted 5 items into 2 chest(s).", lang.plain("en", "sorter.success", "moved", 5, "chests", 2));
        assertEquals("5 Items in 2 Kiste(n) einsortiert.", lang.plain("de", "sorter.success", "moved", 5, "chests", 2));
        assertEquals("Archer Tower Upgrades", lang.plain("en", "tower.dialog.title", "name", "Archer Tower"));
        assertTrue(lang.plain("de", "upgrade.line-max", "upgrade", "Schaden", "level", 10, "max", 10, "value", "24.0 Schaden")
                .endsWith("-> MAX | MAX"));
        assertEquals(lang.lines("en", "guide.body").size(), lang.lines("de", "guide.body").size());
        Lang chinese = Lang.create("BlockIntegrations", Map.of(
                "en", Files.readString(Path.of("lang", "en.yml")), "zh_cn", Files.readString(Path.of("lang", "zh_cn.yml"))));
        assertEquals("已将 5 个物品整理进 2 个箱子。", chinese.plain("zh_cn", "sorter.success", "moved", 5, "chests", 2));
        assertEquals(lang.lines("en", "guide.body").size(), chinese.lines("zh_cn", "guide.body").size());
    }

    private static void check(Set<String> english, Set<String> missing, String key) {
        if (!english.contains(key)) missing.add(key);
    }
}
