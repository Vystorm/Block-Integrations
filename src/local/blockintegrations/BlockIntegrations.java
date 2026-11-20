package local.blockintegrations;

import Vystorm.vystorm_core.API;
import Vystorm.vystorm_core.CoreApi;
import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core._parsersDebuggerEtc.debugger.DebugMessages;
import Vystorm.vystorm_core.compat.dialog.DialogButton;
import Vystorm.vystorm_core.compat.dialog.DialogRequest;

import Vystorm.vystorm_core._uiMethods.menu.MenuCategory;
import Vystorm.vystorm_core._uiMethods.settings.ConfigSettings;
import Vystorm.vystorm_core.compat.dialog.DialogBody;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Provides physical utility structures, attack towers and upgradable furnaces:
 * - light weighted (gold) pressure plate on copper: player inventory sorter
 * - heavy weighted (iron) pressure plate on copper: dropped-item vacuum
 * - slime block on redstone: directional launch pad (optionally a 3x3 slime field)
 * - ice on redstone: sprint booster
 * - white terracotta on copper on redstone: vertical launch with levitation
 * - honey block on redstone: heal aura
 */
public final class BlockIntegrations extends JavaPlugin implements Listener {
    static final String ADMIN_PERMISSION = "blockintegrations.admin";
    /** Core sentinel for the dialog exit button; Core renders it as its localized "Close" label. */
    static final String CLOSE_LABEL = "Schlie\u00dfen";
    private static final int FIRST_MAIN_INVENTORY_SLOT = 9;
    private static final int LAST_MAIN_INVENTORY_SLOT = 35;

    private static final Set<Material> BOOSTER_ICE_BLOCKS = Set.of(
            Material.ICE,
            Material.PACKED_ICE,
            Material.BLUE_ICE
    );

    private final Set<UUID> airbornePlayers = new HashSet<>();
    private final Map<UUID, Long> lastActivationTick = new HashMap<>();
    private final Map<UUID, Long> lastLaunchTick = new HashMap<>();
    private final Map<UUID, Long> lastBoosterTick = new HashMap<>();
    private final Map<UUID, Long> lastFloatLaunchTick = new HashMap<>();

    private int sortRadius;
    private long cooldownTicks;
    private boolean includeTrappedChests;
    private boolean showNothingMessage;

    private int vacuumRadius;
    private long vacuumCooldownTicks;
    private int harvestRadius;
    private long harvestCooldownTicks;
    private boolean harvestParticles;
    private boolean harvestPauseWhenFull;
    private long warpPadCooldownTicks;
    private int xpCollectorRadius;

    boolean harvestPauseWhenFull() {
        return harvestPauseWhenFull;
    }

    boolean harvestParticles() {
        return harvestParticles;
    }
    private boolean showVacuumNothingMessage;

    private double launchVelocity;
    private double launchMinimumUpwardVelocity;
    private long launchCooldownTicks;
    private boolean launchSound;

    private double boosterVelocity;
    private long boosterCooldownTicks;
    private boolean boosterSound;
    private boolean protectBoosterIce;

    private double floatLaunchVelocity;
    private int floatEffectDurationTicks;
    private int floatEffectAmplifier;
    private long floatLaunchCooldownTicks;
    private boolean floatLaunchSound;
    private API core;
    private Lang lang;
    private RadiusPreviewManager radiusPreviews;
    private ComboManager comboManager;
    private TowerManager towerManager;
    private TrapManager trapManager;
    private AnvilManager anvilManager;
    /** Tower markers, loot toast and portable tooltips for the client mod (Core {@code web-client-ui}); null without it. */
    private TowerClient client;
    private FurnaceManager furnaceManager;
    private WorkstationManager workstationManager;
    private PortableBlockData portableBlocks;
    private SortSlotLocks sortSlotLocks;
    private SortItemLocks sortItemLocks;
    private ChestPreferences chestPreferences;
    /** Optional web page (Vystorm Core 0.24.0+ with web enabled); {@code null} without it. */
    private StructureWeb web;
    private MachineDashboard dashboard;

    /** Placeholders through api.placeholders() require Core 0.65.0. */
    static final String MIN_CORE_VERSION = "0.65.0";

    /** Every feature switch as read in loadSettings; null (= all on) until then and in tests that mock the plugin. */
    private volatile Feature.Switches switches;
    /** The switches online players last got their command list for (null before the first registration). */
    private Feature.Switches commandSwitches;

    /** Whether a feature (and blocks.enabled) is on; static so managers holding a mocked plugin read the real field. */
    static boolean enabled(BlockIntegrations plugin, Feature feature) {
        Feature.Switches current = plugin == null ? null : plugin.switches;
        return current == null || current.on(feature);
    }

    boolean on(Feature feature) { return enabled(this, feature); }

    /** True (and the shared message sent) when the feature is off; names the needed switch when only that one is off. */
    boolean off(CommandSender sender, Feature feature) {
        if (on(feature)) return false;
        Feature.Switches current = switches;
        Feature missing = null;
        if (current != null && !current.off().contains(feature))
            missing = feature != Feature.BLOCKS && current.off().contains(Feature.BLOCKS) ? Feature.BLOCKS : feature.needs;
        if (missing != null) lang.send(sender, "feature-needs", "switch", missing.path, "name", lang.plain(sender, missing.label()));
        else lang.send(sender, "feature-off");
        return true;
    }

    @Override
    public void onEnable() {
        if (!CoreApi.atLeast(MIN_CORE_VERSION)) {
            getLogger().severe("Vystorm Core " + MIN_CORE_VERSION + " or newer is required; please update Core first. Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        core = CoreApi.api().orElseThrow();
        core.admin().updates().spigot(this, 139853);
        DebugMessages.mid("[BlockIntegrations] Connected to the Vystorm Core API");
        if (!core.compat().server().hasClass("org.bukkit.inventory.view.AnvilView"))
            getLogger().info("Native anvil enchantment bypass unavailable; enchantment upgrades require a valid vanilla result.");
        saveDefaultConfig();
        // Missing keys are added with their comments by Core's config editor (registered below); a config from before
        // 2.44.0 with combos.enabled: false keeps the heal block, harvester, warp pads and XP collector off.
        readConfig(this, false);
        sortItemLocks = new SortItemLocks(new NamespacedKey(this, "sort-locked-items"));
        sortSlotLocks = new SortSlotLocks(new NamespacedKey(this, "sort-locked-slots"));
        chestPreferences = new ChestPreferences(new NamespacedKey(this, "chest-preferences"));
        lang = core.lang().register(this);
        lang.setLanguage(getConfig().getString("language", "auto"));
        // tutorial.enabled is read once: Core lists tutorials when a plugin starts
        if (getConfig().getBoolean(Feature.TUTORIAL.path, true)) core.registerTutorial(this);
        loadSettings();
        portableBlocks = new PortableBlockData(this, lang);
        getServer().getPluginManager().registerEvents(this, this);
        // Core orders the handlers of the shared events; booster ice that may not melt is a protection
        core.priority().stages().handle(this, BlockFadeEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.PROTECT, this::onBlockFade);
        radiusPreviews = new RadiusPreviewManager(this);
        radiusPreviews.start();
        comboManager = new ComboManager(this);
        comboManager.start();
        try {
            WarpPadMenuEntry.register(core.gui().playerMenu(), this, comboManager);
        } catch (RuntimeException failure) {
            // No player menu entry; /bi warppads share still works.
        }
        towerManager = new TowerManager(this);
        towerManager.start();
        trapManager = new TrapManager(this);
        trapManager.start();
        anvilManager = new AnvilManager(this);
        anvilManager.start();
        furnaceManager = new FurnaceManager(this);
        furnaceManager.start();
        workstationManager = new WorkstationManager(this);
        workstationManager.start();
        dashboard = new MachineDashboard(this);
        dashboard.start();
        core.gui().registerMenu(this, "blocks", lang.ref("menu.title"), Material.ANVIL,
                p -> on(Feature.MENU) && mayOpenGuide(p), this::openBlockGuideMenu, MenuCategory.BUILDING.group(),
                lang.ref("menu.description"));
        registerSettings();
        try { registerPlaceholders(core.placeholders()); }
        catch (RuntimeException failure) { getLogger().log(java.util.logging.Level.WARNING, "Placeholders not registered", failure); }
        registerCommands();
        try {
            BlockConfigEditor.register(core.settings().config(), this, lang, this::reloadAll);
            BlockConfigEditor.declareSwitches(core.settings().config(), this, lang);
        } catch (RuntimeException failure) {
            DebugMessages.high("[BlockIntegrations] Config editor registration failed: " + failure);
        }
        if (on(Feature.WEB)) registerWeb();
        registerAdminModule();
        comboManager.registerStorageDiagnostics();
        towerManager.registerStorageDiagnostics();
        furnaceManager.registerStorageDiagnostics();
        core.data().migrationPreviews().register(this, "legacy-combos",
                    getDataFolder().toPath().resolve("combos.yml"), LegacyComboPreview::convert);
        startClient();
        DebugMessages.mid("[BlockIntegrations] Enabled: sorter, vacuum, launcher, sprint booster, float lift, heal block, "
                + "seven attack towers with loot/XP and upgradable furnaces.");
    }

    @Override
    public void onDisable() {
        if (dashboard != null) dashboard.stop();
        if (workstationManager != null) workstationManager.shutdown();
        if (anvilManager != null) anvilManager.shutdown();
        if (trapManager != null) trapManager.shutdown();
        if (radiusPreviews != null) {
            radiusPreviews.shutdown();
            radiusPreviews = null;
        }
        if (web != null) {
            web.shutdown();
            web = null;
        }
        if (comboManager != null) {
            comboManager.shutdown();
        }
        if (client != null) {
            try { client.shutdown(); } catch (RuntimeException ignored) { /* Core already stopped */ }
            client = null;
        }
        if (towerManager != null) {
            towerManager.shutdown();
        }
        if (furnaceManager != null) {
            furnaceManager.shutdown();
        }
        if (core != null) {
            core.gui().unregisterMenus(this);
            core.dialogs().removeAll(this);
            DebugMessages.mid("[BlockIntegrations] Disabled.");
        }
    }

    private void startClient() {
        try {
            TowerClient started = new TowerClient(this);
            started.start();
            client = started;
        } catch (RuntimeException failure) {
            client = null;
            DebugMessages.high("[BlockIntegrations] Client-mod features unavailable: " + failure);
        }
    }

    /** Client-mod features, or {@code null} (not started, unit tests). */
    TowerClient client() {
        return client;
    }

    RadiusPreviewManager radiusPreviews() {
        return radiusPreviews;
    }

    public API core() {
        return Objects.requireNonNull(core, "Vystorm Core API is not bound");
    }

    void dashboardCommand(Player player) { if (!off(player, Feature.DASHBOARD)) dashboard.open(player, 0); }

    /** Language bundle ({@code lang/<code>.yml}); {@code null} only in unit tests without a server. */
    public Lang lang() {
        return lang;
    }

    /** Number with the decimal separator of the viewer's language (e.g. {@code 1,5} in German, {@code 1.5} in English). */
    String number(CommandSender viewer, String format, double value) {
        return String.format(locale(viewer), format, value);
    }

    java.util.Locale locale(CommandSender viewer) {
        Lang bundle = lang();
        return locale(bundle == null ? "en" : bundle.language(viewer));
    }

    /** Number with the decimal separator of a language code (web page texts). */
    String number(String language, String format, double value) {
        return String.format(locale(language), format, value);
    }

    static java.util.Locale locale(String language) {
        return java.util.Locale.forLanguageTag((language == null ? "en" : language).replace('_', '-'));
    }

    TowerManager towerManager() {
        return towerManager;
    }

    FurnaceManager furnaceManager() {
        return furnaceManager;
    }

    ComboManager comboManager() {
        return comboManager;
    }

    /**
     * {@code /vadmin} module {@code blocks}: replaces Core's built-in catalog entry (status only) with the full list of
     * admin sub-commands. Core runs them as the clicking admin and confirms the mutating ones.
     */
    private void registerAdminModule() {
        try {
            core.admin().register(this, new Vystorm.vystorm_core._uiMethods.admin.AdminModule("blocks", lang.ref("admin.title"), getName(),
                    "blockintegrations", ADMIN_PERMISSION, "blockintegrations menu", java.util.List.of(
                    adminAction("status", "admin.status", "status", "", "", false),
                    adminAction("towers-all", "admin.towers-all", "towers list", "", "", false),
                    adminAction("towers-of", "admin.towers-of", "towers list", "<player>", "Steve", false),
                    adminAction("towers-near", "admin.towers-near", "towers near", "", "", false),
                    adminAction("tower-remove", "admin.tower-remove", "towers remove", "", "", true),
                    adminAction("reload", "admin.reload", "reload", "", "", true))));
        } catch (RuntimeException failure) {
            DebugMessages.high("[BlockIntegrations/Admin] Admin dashboard module could not be registered", failure);
        }
    }

    private Vystorm.vystorm_core._uiMethods.admin.AdminAction adminAction(String id, String labelKey, String fixed, String hint,
                                                                           String example, boolean mutating) {
        return new Vystorm.vystorm_core._uiMethods.admin.AdminAction(id, lang.ref(labelKey), ADMIN_PERMISSION, fixed, hint, example, mutating);
    }

    // ---------------------------------------------------------------------------------------------
    // Optional web page

    /** Registers the web module. */
    private void registerWeb() {
        try {
            web = BlocksWeb.register(this);
        } catch (RuntimeException failure) {
            web = null;
            DebugMessages.high("[BlockIntegrations/Web] Web page could not be registered", failure);
        }
    }

    /** Owner's structures changed (upgrade, build, removal, loot, …): open web pages refresh. No-op without web. */
    void structuresChanged(UUID owner) {
        if (web != null && owner != null) web.changed(owner);
        if (client != null && owner != null) client.changed(owner);
    }

    boolean webAvailable() {
        return web != null && web.available();
    }

    /** "Open in web" for dialogs: only shown while Core's web platform runs. */
    void addWebButton(List<DialogButton> buttons, Player player, String subPath) {
        if (!webAvailable() || lang == null) return;
        buttons.add(new DialogButton(lang.component(player, "web.button.label"), lang.component(player, "web.button.tooltip"),
                (p, r) -> openWeb(p, subPath)));
    }

    void openWeb(Player player, String subPath) {
        if (web != null) web.open(player, subPath);
        else lang.send(player, "web.unavailable");
    }

    PortableBlockData portableBlocks() {
        return portableBlocks;
    }

    private boolean mayOpenGuide(Player player) {
        return player.hasPermission(TrapManager.PERMISSION) || player.hasPermission("blockintegrations.combos")
                || player.hasPermission("blockintegrations.towers")
                || player.hasPermission("blockintegrations.furnaces")
                || player.hasPermission("blockintegrations.workstations");
    }

    /** Native overview of all upgradeable blocks; entry point from /vmenu → Building & Tech. */
    void openBlockGuideMenu(Player player) {
        List<DialogBody> body = lang.lines(player, "guide.body").stream()
                .map(line -> (DialogBody) DialogBody.plainMessage(line)).toList();
        List<DialogButton> buttons = new ArrayList<>();
        if (player.hasPermission(TrapManager.PERMISSION)) buttons.add(new DialogButton(lang.component(player, "trap.title"),
                (p, r) -> openGuidePage(p, "trap.title", TrapManager.PERMISSION, lang.lines(p, "trap.guide"))));
        if (player.hasPermission("blockintegrations.combos") && comboManager != null)
            buttons.add(new DialogButton(lang.component(player, "guide.combos.label"),
                    lang.component(player, "guide.combos.tooltip"),
                    (p, r) -> openGuidePage(p, "guide.combos.label", "blockintegrations.combos", comboManager.guideLines(p))));
        if (player.hasPermission("blockintegrations.towers") && towerManager != null)
            buttons.add(new DialogButton(lang.component(player, "guide.towers.label"),
                    lang.component(player, "guide.towers.tooltip"),
                    (p, r) -> openGuidePage(p, "guide.towers.label", "blockintegrations.towers", towerManager.towerGuideLines(p))));
        if (player.hasPermission("blockintegrations.furnaces") && furnaceManager != null)
            buttons.add(new DialogButton(lang.component(player, "guide.furnaces.label"),
                    lang.component(player, "guide.furnaces.tooltip"),
                    (p, r) -> openGuidePage(p, "guide.furnaces.label", "blockintegrations.furnaces", furnaceManager.guideLines(p))));
        if (player.hasPermission("blockintegrations.workstations"))
            buttons.add(new DialogButton(lang.component(player, "workstation.guide-title"),
                    (p, r) -> openGuidePage(p, "workstation.guide-title", "blockintegrations.workstations",
                            lang.lines(p, "workstation.guide"))));
        addWebButton(buttons, player, "");
        if (player.hasPermission(ADMIN_PERMISSION))
            buttons.add(new DialogButton(lang.component(player, "guide.settings.label"),
                    lang.component(player, "guide.settings.tooltip"),
                    (p, r) -> openServerSettingsMenu(p)));
        buttons.add(PortableMenuButtons.category(core, MenuCategory.BUILDING));
        buttons.add(PortableMenuButtons.home(core));
        core.dialogs().open(this, player, new DialogRequest(lang.component(player, "guide.title"), body, List.of(),
                buttons, p -> p.isOnline() && mayOpenGuide(p), () -> {}, CLOSE_LABEL, 3));
    }

    private void openGuidePage(Player player, String titleKey, String permission, List<Component> lines) {
        List<DialogBody> body = lines.stream().map(line -> (DialogBody) DialogBody.plainMessage(line)).toList();
        core.dialogs().open(this, player, new DialogRequest(lang.component(player, titleKey), body, List.of(),
                List.of(PortableMenuButtons.back(core, this::openBlockGuideMenu), PortableMenuButtons.home(core)),
                p -> p.isOnline() && p.hasPermission(permission), () -> {}, CLOSE_LABEL, 2));
    }

    private void openServerSettingsMenu(Player player) {
        List<DialogButton> buttons = new ArrayList<>();
        for (String section : SETTINGS_SECTIONS) {
            String key = getName().toLowerCase(java.util.Locale.ROOT) + ":" + section;
            buttons.add(new DialogButton(lang.component(player, "settings.section." + section + ".title"),
                    lang.component(player, "settings.section." + section + ".description"), (p, r) -> {
                if (!core.settings().openSection(p, key)) lang.send(p, "general.settings-unavailable");
            }));
        }
        buttons.add(PortableMenuButtons.back(core, this::openBlockGuideMenu));
        buttons.add(PortableMenuButtons.home(core));
        core.dialogs().open(this, player, new DialogRequest(lang.component(player, "settings.dialog.title"),
                List.of(DialogBody.plainMessage(lang.component(player, "settings.dialog.body"))), List.of(),
                buttons, p -> p.isOnline() && p.hasPermission(ADMIN_PERMISSION), () -> {}, CLOSE_LABEL, 2));
    }

    /** Ids of the admin settings sections registered with Vystorm Core (texts: {@code settings.section.<id>.*}). */
    static final String[] SETTINGS_SECTIONS = {"blocks", "combos", "towers", "furnaces", "traps", "anvils"};

    /** Settings builder whose labels and descriptions are language references ({@code settings.option.<path>.*}). */
    private final class LocalizedSettings {
        private final ConfigSettings settings = ConfigSettings.of(BlockIntegrations.this::getConfig, () -> YamlCheck.saveConfig(BlockIntegrations.this));

        private String label(String path) { return ref("settings.option." + path.replace('.', '-') + ".label"); }
        private String description(String path) { return ref("settings.option." + path.replace('.', '-') + ".description"); }

        LocalizedSettings toggle(String path) {
            settings.toggle(path, label(path), description(path));
            return this;
        }

        LocalizedSettings integer(String path, int min, int max, int step) {
            settings.integer(path, label(path), description(path), min, max, step);
            return this;
        }

        LocalizedSettings decimal(String path, double min, double max, double step) {
            settings.decimal(path, label(path), description(path), min, max, step);
            return this;
        }

        Vystorm.vystorm_core._uiMethods.settings.SettingsSection section(String id, java.util.function.Consumer<Player> reload) {
            return settings.section(id, ref("settings.section." + id + ".header"),
                    ref("settings.section." + id + ".description"), ADMIN_PERMISSION, reload);
        }
    }

    /** {@code {lang:blockintegrations:key}} reference, or the key itself without a bundle (unit tests). */
    private String ref(String key) {
        return lang == null ? key : lang.ref(key);
    }

    private void registerSettings() {
        java.util.function.Consumer<Player> reload = p -> reloadAll();
        core.settings().register(this, new LocalizedSettings()
                .integer("radius", 1, 32, 1)
                .integer("cooldown-ticks", 0, 200, 5)
                .toggle("include-trapped-chests")
                .toggle("messages.show-when-nothing-moved")
                .integer("vacuum.radius", 1, 32, 1)
                .integer("vacuum.cooldown-ticks", 0, 200, 5)
                .toggle("vacuum.messages.show-when-nothing-moved")
                .decimal("launcher.velocity", 0.1, 4.0, 0.05)
                .decimal("launcher.minimum-upward-velocity", 0.0, 2.0, 0.05)
                .integer("launcher.cooldown-ticks", 0, 200, 5)
                .toggle("launcher.play-sound")
                .decimal("booster.velocity", 0.1, 3.0, 0.05)
                .integer("booster.cooldown-ticks", 0, 200, 5)
                .toggle("booster.play-sound")
                .toggle("booster.prevent-ice-melting")
                .decimal("float-launcher.velocity", 0.1, 4.0, 0.05)
                .integer("float-launcher.levitation-duration-ticks", 1, 1200, 10)
                .integer("float-launcher.levitation-amplifier", 0, 10, 1)
                .integer("float-launcher.cooldown-ticks", 0, 400, 5)
                .toggle("float-launcher.play-sound")
                .section(SETTINGS_SECTIONS[0], reload));
        core.settings().register(this, new LocalizedSettings()
                .toggle("combos.enabled")
                .integer("combos.upgrades.maximum-level", 1, 10, 1)
                .decimal("combos.scaling.cooldown-reduction-per-level", 0.0, 0.1, 0.005)
                .integer("combos.scaling.minimum-cooldown-ticks", 0, 100, 1)
                .decimal("combos.heal.amount", 0.1, 20.0, 0.1)
                .integer("combos.heal.aura-radius", 1, 32, 1)
                .integer("combos.heal.pulse-cooldown-ticks", 1, 1200, 5)
                .integer("combos.heal.damage-cooldown-ticks", 0, 1200, 10)
                .integer("combos.heal.minimum-distance", 1, 64, 1)
                .toggle("combos.heal.play-particles")
                .toggle("combos.heal.play-sound")
                .toggle("combos.warp-pad.enabled")
                .integer("combos.warp-pad.base-distance", 8, 10000, 8)
                .integer("combos.warp-pad.distance-per-level", 0, 2000, 8)
                .integer("combos.warp-pad.cooldown-ticks", 0, 1200, 5)
                .integer("combos.warp-pad.max-per-player", 0, 100, 1)
                .toggle("combos.xp-collector.enabled")
                .integer("combos.xp-collector.radius", 1, 16, 1)
                .integer("combos.xp-collector.capacity", 10, 100000, 10)
                .integer("combos.xp-collector.capacity-per-level", 0, 100000, 10)
                .decimal("combos.xp-collector.player-safe-radius", 0.0, 16.0, 0.5)
                .section(SETTINGS_SECTIONS[1], reload));
        core.settings().register(this, new LocalizedSettings()
                .toggle("towers.enabled")
                .integer("towers.upgrades.maximum-level", 1, 10, 1)
                .integer("towers.max-per-player", 0, 100, 1)
                .integer("towers.attack-task-period-ticks", 1, 20, 1)
                .toggle("towers.pause-when-loot-full")
                .toggle("towers.experience.require-nearby-player")
                .decimal("towers.experience.player-radius", 8.0, 128.0, 1.0)
                .toggle("towers.experience.spawner-mobs")
                .toggle("towers.targeting.line-of-sight")
                .toggle("towers.targeting.ignore-named-mobs")
                .toggle("towers.loot.capture-nearby-plugin-drops")
                .toggle("towers.loot.drop-contents-when-broken")
                .section(SETTINGS_SECTIONS[2], reload));
        core.settings().register(this, new LocalizedSettings()
                .toggle("furnaces.enabled")
                .integer("furnaces.upgrades.maximum-level", 1, 10, 1)
                .decimal("furnaces.speed-multiplier-per-level", 0.0, 1.0, 0.01)
                .decimal("furnaces.fuel-efficiency-per-level", 0.0, 2.0, 0.01)
                .decimal("furnaces.bonus-yield-chance-per-level", 0.0, 1.0, 0.005)
                .decimal("furnaces.xp-boost-per-level", 0.0, 2.0, 0.05)
                .section(SETTINGS_SECTIONS[3], reload));
        core.settings().register(this, new LocalizedSettings()
                .toggle("traps.enabled")
                .toggle("traps.default-automatic")
                .integer("traps.default-cooldown-seconds", 10, 15, 5)
                .decimal("traps.throw-power", 0.5, 3.0, 0.1)
                .decimal("traps.sling-power", 0.5, 3.0, 0.1)
                .decimal("traps.sling-lift", 0.2, 2.0, 0.1)
                .decimal("traps.spike-damage", 0, 40, 0.5)
                .section(SETTINGS_SECTIONS[4], reload));
        core.settings().register(this, new LocalizedSettings()
                .toggle("anvils.enabled")
                .integer("anvils.base-xp-cost", 1, 100000, 1)
                .integer("anvils.unbreakable-xp-cost", 1, 1000000, 1)
                .section(SETTINGS_SECTIONS[5], reload));
    }

    /** Re-reads config.yml and applies it to all managers (used by /bi reload and the settings dialogs). */
    String reloadAll() {
        String problem = readConfig(this, true);
        if (problem != null) return problem;
        if (lang != null) {
            lang.reload();
            lang.setLanguage(getConfig().getString("language", "auto"));
        }
        loadSettings();
        if (towerManager != null) towerManager.reloadSettings();
        if (trapManager != null) trapManager.reloadSettings();
        if (anvilManager != null) anvilManager.reloadSettings();
        if (furnaceManager != null) furnaceManager.reloadSettings();
        if (workstationManager != null) workstationManager.reloadSettings();
        if (comboManager != null) comboManager.reloadSettings();
        applySwitches();
        return null;
    }

    /**
     * Start and reload read config.yml through here. A file with a YAML error is never saved over: it is reported and
     * left as it is (jar defaults at start, the last good settings on a reload). Returns the problem or null.
     */
    static String readConfig(org.bukkit.plugin.java.JavaPlugin plugin, boolean reload) {
        String problem = YamlCheck.readConfig(plugin, reload);
        // a pasted config from before 2.44.0 with combos.enabled: false keeps the four combos it switched off
        if (problem == null && Feature.migrateComboGate(plugin.getConfig())) plugin.saveConfig();
        return problem;
    }

    /**
     * After a reload: when a switch changed, online players get their command list again (the /bi tree asks the
     * switches at every completion, so it is not registered again); the web page is registered or removed.
     */
    private void applySwitches() {
        if (commandSwitches != null && !commandSwitches.equals(switches)) {
            commandSwitches = switches;
            try { core.command().refresh(); }
            catch (RuntimeException failure) { DebugMessages.high("[BlockIntegrations] command lists could not be sent again: " + failure); }
        }
        if (commandSwitches == null) return;
        if (on(Feature.WEB) && web == null) registerWeb();
        else if (!on(Feature.WEB) && web != null) { web.shutdown(); web = null; }
    }

    /** Registers /blockintegrations; the subcommands of switched-off features are hidden from help and tab completion. */
    private void registerCommands() {
        commandSwitches = switches == null ? Feature.Switches.without() : switches;
        core.command().register(this, lang, BlockCommands.spec(this));
    }

    /** The namespace {@code blocks}: combos, warp pads, towers and furnaces (server thread; empty while switched off). */
    void registerPlaceholders(Vystorm.vystorm_core._advancedMethods.placeholderHandler.PlaceholderInterface registry) {
        BlocksPlaceholders.register(registry, this, new BlocksPlaceholders.Source() {
            @Override public boolean on(Feature feature) { return BlockIntegrations.this.on(feature); }
            @Override public ComboManager combos() { return comboManager; }
            @Override public TowerManager towers() { return towerManager; }
            @Override public FurnaceManager furnaces() { return furnaceManager; }
        }, key -> lang == null ? key : lang.ref(key));
    }

    static boolean normalMenuClick(org.bukkit.event.inventory.InventoryClickEvent event, Player player) {
        return !(event instanceof org.bukkit.event.inventory.InventoryCreativeEvent)
                && (event.getClick() == org.bukkit.event.inventory.ClickType.LEFT
                || event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT);
    }

    // ---- /blockintegrations (tree in BlockCommands.spec, run by Vystorm Core) -----------------------------------

    /** {@code /bi} without arguments: players without admin rights get the guide menu, everyone else the status. */
    void rootCommand(CommandSender sender) {
        if (sender instanceof Player player && !player.hasPermission(ADMIN_PERMISSION)) menuCommand(player);
        else statusCommand(sender);
    }

    void menuCommand(Player player) {
        if (off(player, Feature.MENU)) return;
        if (mayOpenGuide(player)) openBlockGuideMenu(player);
        else lang.send(player, "general.no-permission");
    }

    void webCommand(Player player, boolean guide) {
        if (off(player, Feature.WEB)) return;
        openWeb(player, guide ? "guide" : "");
    }

    void statusCommand(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            lang.send(sender, "general.no-permission");
            return;
        }
        lang.send(sender, "status.line",
                "towers", towerManager == null ? 0 : towerManager.towerCount(),
                "attacks", towerManager == null ? 0 : towerManager.attacksFired(),
                "cycles", towerManager == null ? 0 : towerManager.attackCycles(),
                "tower_max", towerManager == null ? 0 : towerManager.maximumUpgradeLevel(),
                "furnace_max", furnaceManager == null ? 0 : furnaceManager.maximumLevel(),
                "combos", comboManager == null ? 0 : comboManager.comboCount(),
                "combo_max", comboManager == null ? 0 : comboManager.maximumLevel());
        if (furnaceManager != null) lang.send(sender, "status.furnaces", "furnaces", furnaceManager.furnaceCount());
        if (sender instanceof Player) core.compat().platform().sendMessage(sender, lang.component(sender, "status.next")
                .append(core.message().chat().command(lang.plain(sender, "status.towers-button"),
                        lang.plain(sender, "status.towers-hover"), "/blockintegrations towers list"))
                .append(Component.space())
                .append(core.message().chat().button(lang.plain(sender, "status.settings-button"),
                        lang.plain(sender, "status.settings-hover"), p -> {
                    if (p.hasPermission(ADMIN_PERMISSION)) openServerSettingsMenu(p);
                    else lang.send(p, "general.no-permission");
                })));
    }

    void trapsCommand(CommandSender sender) {
        if (off(sender, Feature.TRAPS)) return;
        lang.lines(sender, "trap.guide").forEach(line -> core.compat().platform().sendMessage(sender, line));
    }

    void myCombosCommand(Player player) {
        if (off(player, Feature.BLOCKS)) return;
        if (comboManager != null) comboManager.sendOwnCombos(player);
    }

    void combosCommand(CommandSender sender) {
        if (off(sender, Feature.BLOCKS)) return;
        if (comboManager != null) comboManager.sendGuide(sender);
    }

    /** {@code args} as the old executor saw them: {@code towers [list [player]|near|remove]}. */
    void towersCommand(CommandSender sender, String[] args) {
        if (off(sender, Feature.TOWERS)) return;
        if (towerManager != null && !towerManager.towerCommand(sender, args)) towerManager.sendTowerGuide(sender);
    }

    /** {@code args}: {@code warppads [list|share PLAYER|unshare PLAYER]}. */
    void warpPadsCommand(Player player, String[] args) {
        if (off(player, Feature.WARP_PADS)) return;
        if (comboManager != null) comboManager.warpCommand(player, args);
    }

    void workstationsCommand(CommandSender sender) {
        if (!on(Feature.BREWING) && off(sender, Feature.CAMPFIRES)) return;
        lang.lines(sender, "workstation.guide").forEach(line -> core.compat().platform().sendMessage(sender, line));
    }

    void furnacesCommand(CommandSender sender) {
        if (off(sender, Feature.FURNACES)) return;
        if (furnaceManager != null) furnaceManager.sendGuide(sender);
    }

    void reloadCommand(CommandSender sender) {
        String problem = reloadAll();
        if (problem != null) lang.send(sender, "config-error", "problem", problem);
        else lang.send(sender, "general.reloaded");
    }


    private void loadSettings() {
        switches = Feature.Switches.read(getConfig());
        sortRadius = boundedRadius(getConfig().getInt("radius", 10));
        cooldownTicks = Math.max(0L, getConfig().getLong("cooldown-ticks", 20L));
        includeTrappedChests = getConfig().getBoolean("include-trapped-chests", true);
        showNothingMessage = getConfig().getBoolean("messages.show-when-nothing-moved", true);

        vacuumRadius = boundedRadius(getConfig().getInt("vacuum.radius", 10));
        vacuumCooldownTicks = Math.max(0L, getConfig().getLong("vacuum.cooldown-ticks", 20L));
        harvestRadius = Math.max(1, Math.min(16, getConfig().getInt("combos.harvester.radius", 4)));
        harvestCooldownTicks = Math.max(20L, Math.min(12000L, getConfig().getLong("combos.harvester.cooldown-ticks", 200L)));
        harvestParticles = getConfig().getBoolean("combos.harvester.play-particles", true);
        harvestPauseWhenFull = getConfig().getBoolean("combos.harvester.pause-when-full", true);
        warpPadCooldownTicks = Math.max(0L, Math.min(12000L, getConfig().getLong("combos.warp-pad.cooldown-ticks", 100L)));
        xpCollectorRadius = Math.max(1, Math.min(16, getConfig().getInt("combos.xp-collector.radius", 6)));
        showVacuumNothingMessage = getConfig().getBoolean("vacuum.messages.show-when-nothing-moved", true);

        launchVelocity = boundedDouble(getConfig().getDouble("launcher.velocity", 1.70D), 0.1D, 4.0D);
        launchMinimumUpwardVelocity = boundedDouble(
                getConfig().getDouble("launcher.minimum-upward-velocity", 0.65D), 0.0D, 2.0D);
        launchCooldownTicks = Math.max(0L, getConfig().getLong("launcher.cooldown-ticks", 20L));
        launchSound = getConfig().getBoolean("launcher.play-sound", true);

        boosterVelocity = boundedDouble(getConfig().getDouble("booster.velocity", 1.15D), 0.1D, 3.0D);
        boosterCooldownTicks = Math.max(0L, getConfig().getLong("booster.cooldown-ticks", 10L));
        boosterSound = getConfig().getBoolean("booster.play-sound", false);
        protectBoosterIce = getConfig().getBoolean("booster.prevent-ice-melting", true);

        floatLaunchVelocity = boundedDouble(
                getConfig().getDouble("float-launcher.velocity", 1.25D), 0.1D, 4.0D);
        floatEffectDurationTicks = Math.max(1, Math.min(1200,
                getConfig().getInt("float-launcher.levitation-duration-ticks", 100)));
        floatEffectAmplifier = Math.max(0, Math.min(10,
                getConfig().getInt("float-launcher.levitation-amplifier", 0)));
        floatLaunchCooldownTicks = Math.max(0L,
                getConfig().getLong("float-launcher.cooldown-ticks", 60L));
        floatLaunchSound = getConfig().getBoolean("float-launcher.play-sound", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!player.isOnGround()) {
            boolean firstAirborneMove = airbornePlayers.add(playerId);
            if (firstAirborneMove && event.getTo() != null
                    && event.getTo().getY() > event.getFrom().getY() + 0.01D) {
                if (on(Feature.LAUNCHER)) tryLaunch(player, event.getFrom());
            }
            return;
        }

        if (on(Feature.BOOSTER)) tryGroundBooster(event);

        // Walking over the plate does not count. A player must have been in the air.
        if (!airbornePlayers.remove(playerId)) return;
        if (!player.hasPermission("blockintegrations.use")) return;
        if (on(Feature.FLOAT_LIFT) && tryFloatLaunch(player)) return;

        Block pressurePlate = findPressurePlateAtFeet(player.getLocation(), Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        boolean vacuum = false;
        if (pressurePlate == null) {
            pressurePlate = findPressurePlateAtFeet(player.getLocation(), Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
            vacuum = pressurePlate != null;
        }
        if (pressurePlate == null) return;

        if (!on(vacuum ? Feature.VACUUM : Feature.SORTER)) return;
        Block copperBlock = pressurePlate.getRelative(0, -1, 0);
        if (!CopperMaterials.isFullBuildingBlock(copperBlock.getType())) return;

        long currentTick = player.getWorld().getGameTime();
        Long previousTick = lastActivationTick.get(playerId);
        ComboType comboType = vacuum ? ComboType.VACUUM : ComboType.SORTER;
        long baseCooldown = vacuum ? vacuumCooldownTicks : cooldownTicks;
        long requiredCooldown = comboManager == null ? baseCooldown
                : comboManager.cooldown(pressurePlate, comboType, baseCooldown);
        if (previousTick != null && currentTick - previousTick < requiredCooldown) return;
        lastActivationTick.put(playerId, currentTick);

        if (vacuum) {
            int effectiveRadius = comboManager == null ? vacuumRadius
                    : comboManager.range(pressurePlate, ComboType.VACUUM, vacuumRadius);
            vacuumDroppedItems(player, copperBlock, effectiveRadius);
        } else {
            int effectiveRadius = comboManager == null ? sortRadius
                    : comboManager.range(pressurePlate, ComboType.SORTER, sortRadius);
            sortInventory(player, copperBlock, effectiveRadius);
        }
    }

    private void tryLaunch(Player player, Location jumpFrom) {
        if (!player.hasPermission("blockintegrations.use")) return;

        Block slimeBlock = SlimeFieldStructure.controlFor(findSupportingBlock(jumpFrom, Material.SLIME_BLOCK));
        if (slimeBlock == null) return;

        UUID playerId = player.getUniqueId();
        long currentTick = player.getWorld().getGameTime();
        long effectiveCooldown = comboManager == null ? launchCooldownTicks
                : comboManager.cooldown(slimeBlock, ComboType.LAUNCHER, launchCooldownTicks);
        Long previousTick = lastLaunchTick.get(playerId);
        if (previousTick != null && currentTick - previousTick < effectiveCooldown) return;
        lastLaunchTick.put(playerId, currentTick);

        double effectiveVelocity = comboManager == null ? launchVelocity
                : comboManager.power(slimeBlock, ComboType.LAUNCHER, launchVelocity);
        Vector direction = player.getEyeLocation().getDirection().normalize().multiply(effectiveVelocity);
        direction.setY(Math.max(launchMinimumUpwardVelocity, direction.getY()));
        player.setFallDistance(0.0F);
        player.setVelocity(direction);
        if (launchSound)
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8F, 1.15F);
    }

    private void tryGroundBooster(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isSprinting() || event.getTo() == null) return;

        Block iceBlock = findSupportingBlock(event.getTo(), BOOSTER_ICE_BLOCKS);
        if (iceBlock == null || iceBlock.getRelative(0, -1, 0).getType() != Material.REDSTONE_BLOCK) return;
        if (!player.hasPermission("blockintegrations.use")) return;

        UUID playerId = player.getUniqueId();
        long currentTick = player.getWorld().getGameTime();
        long effectiveCooldown = comboManager == null ? boosterCooldownTicks
                : comboManager.cooldown(iceBlock, ComboType.BOOSTER, boosterCooldownTicks);
        Long previousTick = lastBoosterTick.get(playerId);
        if (previousTick != null && currentTick - previousTick < effectiveCooldown) return;

        Vector movement = event.getTo().toVector().subtract(event.getFrom().toVector());
        movement.setY(0.0D);
        if (movement.lengthSquared() < 0.000001D) {
            movement = player.getLocation().getDirection();
            movement.setY(0.0D);
        }
        if (movement.lengthSquared() < 0.000001D) return;

        lastBoosterTick.put(playerId, currentTick);
        double effectiveVelocity = comboManager == null ? boosterVelocity
                : comboManager.power(iceBlock, ComboType.BOOSTER, boosterVelocity);
        Vector boost = movement.normalize().multiply(effectiveVelocity);
        boost.setY(player.getVelocity().getY());
        player.setVelocity(boost);
        if (boosterSound)
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.45F, 1.7F);
    }

    private boolean tryFloatLaunch(Player player) {
        Block topBlock = findSupportingBlock(player.getLocation(), Material.WHITE_TERRACOTTA);
        if (topBlock == null || !FloatLauncherStructure.matches(topBlock)) return false;

        UUID playerId = player.getUniqueId();
        long currentTick = player.getWorld().getGameTime();
        long effectiveCooldown = comboManager == null ? floatLaunchCooldownTicks
                : comboManager.cooldown(topBlock, ComboType.FLOAT_LAUNCHER,
                floatLaunchCooldownTicks);
        Long previousTick = lastFloatLaunchTick.get(playerId);
        if (previousTick != null && currentTick - previousTick < effectiveCooldown) return true;
        lastFloatLaunchTick.put(playerId, currentTick);

        int effectiveDuration = comboManager == null ? floatEffectDurationTicks
                : comboManager.duration(topBlock, ComboType.FLOAT_LAUNCHER,
                floatEffectDurationTicks);
        double effectiveVelocity = comboManager == null ? floatLaunchVelocity
                : comboManager.power(topBlock, ComboType.FLOAT_LAUNCHER,
                floatLaunchVelocity);
        player.setFallDistance(0.0F);
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,
                effectiveDuration, floatEffectAmplifier, false, true, true), true);
        player.setVelocity(new Vector(0.0D, effectiveVelocity, 0.0D));
        if (floatLaunchSound) {
            player.getWorld().playSound(player.getLocation(),
                    Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8F, 1.35F);
        }
        return true;
    }

    /* Stage PROTECT, registered in onEnable */
    public void onBlockFade(BlockFadeEvent event) {
        if (protectBoosterIce && on(Feature.BOOSTER) && event.getBlock().getType() == Material.ICE
                && event.getBlock().getRelative(0, -1, 0).getType() == Material.REDSTONE_BLOCK) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        airbornePlayers.remove(playerId);
        lastActivationTick.remove(playerId);
        lastLaunchTick.remove(playerId);
        lastBoosterTick.remove(playerId);
        lastFloatLaunchTick.remove(playerId);
    }

    private Block findPressurePlateAtFeet(Location location, Material material) {
        Block feet = location.getBlock();
        if (feet.getType() == material) return feet;

        Block justBelowFeet = location.clone().subtract(0.0, 0.125, 0.0).getBlock();
        if (justBelowFeet.getType() == material) return justBelowFeet;

        Block blockBelow = feet.getRelative(0, -1, 0);
        return blockBelow.getType() == material ? blockBelow : null;
    }

    private Block findSupportingBlock(Location location, Material material) {
        Block block = location.getBlock();
        for (int down = 0; down <= 2; down++) {
            Block candidate = block.getRelative(0, -down, 0);
            if (candidate.getType() == material) return candidate;
        }
        return null;
    }

    private Block findSupportingBlock(Location location, Set<Material> materials) {
        Block block = location.getBlock();
        for (int down = 0; down <= 2; down++) {
            Block candidate = block.getRelative(0, -down, 0);
            if (materials.contains(candidate.getType())) return candidate;
        }
        return null;
    }

    /** The player's chest order, or none while sort-locks.enabled is off. */
    private ChestPreferences.Preferences chestOrder(Player player) {
        return on(Feature.SORT_LOCKS) ? chestPreferences.read(player.getPersistentDataContainer()) : new ChestPreferences.Preferences(List.of(), null);
    }

    private void sortInventory(Player player, Block copperBlock, int effectiveRadius) {
        PlayerInventory playerInventory = player.getInventory();
        // sort-locks.enabled off: locks and chest order stay stored but are not applied
        boolean locks = on(Feature.SORT_LOCKS);
        Set<Integer> lockedSlots = locks ? sortSlotLocks.read(player.getPersistentDataContainer()) : Set.of();
        Set<Material> lockedItems = locks ? sortItemLocks.read(player.getPersistentDataContainer()) : Set.of();
        if (!hasItemsToSort(playerInventory, lockedSlots, lockedItems)) {
            sendNothingMessage(player);
            return;
        }

        List<ChestTarget> targets = orderChestTargets(findNearbyChests(player, copperBlock, effectiveRadius),
                chestOrder(player));
        if (targets.isEmpty()) {
            sendNothingMessage(player);
            return;
        }

        int movedItems = 0;
        Set<String> usedChests = new HashSet<>();

        for (int slot = FIRST_MAIN_INVENTORY_SLOT; slot <= LAST_MAIN_INVENTORY_SLOT; slot++) {
            if (lockedSlots.contains(slot)) continue;
            ItemStack original = playerInventory.getItem(slot);
            if (!SortItemLocks.canSort(original, lockedItems)) continue;

            ItemStack remaining = original.clone();
            for (ChestTarget target : targets) {
                if (!ChestAccess.allowed(player, target.inventory()) || !containsSimilarItem(target.inventory(), remaining)) continue;

                int before = remaining.getAmount();
                Map<Integer, ItemStack> leftovers = target.inventory().addItem(remaining.clone());
                remaining = combineLeftovers(leftovers);
                int after = isEmpty(remaining) ? 0 : remaining.getAmount();

                if (after < before) {
                    movedItems += before - after;
                    usedChests.add(target.key());
                }
                if (after == 0) break;
            }

            playerInventory.setItem(slot, isEmpty(remaining) ? null : remaining);
        }

        if (movedItems == 0) {
            sendNothingMessage(player);
            return;
        }

        lang.send(player, "sorter.success", "moved", movedItems, "chests", usedChests.size());
    }

    private void vacuumDroppedItems(Player player, Block copperBlock, int effectiveRadius) {
        Location center = copperBlock.getLocation().add(0.5D, 1.0D, 0.5D);
        double radiusSquared = (double) effectiveRadius * effectiveRadius;
        List<Item> droppedItems = new ArrayList<>();
        for (org.bukkit.entity.Entity entity : copperBlock.getWorld().getNearbyEntities(
                center, effectiveRadius, effectiveRadius, effectiveRadius)) {
            if (entity instanceof Item item && item.isValid()
                    && item.getLocation().distanceSquared(center) <= radiusSquared) {
                droppedItems.add(item);
            }
        }
        droppedItems.sort(Comparator.comparingDouble(item -> item.getLocation().distanceSquared(center)));

        if (droppedItems.isEmpty()) {
            sendVacuumNothingMessage(player);
            return;
        }

        List<ChestTarget> targets = orderChestTargets(findNearbyChests(player, copperBlock, effectiveRadius),
                chestOrder(player));
        boolean usePlayerInventory = targets.isEmpty();
        int movedItems = 0;
        Set<String> usedChests = new HashSet<>();

        for (Item droppedItem : droppedItems) {
            ItemStack original = droppedItem.getItemStack();
            if (isEmpty(original)) continue;

            ItemStack remaining = original.clone();
            if (usePlayerInventory) {
                remaining = addToMainInventory(player.getInventory(), remaining);
            } else {
                for (ChestTarget target : targets) {
                    if (!ChestAccess.allowed(player, target.inventory()) || !containsSimilarItem(target.inventory(), remaining)) continue;

                    int before = remaining.getAmount();
                    remaining = combineLeftovers(target.inventory().addItem(remaining.clone()));
                    int after = isEmpty(remaining) ? 0 : remaining.getAmount();
                    if (after < before) usedChests.add(target.key());
                    if (after == 0) break;
                }
            }

            int remainingAmount = isEmpty(remaining) ? 0 : remaining.getAmount();
            movedItems += original.getAmount() - remainingAmount;
            if (remainingAmount == 0) {
                droppedItem.remove();
            } else {
                droppedItem.setItemStack(remaining);
            }
        }

        if (movedItems == 0) {
            sendVacuumNothingMessage(player);
            return;
        }

        lang.send(player, usePlayerInventory ? "vacuum.inventory" : "vacuum.chests",
                "moved", movedItems, "chests", usedChests.size());
    }

    private ItemStack addToMainInventory(PlayerInventory inventory, ItemStack original) {
        ItemStack remaining = original.clone();

        for (int slot = FIRST_MAIN_INVENTORY_SLOT; slot <= LAST_MAIN_INVENTORY_SLOT; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (isEmpty(existing) || !existing.isSimilar(remaining)) continue;

            int maximum = Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize());
            int free = Math.max(0, maximum - existing.getAmount());
            if (free == 0) continue;

            int moved = Math.min(free, remaining.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            inventory.setItem(slot, existing);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() == 0) return null;
        }

        for (int slot = FIRST_MAIN_INVENTORY_SLOT; slot <= LAST_MAIN_INVENTORY_SLOT; slot++) {
            if (!isEmpty(inventory.getItem(slot))) continue;

            int maximum = Math.min(remaining.getMaxStackSize(), inventory.getMaxStackSize());
            int moved = Math.min(maximum, remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            inventory.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() == 0) return null;
        }

        return remaining;
    }

    private boolean hasItemsToSort(PlayerInventory inventory, Set<Integer> lockedSlots, Set<Material> lockedItems) {
        for (int slot = FIRST_MAIN_INVENTORY_SLOT; slot <= LAST_MAIN_INVENTORY_SLOT; slot++) {
            if (!lockedSlots.contains(slot) && SortItemLocks.canSort(inventory.getItem(slot), lockedItems)) return true;
        }
        return false;
    }

    void sortLockCommand(Player player, String[] args) {
        if (off(player, Feature.SORT_LOCKS)) return;
        if (args.length == 2 && args[1].equalsIgnoreCase("item")) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (isEmpty(held)) {
                lang.send(player, "sort-locks.hold-item");
                return;
            }
            boolean locked = sortItemLocks.toggle(player.getPersistentDataContainer(), held.getType());
            core.compat().platform().sendMessage(player, lang.component(player, locked ? "sort-locks.item-locked" : "sort-locks.item-unlocked")
                    .append(Component.translatable(held.getType().getTranslationKey())));
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("items")) {
            Set<Material> items = sortItemLocks.read(player.getPersistentDataContainer());
            lang.send(player, items.isEmpty() ? "sort-locks.items-empty" : "sort-locks.items-title");
            for (Material item : items) core.compat().platform().sendMessage(player, Component.translatable(item.getTranslationKey()));
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("clearitems")) {
            sortItemLocks.clear(player.getPersistentDataContainer());
            lang.send(player, "sort-locks.items-cleared");
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            List<Integer> slots = new ArrayList<>(sortSlotLocks.read(player.getPersistentDataContainer()));
            if (slots.isEmpty()) {
                lang.send(player, "sort-locks.list-empty");
            } else {
                lang.send(player, "sort-locks.list", "slots", slots.stream()
                        .map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")));
            }
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("clear")) {
            sortSlotLocks.clear(player.getPersistentDataContainer());
            lang.send(player, "sort-locks.cleared");
            return;
        }
        if (args.length == 2) {
            try {
                int slot = Integer.parseInt(args[1]);
                boolean locked = sortSlotLocks.toggle(player.getPersistentDataContainer(), slot);
                lang.send(player, locked ? "sort-locks.locked" : "sort-locks.unlocked", "slot", slot);
            } catch (IllegalArgumentException invalid) {
                lang.send(player, "sort-locks.invalid");
            }
            return;
        }
        lang.send(player, "sort-locks.usage");
    }

    private static Block lookedAtLoadedBlock(Player player) {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector direction = eye.getDirection();
        World world = eye.getWorld();
        for (int step = 0; step <= 30; step++) {
            Location sample = eye.clone().add(direction.clone().multiply(step * 0.2));
            int x = sample.getBlockX(), y = sample.getBlockY(), z = sample.getBlockZ();
            if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) return null;
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isAir()) return block;
        }
        return null;
    }

    void chestPriorityCommand(Player player, String[] args) {
        if (off(player, Feature.SORT_LOCKS)) return;
        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            ChestPreferences.Preferences prefs = chestPreferences.read(player.getPersistentDataContainer());
            String preferred = prefs.preferred().stream().map(ChestPreferences.Position::key)
                    .collect(java.util.stream.Collectors.joining(", "));
            lang.send(player, preferred.isEmpty() ? "chest-priority.list-empty" : "chest-priority.list",
                    "chests", preferred, "overflow", prefs.overflow() == null ? "-" : prefs.overflow().key());
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("clear")) {
            chestPreferences.write(player.getPersistentDataContainer(), ChestPreferences.Preferences.empty());
            lang.send(player, "chest-priority.cleared");
            return;
        }
        if (args.length != 2 || !(args[1].equalsIgnoreCase("add")
                || args[1].equalsIgnoreCase("overflow") || args[1].equalsIgnoreCase("remove"))) {
            lang.send(player, "chest-priority.usage");
            return;
        }
        Block target = lookedAtLoadedBlock(player);
        if (target == null || !target.getWorld().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)
                || !isAllowedChest(target.getType()) || !(target.getState() instanceof Chest)) {
            lang.send(player, "chest-priority.look-at-chest");
            return;
        }
        ChestPreferences.Position position = new ChestPreferences.Position(target.getWorld().getUID(),
                target.getX(), target.getY(), target.getZ());
        ChestPreferences.Preferences current = chestPreferences.read(player.getPersistentDataContainer());
        try {
            ChestPreferences.Preferences updated;
            if (args[1].equalsIgnoreCase("add")) updated = ChestPreferences.add(current, position);
            else if (args[1].equalsIgnoreCase("overflow")) updated = ChestPreferences.setOverflow(current, position);
            else updated = ChestPreferences.remove(current, position);
            chestPreferences.write(player.getPersistentDataContainer(), updated);
            lang.send(player, "chest-priority." + args[1].toLowerCase(java.util.Locale.ROOT),
                    "chest", position.key());
        } catch (IllegalArgumentException limit) {
            lang.send(player, "chest-priority.limit");
        }
    }

    private List<ChestTarget> orderChestTargets(List<ChestTarget> targets, ChestPreferences.Preferences preferences) {
        targets.sort(Comparator.comparingInt((ChestTarget target) -> ChestPreferences.rank(target.key(), preferences))
                .thenComparingInt(ChestTarget::distanceSquared).thenComparing(ChestTarget::key));
        return targets;
    }

    private List<ChestTarget> findNearbyChests(Player player, Block origin, int searchRadius) {
        World world = origin.getWorld();
        int radiusSquared = searchRadius * searchRadius;
        Map<String, ChestTarget> uniqueTargets = new HashMap<>();

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                int blockY = origin.getY() + y;
                if (blockY < world.getMinHeight() || blockY >= world.getMaxHeight()) continue;

                for (int z = -searchRadius; z <= searchRadius; z++) {
                    int distanceSquared = x * x + y * y + z * z;
                    if (distanceSquared > radiusSquared) continue;

                    int blockX = origin.getX() + x;
                    int blockZ = origin.getZ() + z;
                    if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) continue;

                    Block block = world.getBlockAt(blockX, blockY, blockZ);
                    if (!isAllowedChest(block.getType()) || !(block.getState() instanceof Chest chest)) continue;

                    // A double chest may resolve its other half; do not let this access load a neighboring chunk.
                    if (!world.isChunkLoaded((blockX - 1) >> 4, blockZ >> 4)
                            || !world.isChunkLoaded((blockX + 1) >> 4, blockZ >> 4)
                            || !world.isChunkLoaded(blockX >> 4, (blockZ - 1) >> 4)
                            || !world.isChunkLoaded(blockX >> 4, (blockZ + 1) >> 4)) continue;
                    Inventory inventory = chest.getInventory();
                    if (!ChestAccess.allowed(player, inventory)) continue;
                    String key = inventoryKey(inventory, block);
                    ChestTarget candidate = new ChestTarget(inventory, distanceSquared, key);
                    uniqueTargets.merge(key, candidate,
                            (oldValue, newValue) -> oldValue.distanceSquared() <= newValue.distanceSquared()
                                    ? oldValue : newValue);
                }
            }
        }

        List<ChestTarget> targets = new ArrayList<>(uniqueTargets.values());
        targets.sort(Comparator.comparingInt(ChestTarget::distanceSquared).thenComparing(ChestTarget::key));
        return targets;
    }

    private boolean isAllowedChest(Material material) {
        return material == Material.CHEST || (includeTrappedChests && material == Material.TRAPPED_CHEST);
    }

    private String inventoryKey(Inventory inventory, Block fallbackBlock) {
        if (inventory instanceof DoubleChestInventory doubleChest) {
            Location left = doubleChest.getLeftSide().getLocation();
            Location right = doubleChest.getRightSide().getLocation();
            String leftKey = locationKey(left, fallbackBlock);
            String rightKey = locationKey(right, fallbackBlock);
            return leftKey.compareTo(rightKey) <= 0 ? leftKey + "|" + rightKey : rightKey + "|" + leftKey;
        }
        return locationKey(inventory.getLocation(), fallbackBlock);
    }

    private String locationKey(Location location, Block fallbackBlock) {
        if (location == null || location.getWorld() == null) {
            return fallbackBlock.getWorld().getUID() + ":" + fallbackBlock.getX() + ":"
                    + fallbackBlock.getY() + ":" + fallbackBlock.getZ();
        }
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }

    private boolean containsSimilarItem(Inventory inventory, ItemStack candidate) {
        for (ItemStack existing : inventory.getStorageContents()) {
            if (!isEmpty(existing) && existing.isSimilar(candidate)) return true;
        }
        return false;
    }

    private ItemStack combineLeftovers(Map<Integer, ItemStack> leftovers) {
        if (leftovers.isEmpty()) return null;
        ItemStack combined = null;
        int total = 0;
        for (ItemStack leftover : leftovers.values()) {
            if (isEmpty(leftover)) continue;
            if (combined == null) combined = leftover.clone();
            total += leftover.getAmount();
        }
        if (combined == null || total == 0) return null;
        combined.setAmount(total);
        return combined;
    }

    private void sendNothingMessage(Player player) {
        if (showNothingMessage) lang.send(player, "sorter.nothing");
    }

    private void sendVacuumNothingMessage(Player player) {
        if (showVacuumNothingMessage) lang.send(player, "vacuum.nothing");
    }

    private int boundedRadius(int configured) {
        return Math.max(1, Math.min(32, configured));
    }

    private double boundedDouble(double configured, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, configured));
    }

    int comboBaseRange(ComboType type) {
        return switch (type) {
            case SORTER -> sortRadius;
            case VACUUM -> vacuumRadius;
            case HARVESTER -> harvestRadius;
            case XP_COLLECTOR -> xpCollectorRadius;
            default -> 1;
        };
    }

    double comboBasePower(ComboType type) {
        return switch (type) {
            case LAUNCHER -> launchVelocity;
            case BOOSTER -> boosterVelocity;
            case FLOAT_LAUNCHER -> floatLaunchVelocity;
            case HEAL -> 0.1D;
            default -> 0.1D;
        };
    }

    int comboBaseDuration(ComboType type) {
        return type == ComboType.FLOAT_LAUNCHER ? floatEffectDurationTicks : 1;
    }

    long comboBaseCooldown(ComboType type) {
        return switch (type) {
            case SORTER -> cooldownTicks;
            case VACUUM -> vacuumCooldownTicks;
            case LAUNCHER -> launchCooldownTicks;
            case BOOSTER -> boosterCooldownTicks;
            case FLOAT_LAUNCHER -> floatLaunchCooldownTicks;
            case HEAL -> 1L;
            case HARVESTER -> harvestCooldownTicks;
            case WARP_PAD -> warpPadCooldownTicks;
            case XP_COLLECTOR -> 1L;
        };
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private record ChestTarget(Inventory inventory, int distanceSquared, String key) {
    }
}
