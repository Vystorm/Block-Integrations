package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import Vystorm.vystorm_core.commands.CommandRegistration;
import Vystorm.vystorm_core.commands.CommandSpec;
import Vystorm.vystorm_core.commands.CommandTesting;
import Vystorm.vystorm_core.lang.Lang;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** /blockintegrations through Core's command tree with the real language files: names, routing, help per language. */
class BlockCommandsTest {
    private static final List<String> LANGUAGES = List.of("en", "de", "zh_cn");
    private static final String[] ALL = {BlockCommands.USE, BlockCommands.COMBOS, BlockCommands.TOWERS, BlockCommands.FURNACES,
            BlockCommands.WORKSTATIONS, BlockCommands.ADMIN, TrapManager.PERMISSION, ComboManager.WARP_PERMISSION};
    private final List<String> chat = new ArrayList<>();
    private final BlockIntegrations plugin = mock(BlockIntegrations.class);

    private static Lang lang() throws Exception {
        return Lang.create("BlockIntegrations", Map.of("en", read("en"), "de", read("de"), "zh_cn", read("zh_cn")));
    }

    private static String read(String code) throws Exception {
        return Files.readString(Path.of("lang", code + ".yml"));
    }

    private CommandRegistration registration() throws Exception {
        Plugin owner = mock(Plugin.class);
        when(owner.isEnabled()).thenReturn(true);
        when(owner.getName()).thenReturn("BlockIntegrations");
        return CommandTesting.detached(owner, lang(), BlockCommands.spec(plugin));
    }

    private Player player(Locale locale, String... permissions) {
        Player p = mock(Player.class);
        when(p.locale()).thenReturn(locale);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.hasPermission(anyString())).thenAnswer(i -> List.of(permissions).contains((String) i.getArgument(0)));
        doAnswer(i -> { chat.add(PlainTextComponentSerializer.plainText().serialize((Component) i.getArgument(0))); return null; })
                .when(p).sendMessage(any(Component.class));
        return p;
    }

    private ConsoleCommandSender console() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(console.hasPermission(anyString())).thenReturn(true);
        doAnswer(i -> { chat.add(PlainTextComponentSerializer.plainText().serialize((Component) i.getArgument(0))); return null; })
                .when(console).sendMessage(any(Component.class));
        return console;
    }

    private static String[] line(String text) {
        return text.isEmpty() ? new String[0] : text.split(" ");
    }

    @Test
    void givenLanguages_whenCompleting_thenSubcommandsInPlayersLanguageAndByPermission() throws Exception {
        CommandRegistration r = registration();
        String[] none = {""};
        assertEquals(List.of("menu", "dashboard", "web", "status", "combos", "traps", "towers", "furnaces", "workstations", "warppads",
                "sortlock", "chestpriority", "reload", "help"), r.suggest(player(Locale.US, ALL), "bi", none));
        assertEquals(List.of("menue", "übersicht", "web", "status", "kombos", "fallen", "tuerme", "oefen", "werkstationen", "warpfelder",
                "sortiersperre", "truhenvorrang", "neuladen", "hilfe"), r.suggest(player(Locale.GERMANY, ALL), "bi", none));
        assertEquals(List.of("菜单", "机器", "网页", "状态", "组合", "陷阱", "防御塔", "熔炉", "工作站", "传送垫", "锁定槽位", "箱子优先", "重载", "帮助"),
                r.suggest(player(Locale.SIMPLIFIED_CHINESE, ALL), "bi", none));
        assertEquals(List.of("menu", "dashboard", "web", "sortlock", "chestpriority", "help"), r.suggest(player(Locale.US, BlockCommands.USE), "bi", none));
        assertEquals(List.of(), r.suggest(player(Locale.US), "bi", none), "without blockintegrations.use nothing");
        assertEquals(List.of("liste", "nahe"), r.suggest(player(Locale.GERMANY, BlockCommands.USE, BlockCommands.TOWERS), "bi", new String[]{"tuerme", ""}));
        assertEquals(List.of("list", "near", "remove"), r.suggest(player(Locale.US, ALL), "bi", new String[]{"TOWERS", ""}));
        assertEquals(List.of("hinzufuegen", "ueberlauf", "entfernen", "liste", "leeren"),
                r.suggest(player(Locale.GERMANY, BlockCommands.USE), "bi", new String[]{"chestpriority", ""}));
        assertEquals(List.of("anleitung"), r.suggest(player(Locale.GERMANY, BlockCommands.USE), "bi", new String[]{"web", ""}));
        assertTrue(r.suggest(player(Locale.US, BlockCommands.USE), "bi", new String[]{"sortlock", ""}).containsAll(List.of("list", "clear", "9", "35")));
    }

    @Test
    void givenOldEnglishPathsAndOldInlineAliases_whenRun_thenSameHandlersWithTheOldArguments() throws Exception {
        CommandRegistration r = registration();
        Player p = player(Locale.US, ALL);
        for (String text : List.of("menu", "menue", "MENU")) { r.execute(p, "bi", line(text)); }
        verify(plugin, org.mockito.Mockito.times(3)).menuCommand(p);
        r.execute(p, "blockintegrations", new String[0]);
        verify(plugin).rootCommand(p);
        r.execute(p, "bi", line("web"));
        verify(plugin).webCommand(p, false);
        r.execute(p, "bi", line("web guide"));
        verify(plugin).webCommand(p, true);
        for (String text : List.of("status")) r.execute(p, "bi", line(text));
        verify(plugin).statusCommand(p);
        for (String text : List.of("combos", "kombos")) r.execute(p, "bi", line(text));
        verify(plugin, org.mockito.Mockito.times(2)).combosCommand(p);
        for (String text : List.of("combos mine", "kombos meine", "组合 我的")) r.execute(p, "bi", line(text));
        verify(plugin, org.mockito.Mockito.times(3)).myCombosCommand(p);
        for (String text : List.of("traps", "fallen")) r.execute(p, "bi", line(text));
        verify(plugin, org.mockito.Mockito.times(2)).trapsCommand(p);
        for (String text : List.of("furnaces", "oefen", "ofen")) r.execute(p, "bi", line(text));
        verify(plugin, org.mockito.Mockito.times(3)).furnacesCommand(p);
        for (String text : List.of("workstations", "werkstationen")) r.execute(p, "bi", line(text));
        verify(plugin, org.mockito.Mockito.times(2)).workstationsCommand(p);
        r.execute(p, "bi", line("reload"));
        verify(plugin).reloadCommand(p);

        r.execute(p, "bi", line("towers"));
        r.execute(p, "bi", line("tuerme"));
        verify(plugin, org.mockito.Mockito.times(2)).towersCommand(any(), aryEq(new String[]{"towers"}));
        r.execute(p, "bi", line("towers list"));
        verify(plugin).towersCommand(any(), aryEq(new String[]{"towers", "list"}));
        r.execute(p, "bi", line("towers list Steve"));
        verify(plugin).towersCommand(any(), aryEq(new String[]{"towers", "list", "Steve"}));
        r.execute(p, "bi", line("towers near"));
        verify(plugin).towersCommand(any(), aryEq(new String[]{"towers", "near"}));
        r.execute(p, "bi", line("towers remove"));
        verify(plugin).towersCommand(any(), aryEq(new String[]{"towers", "remove"}));

        r.execute(p, "bi", line("warppads"));
        r.execute(p, "bi", line("warpfelder"));
        verify(plugin, org.mockito.Mockito.times(2)).warpPadsCommand(any(), aryEq(new String[]{"warppads"}));
        r.execute(p, "bi", line("warppads list"));
        verify(plugin).warpPadsCommand(any(), aryEq(new String[]{"warppads", "list"}));
        r.execute(p, "bi", line("warppads share Alex"));
        verify(plugin).warpPadsCommand(any(), aryEq(new String[]{"warppads", "share", "Alex"}));
        r.execute(p, "bi", line("warpfelder unshare Alex"));
        verify(plugin).warpPadsCommand(any(), aryEq(new String[]{"warppads", "unshare", "Alex"}));

        r.execute(p, "bi", line("sortlock"));
        verify(plugin).sortLockCommand(any(), aryEq(new String[]{"sortlock"}));
        r.execute(p, "bi", line("sortlock 12"));
        verify(plugin).sortLockCommand(any(), aryEq(new String[]{"sortlock", "12"}));
        r.execute(p, "bi", line("sortlock list"));
        verify(plugin).sortLockCommand(any(), aryEq(new String[]{"sortlock", "list"}));
        r.execute(p, "bi", line("sortlock clear"));
        verify(plugin).sortLockCommand(any(), aryEq(new String[]{"sortlock", "clear"}));

        r.execute(p, "bi", line("chestpriority"));
        verify(plugin).chestPriorityCommand(any(), aryEq(new String[]{"chestpriority"}));
        for (String action : List.of("add", "overflow", "remove", "list", "clear")) {
            r.execute(p, "bi", line("chestpriority " + action));
            verify(plugin).chestPriorityCommand(any(), aryEq(new String[]{"chestpriority", action}));
        }
    }

    @Test
    void givenLocalizedNames_whenRun_thenTheEnglishArguments() throws Exception {
        CommandRegistration r = registration();
        Player german = player(Locale.GERMANY, ALL);
        r.execute(german, "blockkombos", line("tuerme liste Steve"));
        verify(plugin).towersCommand(any(), aryEq(new String[]{"towers", "list", "Steve"}));
        r.execute(german, "bi", line("web anleitung"));
        verify(plugin).webCommand(german, true);
        r.execute(german, "bi", line("truhenvorrang überlauf"));
        verify(plugin).chestPriorityCommand(any(), aryEq(new String[]{"chestpriority", "overflow"}));
        Player chinese = player(Locale.SIMPLIFIED_CHINESE, ALL);
        r.execute(chinese, "方块组合", line("传送垫 共享 Alex"));
        verify(plugin).warpPadsCommand(any(), aryEq(new String[]{"warppads", "share", "Alex"}));
        r.execute(chinese, "bi", line("锁定槽位 清除"));
        verify(plugin).sortLockCommand(any(), aryEq(new String[]{"sortlock", "clear"}));
        // everyone may use every installed language's names
        r.execute(player(Locale.US, ALL), "bi", line("oefen"));
        verify(plugin).furnacesCommand(any());
    }

    @Test
    void givenConsole_whenRun_thenAdminCommandsWorkAndPlayerOnlyAreRefused() throws Exception {
        CommandRegistration r = registration();
        ConsoleCommandSender console = console();
        r.execute(console, "blockintegrations", new String[0]);
        verify(plugin).rootCommand(console);
        r.execute(console, "blockintegrations", line("status"));
        verify(plugin).statusCommand(console);
        r.execute(console, "blockintegrations", line("towers list Steve"));
        verify(plugin).towersCommand(any(), aryEq(new String[]{"towers", "list", "Steve"}));
        r.execute(console, "blockintegrations", line("reload"));
        verify(plugin).reloadCommand(console);
        chat.clear();
        r.execute(console, "blockintegrations", line("menu"));
        assertEquals("Only players can do that.", chat.get(0));
        verify(plugin, never()).menuCommand(any());
    }

    @Test
    void givenMissingPermission_whenRun_thenRefusedAndNotRun() throws Exception {
        CommandRegistration r = registration();
        r.execute(player(Locale.GERMANY, BlockCommands.USE), "bi", line("neuladen"));
        assertEquals("Dafür hast du keine Berechtigung.", chat.get(0));
        r.execute(player(Locale.US, BlockCommands.USE, BlockCommands.TOWERS), "bi", line("towers remove"));
        verify(plugin, never()).reloadCommand(any());
        verify(plugin, never()).towersCommand(any(), any());
        Player outsider = player(Locale.US);
        r.execute(outsider, "bi", new String[0]);
        verify(plugin, never()).rootCommand(any());
    }

    @Test
    void givenHelp_whenGerman_thenLocalizedUsageAndDescriptions() throws Exception {
        registration().execute(player(Locale.GERMANY, ALL), "bi", line("hilfe tuerme"));
        String all = String.join("\n", chat);
        assertTrue(all.contains("/bi tuerme liste [SPIELER] – Deine Türme auflisten"), all);
        assertTrue(all.contains("/bi tuerme entfernen – Den Turm entfernen, den du ansiehst"), all);
        chat.clear();
        registration().execute(player(Locale.SIMPLIFIED_CHINESE, BlockCommands.USE), "bi", line("帮助"));
        all = String.join("\n", chat);
        assertTrue(all.contains("/bi 菜单 – 打开方块指南菜单"), all);
        assertFalse(all.contains("重载"), all);
        verifyNoInteractions(plugin);
    }

    @Test
    void givenTree_whenChecked_thenOldAliasesLocalizedRootAliasesAndNoConflicts() throws Exception {
        CommandRegistration r = registration();
        assertEquals(List.of("blockint", "bi"), r.fixedAliases());
        assertEquals("blockkombos", r.lang().raw("de", "commands.blockintegrations.aliases"));
        assertEquals("方块组合", r.lang().raw("zh_cn", "commands.blockintegrations.aliases"));
        assertEquals(List.of(), r.conflicts());
    }

    @Test
    void givenLanguageFiles_whenCompared_thenSameCommandKeysAndEveryNodeDescribed() {
        Map<String, YamlConfiguration> files = new java.util.LinkedHashMap<>();
        for (String code : LANGUAGES) files.put(code, YamlConfiguration.loadConfiguration(Path.of("lang", code + ".yml").toFile()));
        Set<String> english = commandKeys(files.get("en"));
        for (String code : LANGUAGES) assertEquals(english, commandKeys(files.get(code)), code);
        List<CommandSpec> nodes = new ArrayList<>();
        collect(BlockCommands.spec(null), nodes);
        for (CommandSpec node : nodes)
            for (String code : LANGUAGES) {
                String description = files.get(code).getString(node.langPath() + ".description");
                assertTrue(description != null && !description.isBlank(), code + ": " + node.langPath());
                if (!node.isRoot()) assertTrue(files.get(code).isString(node.langPath() + ".name"), code + ": " + node.langPath());
            }
    }

    @Test
    void itemLocksRouteInEveryLanguageAndRequireAPlayerWithUsePermission() throws Exception {
        CommandRegistration r = registration();
        Player p = player(Locale.US, BlockCommands.USE);
        for (String command : List.of("sortlock item", "sortiersperre gegenstand", "锁定槽位 物品"))
            r.execute(p, "bi", line(command));
        verify(plugin, org.mockito.Mockito.times(3)).sortLockCommand(p, new String[]{"sortlock", "item"});
        for (String command : List.of("sortlock items", "sortiersperre gegenstaende", "锁定槽位 物品列表"))
            r.execute(p, "bi", line(command));
        verify(plugin, org.mockito.Mockito.times(3)).sortLockCommand(p, new String[]{"sortlock", "items"});
        for (String command : List.of("sortlock clearitems", "sortiersperre gegenstaendeleeren", "锁定槽位 清除物品"))
            r.execute(p, "bi", line(command));
        verify(plugin, org.mockito.Mockito.times(3)).sortLockCommand(p, new String[]{"sortlock", "clearitems"});
        assertTrue(r.suggest(p, "bi", new String[]{"sortlock", ""}).containsAll(List.of("item", "items", "clearitems")));
        org.mockito.Mockito.clearInvocations(plugin);
        r.execute(console(), "bi", line("sortlock item"));
        r.execute(player(Locale.US), "bi", line("sortlock item"));
        verifyNoInteractions(plugin);
    }
    private static Set<String> commandKeys(YamlConfiguration yaml) {
        Set<String> out = new TreeSet<>();
        for (String key : yaml.getKeys(true)) if (key.startsWith("commands.") && !yaml.isConfigurationSection(key)) out.add(key);
        return out;
    }

    private static void collect(CommandSpec node, List<CommandSpec> out) {
        out.add(node);
        for (CommandSpec child : node.children().values()) collect(child, out);
    }

}
