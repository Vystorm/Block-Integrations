package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core._advancedMethods.placeholderHandler.PlaceholderTesting;
import Vystorm.vystorm_core._uiMethods.settings.ConfigEditor;
import Vystorm.vystorm_core.commands.CommandSpec;
import Vystorm.vystorm_core.lang.Lang;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * Every feature has a switch (2.44.0): off stops the feature and answers the one shared message, on behaves as before,
 * an old config.yml gains the new keys, combos.enabled: false of an old config keeps the four combos it gated off, and
 * the placeholders resolve and are empty while their feature is off.
 */
class SwitchesTest {
    private static final List<String> LANGUAGES = List.of("en", "de", "fr", "it", "pt_br", "ru", "zh_cn");
    private static final List<String> PLACEHOLDERS = List.of("combos_owned", "warp_pads_owned", "warp_pads_max", "warp_pads_shared",
            "towers_owned", "furnaces_owned", "combos_total", "towers_total", "furnaces_total");
    private static final Path OLD = Path.of("tests/resources/config-2.43.yml");
    @TempDir Path dir;

    // ------------------------------------------------------------------ config, descriptions, old configs

    @Test void givenPackagedConfig_whenRead_thenEverySwitchIsOnAndExplained() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("config.yml"));
        for (Feature feature : Feature.values()) {
            assertTrue(config.isBoolean(feature.path), feature.path);
            assertTrue(config.getBoolean(feature.path), feature.path);
            String parent = feature.path.substring(0, feature.path.lastIndexOf('.'));
            assertFalse(config.getComments(feature.path).isEmpty() && config.getComments(parent).isEmpty(), "comment for " + feature.path);
        }
    }

    @Test void givenEveryLanguage_whenRead_thenEverySwitchAndPlaceholderHasADescription() {
        for (String code : LANGUAGES) {
            YamlConfiguration lang = YamlConfiguration.loadConfiguration(new File("lang/" + code + ".yml"));
            assertFalse(lang.getString("feature-off", "").isBlank(), code);
            assertTrue(lang.getString("feature-needs", "").contains("{switch}"), code);
            for (Feature feature : Feature.values()) assertFalse(lang.getString(feature.description(), "").isBlank(), code + " " + feature.description());
            for (String key : PLACEHOLDERS) assertFalse(lang.getString("placeholder." + key, "").isBlank(), code + " placeholder." + key);
        }
    }

    @Test void givenAConfigFrom2_43_whenRead_thenEveryFeatureStaysOn() {
        YamlConfiguration old = YamlConfiguration.loadConfiguration(OLD.toFile());
        assertEquals(Set.of(), Feature.Switches.read(old).off());
        assertFalse(Feature.migrateComboGate(old), "combos.enabled: true needs no migration");
    }

    @Test void givenAnOldConfigWithCombosOff_whenMigrated_thenTheFourGatedCombosStayOff() throws Exception {
        YamlConfiguration old = YamlConfiguration.loadConfiguration(OLD.toFile());
        old.set("combos.enabled", false);
        Feature.Switches before = Feature.Switches.read(old);
        assertTrue(before.on(Feature.HEAL), "without the migration the heal block would turn on");
        assertTrue(Feature.migrateComboGate(old));
        Feature.Switches after = Feature.Switches.read(old);
        for (Feature feature : List.of(Feature.COMBO_UPGRADES, Feature.HEAL, Feature.HARVESTER, Feature.WARP_PADS, Feature.XP_COLLECTOR))
            assertFalse(after.on(feature), feature.path);
        assertTrue(after.on(Feature.SORTER), "the sorter was never gated by combos.enabled");
        assertTrue(after.on(Feature.LAUNCHER));
        assertFalse(Feature.migrateComboGate(old), "only once");
        // a new config that switches only the upgrades off keeps the four combos on
        YamlConfiguration fresh = YamlConfiguration.loadConfiguration(new File("config.yml"));
        fresh.set("combos.enabled", false);
        assertFalse(Feature.migrateComboGate(fresh));
        assertTrue(Feature.Switches.read(fresh).on(Feature.HEAL));
        assertTrue(Feature.Switches.read(fresh).on(Feature.WARP_PADS));
    }

    @Test void givenAConfigFrom2_43_whenCoreMergesDefaults_thenEveryNewSwitchIsAddedAndOldValuesStay() throws Exception {
        Path data = Files.createDirectories(dir.resolve("BlockIntegrations"));
        Files.writeString(data.resolve("config.yml"), Files.readString(OLD).replace("\r\n", "\n")
                .replace("traps:\n  enabled: true", "traps:\n  enabled: false"));
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getResource("config.yml")).thenAnswer(call -> Files.newInputStream(Path.of("config.yml")));
        when(plugin.getConfig()).thenReturn(YamlConfiguration.loadConfiguration(data.resolve("config.yml").toFile()));

        assertTrue(Files.readString(data.resolve("config.yml")).contains("traps:\n  enabled: false"), "test setup");
        assertTrue(ConfigEditor.mergeDefaults(plugin) >= 13);
        YamlConfiguration merged = YamlConfiguration.loadConfiguration(data.resolve("config.yml").toFile());
        for (Feature feature : Feature.values()) assertTrue(merged.isBoolean(feature.path), feature.path);
        assertEquals(Set.of(Feature.TRAPS), Feature.Switches.read(merged).off(), "an existing value is never changed");
        String text = Files.readString(data.resolve("config.yml"));
        assertTrue(text.contains("The inventory sorter itself"), "the comment of a new section comes along");
        assertTrue(text.contains("The heal block. Off"), "the comment of a new key in an old section comes along");
    }

    @Test void givenTheMasterSwitchOff_whenAnyFeatureIsAsked_thenOff() {
        Feature.Switches off = Feature.Switches.without(Feature.BLOCKS);
        for (Feature feature : Feature.values()) if (feature != Feature.BLOCKS) assertFalse(off.on(feature), feature.name());
        assertFalse(Feature.Switches.without(Feature.TOWERS).on(Feature.TOWER_EFFECTS), "tower effects need towers");
        assertTrue(Feature.Switches.without(Feature.COMBO_UPGRADES).on(Feature.HEAL), "combos.enabled is only the upgrades now");
        assertEquals(1, Arrays.stream(Feature.values()).filter(Feature::needsRestart).count());
    }

    @Test void givenComboTypes_whenTheirMenuIsAsked_thenOnlyThoseWithMoreThanUpgradesOpenWithoutCombosEnabled() {
        assertTrue(ComboManager.ownMenu(ComboType.XP_COLLECTOR), "stored XP can still be taken out");
        assertTrue(ComboManager.ownMenu(ComboType.WARP_PAD));
        assertTrue(ComboManager.ownMenu(ComboType.HARVESTER));
        assertFalse(ComboManager.ownMenu(ComboType.SORTER));
        assertFalse(ComboManager.ownMenu(ComboType.HEAL));
    }

    // ------------------------------------------------------------------ commands

    @Test void givenASwitchOff_whenItsCommandRuns_thenTheSharedMessageAnswers() throws Exception {
        for (Feature feature : List.of(Feature.MENU, Feature.DASHBOARD, Feature.WEB, Feature.TRAPS, Feature.TOWERS, Feature.WARP_PADS,
                Feature.FURNACES, Feature.SORT_LOCKS)) {
            BlockIntegrations plugin = plugin(feature);
            Player player = player();
            switch (feature) {
                case MENU -> plugin.menuCommand(player);
                case DASHBOARD -> plugin.dashboardCommand(player);
                case WEB -> plugin.webCommand(player, false);
                case TRAPS -> plugin.trapsCommand(player);
                case TOWERS -> plugin.towersCommand(player, new String[] {"towers"});
                case WARP_PADS -> plugin.warpPadsCommand(player, new String[] {"warppads"});
                case FURNACES -> plugin.furnacesCommand(player);
                default -> plugin.sortLockCommand(player, new String[] {"sortlock", "list"});
            }
            verify(langOf(plugin)).send(player, "feature-off");
        }
        BlockIntegrations noWorkstations = plugin(Feature.BREWING, Feature.CAMPFIRES);
        Player player = player();
        noWorkstations.workstationsCommand(player);
        verify(langOf(noWorkstations)).send(player, "feature-off");
        BlockIntegrations master = plugin(Feature.BLOCKS);
        Player other = player();
        master.trapsCommand(other);
        verify(langOf(master)).send(other, "feature-needs", "switch", "blocks.enabled");
    }

    @Test void givenEverythingOn_whenTheSwitchesAreAsked_thenNoOffMessage() throws Exception {
        BlockIntegrations plugin = plugin();
        for (Feature feature : Feature.values()) assertFalse(plugin.off(player(), feature), feature.name());
        verify(langOf(plugin), never()).send(any(CommandSender.class), anyString());
    }

    @Test void givenASwitchOff_whenTheCommandTreeIsAsked_thenItsSubcommandsAreHiddenAndReturnWhenItIsOnAgain() throws Exception {
        BlockIntegrations plugin = plugin();
        CommandSpec spec = BlockCommands.spec(plugin);
        for (CommandSpec child : spec.children().values()) assertTrue(child.visibleTo(null), child.name());
        field(plugin, "switches", Feature.Switches.without(Feature.TOWERS, Feature.SORT_LOCKS));
        assertFalse(spec.children().get("towers").visibleTo(null));
        assertFalse(spec.children().get("sortlock").visibleTo(null));
        assertFalse(spec.children().get("chestpriority").visibleTo(null));
        assertTrue(spec.children().get("traps").visibleTo(null));
        field(plugin, "switches", Feature.Switches.without(Feature.BREWING));
        assertTrue(spec.children().get("workstations").visibleTo(null), "campfires still on");
        field(plugin, "switches", Feature.Switches.without(Feature.BLOCKS));
        assertFalse(spec.children().get("furnaces").visibleTo(null));
        assertFalse(spec.children().get("workstations").visibleTo(null));
        assertTrue(spec.children().get("status").visibleTo(null), "status and reload stay");
        assertTrue(spec.children().get("reload").visibleTo(null));
        field(plugin, "switches", Feature.Switches.without());
        for (CommandSpec child : spec.children().values()) assertTrue(child.visibleTo(null), "on again: " + child.name());
    }

    @Test void givenASwitchChangedByAReload_whenTheSwitchesApply_thenCommandListsAreRefreshedWithoutRegisteringAgain() throws Exception {
        BlockIntegrations plugin = plugin(Feature.WEB);
        Vystorm.vystorm_core.API core = mock(Vystorm.vystorm_core.API.class);
        Vystorm.vystorm_core._uiMethods.commands.CommandInterface commands = mock(Vystorm.vystorm_core._uiMethods.commands.CommandInterface.class);
        when(core.command()).thenReturn(commands);
        field(plugin, "core", core);
        field(plugin, "commandSwitches", Feature.Switches.without());
        java.lang.reflect.Method apply = BlockIntegrations.class.getDeclaredMethod("applySwitches");
        apply.setAccessible(true);
        apply.invoke(plugin);
        verify(commands, times(1)).refresh();
        apply.invoke(plugin);
        verify(commands, times(1)).refresh();
        verify(commands, never()).register(any(), any(), any());
    }

    // ------------------------------------------------------------------ placeholders

    @Test void givenTheNamespace_whenResolved_thenValuesAndEmptyWhileOff() {
        PlaceholderTesting testing = PlaceholderTesting.detached();
        Set<Feature> off = EnumSet.noneOf(Feature.class);
        OfflinePlayer viewer = PlaceholderTesting.viewer("Steve");
        ComboManager combos = mock(ComboManager.class);
        when(combos.combosOwnedBy(viewer.getUniqueId())).thenAnswer(call -> List.of(mock(Combo.class), mock(Combo.class), mock(Combo.class)));
        when(combos.warpPadsOwnedBy(viewer.getUniqueId())).thenAnswer(call -> List.of(mock(Combo.class)));
        when(combos.warpGuests(viewer.getUniqueId())).thenReturn(Set.of(UUID.randomUUID(), UUID.randomUUID()));
        when(combos.comboCount()).thenReturn(7);
        TowerManager towers = mock(TowerManager.class);
        when(towers.towersOwnedBy(viewer.getUniqueId())).thenAnswer(call -> List.of(mock(Tower.class)));
        when(towers.towerCount()).thenReturn(4);
        FurnaceManager furnaces = mock(FurnaceManager.class);
        when(furnaces.furnaceCount()).thenReturn(9);
        when(furnaces.furnacesOwnedBy(viewer.getUniqueId())).thenReturn(List.of());
        BlocksPlaceholders.register(testing.placeholders(), PlaceholderTesting.plugin("BlockIntegrations"), new BlocksPlaceholders.Source() {
            @Override public boolean on(Feature feature) { return !off.contains(feature); }
            @Override public ComboManager combos() { return combos; }
            @Override public TowerManager towers() { return towers; }
            @Override public FurnaceManager furnaces() { return furnaces; }
        }, key -> key);

        assertEquals("3", testing.resolve(viewer, "%vystorm_blocks_combos_owned%"));
        assertEquals("1", testing.resolve(viewer, "{blocks:warp_pads_owned}"));
        assertEquals("2", testing.resolve(viewer, "%vystorm_blocks_warp_pads_shared%"));
        assertEquals("1", testing.resolve(viewer, "%vystorm_blocks_towers_owned%"));
        assertEquals("0", testing.resolve(viewer, "%vystorm_blocks_furnaces_owned%"));
        assertEquals("7", testing.resolve(null, "%vystorm_blocks_combos_total%"));
        assertEquals("4", testing.resolve(null, "%vystorm_blocks_towers_total%"));
        assertEquals("9", testing.resolve(null, "%vystorm_blocks_furnaces_total%"));

        off.addAll(List.of(Feature.TOWERS, Feature.WARP_PADS));
        assertEquals("", testing.resolve(viewer, "%vystorm_blocks_towers_owned%"));
        assertEquals("", testing.resolve(null, "%vystorm_blocks_towers_total%"));
        assertEquals("", testing.resolve(viewer, "%vystorm_blocks_warp_pads_owned%"));
        assertEquals("3", testing.resolve(viewer, "%vystorm_blocks_combos_owned%"));
        off.add(Feature.BLOCKS);
        assertEquals("", testing.resolve(viewer, "%vystorm_blocks_combos_owned%"));
    }

    // ------------------------------------------------------------------ helpers

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(true);
        return player;
    }

    /** A plugin object without a server: lang and the switches set, real methods run. */
    private static BlockIntegrations plugin(Feature... off) throws Exception {
        BlockIntegrations plugin = mock(BlockIntegrations.class, Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        field(plugin, "lang", mock(Lang.class));
        field(plugin, "switches", Feature.Switches.without(off));
        return plugin;
    }

    private static Lang langOf(BlockIntegrations plugin) throws Exception {
        Field field = BlockIntegrations.class.getDeclaredField("lang");
        field.setAccessible(true);
        return (Lang) field.get(plugin);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = BlockIntegrations.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
