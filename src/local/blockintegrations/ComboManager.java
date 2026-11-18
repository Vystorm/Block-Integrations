package local.blockintegrations;

import Vystorm.vystorm_core.schedule.TaskGroup;
import Vystorm.vystorm_core.compat.dialog.DialogButton;
import Vystorm.vystorm_core.compat.dialog.DialogRequest;

import Vystorm.vystorm_core._parsersDebuggerEtc.debugger.DebugMessages;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import Vystorm.vystorm_core.compat.dialog.DialogBody;
import net.kyori.adventure.text.Component;
import Vystorm.vystorm_core.lang.Lang;

final class ComboManager implements Listener {
    void registerStorageDiagnostics() { store.registerDiagnostics(plugin.core().admin().diagnostics(), plugin); }

    private final BlockIntegrations plugin;
    /** {@code combos.json} as Core VersionedStore (atomic writes, schema envelope). */
    private final StateFile<CombosState> store;
    private final Map<BlockKey, Combo> combos = new HashMap<>();
    private final Map<ComboUpgrade, List<Integer>> upgradeCosts =
            new EnumMap<>(ComboUpgrade.class);
    private final Map<ComboType, Double> powerPerLevel = new EnumMap<>(ComboType.class);
    private final Map<UUID, Long> lastDamageTick = new HashMap<>();
    private final Map<UUID, Long> lastHealTick = new HashMap<>();

    /** combos.enabled: the upgrade menus and the effect of bought upgrades (stored levels stay). */
    private boolean enabled;
    /** blocks.enabled: combos are recognised, tracked and keep their levels on the item when broken. */
    private boolean tracking = true;
    private boolean healEnabled = true;
    private boolean harvesterEnabled = true;
    private int maximumLevel;
    private int rangeBlocksPerLevel;
    private int durationTicksPerLevel;
    private double cooldownReductionPerLevel;
    private long minimumCooldownTicks;
    private double healAmount;
    private int healAuraRadius;
    private long healPulseCooldownTicks;
    private long healDamageCooldownTicks;
    private int healMinimumDistance;
    private long healTaskPeriodTicks;
    private boolean healParticles;
    private boolean healSound;
    /** Core task group of this manager (heal aura, deferred block checks, batched saves); closed on shutdown. */
    private final TaskGroup tasks;
    private BukkitTask healTask;
    private BukkitTask harvestTask;
    private final Map<BlockKey, Long> nextHarvest = new HashMap<>();
    private boolean saveQueued;
    private BukkitTask xpTask;
    /** Places where players recently died and dropped XP: collectors leave those orbs alone. */
    private final DeathOrbGuard deathOrbs = new DeathOrbGuard();
    /** XP collectors change often: their saves are batched with a delay. */
    private boolean xpSaveQueued;
    /** Warp pad guests per owner: who may travel on that owner's pads. */
    private final Map<UUID, java.util.Set<UUID>> warpGuests = new HashMap<>();
    /** Server tick from which a player may warp again / sneak-open the travel dialog again. */
    private final Map<UUID, Long> nextWarp = new HashMap<>();
    private final Map<UUID, Long> nextWarpPrompt = new HashMap<>();
    private boolean warpPadsEnabled;
    private int warpBaseDistance;
    private int warpDistancePerLevel;
    private int warpMaxPerPlayer;
    private boolean warpParticles;
    private boolean xpCollectorsEnabled;
    private int xpBaseCapacity;
    private int xpCapacityPerLevel;
    private double xpPlayerSafeRadius;
    private boolean xpParticles;

    ComboManager(BlockIntegrations plugin) {
        this.plugin = plugin;
        this.tasks = plugin.core().tasks().of(plugin, "BlockIntegrations/ComboManager");
        this.store = new StateFile<>(new File(plugin.getDataFolder(), "combos.json").toPath(), "BlockIntegrations/ComboManager",
                CombosState.class, STATE_SCHEMA, Map.of(), CombosState::empty, plugin.core().data().files());
    }

    /**
     * The other shared events go through Core's stages too: the sneak click on a combination is the feature acting on
     * an allowed click (ACT); damage and death are only noted (OBSERVE). None of the handlers sees a cancelled event.
     */
    void handleSharedEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, PlayerInteractEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.ACT, this::onComboInteract);
        stages.handle(plugin, EntityDamageEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onPlayerDamaged);
        stages.handle(plugin, org.bukkit.event.entity.PlayerDeathEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onPlayerDeath);
    }
    void start() {
        loadSettings();
        loadCombos();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        handleBlockEvents(plugin.core().priority().stages());
        handleSharedEvents(plugin.core().priority().stages());
        startHealTask();
        startHarvestTask();
        startXpTask();
    }

    void shutdown() {
        tasks.close();
        healTask = null;
        saveCombos();
    }

    void reloadSettings() {
        loadSettings();
        validateLoadedCombos();
        startHealTask();
        startHarvestTask();
        startXpTask();
    }

    /** Warp pads and XP collectors are not registered while their switch is off (as before 2.44.0). */
    boolean typeEnabled(ComboType type) {
        return switch (type) {
            case WARP_PAD -> warpPadsEnabled;
            case XP_COLLECTOR -> xpCollectorsEnabled;
            default -> true;
        };
    }

    /** The feature switch of a combo type (each combo has its own since 2.44.0). */
    boolean featureOn(ComboType type) {
        return switch (type) {
            case SORTER -> BlockIntegrations.enabled(plugin, Feature.SORTER);
            case VACUUM -> BlockIntegrations.enabled(plugin, Feature.VACUUM);
            case LAUNCHER -> BlockIntegrations.enabled(plugin, Feature.LAUNCHER);
            case BOOSTER -> BlockIntegrations.enabled(plugin, Feature.BOOSTER);
            case FLOAT_LAUNCHER -> BlockIntegrations.enabled(plugin, Feature.FLOAT_LIFT);
            case HEAL -> healEnabled;
            case HARVESTER -> harvesterEnabled;
            case WARP_PAD -> warpPadsEnabled;
            case XP_COLLECTOR -> xpCollectorsEnabled;
        };
    }

    /** Combos whose menu holds more than upgrades (profiles, rename, stored XP): it opens without combos.enabled too. */
    static boolean ownMenu(ComboType type) {
        return type == ComboType.HARVESTER || type == ComboType.WARP_PAD || type == ComboType.XP_COLLECTOR;
    }

    int maximumLevel() {
        return maximumLevel;
    }

    int comboCount() {
        return combos.size();
    }

    int range(Block control, ComboType type, int base) {
        return ComboScaling.range(base, level(control, type, ComboUpgrade.RANGE),
                rangeBlocksPerLevel);
    }

    double power(Block control, ComboType type, double base) {
        return ComboScaling.power(base, level(control, type, ComboUpgrade.POWER),
                powerPerLevel.getOrDefault(type, 0.0D));
    }

    int duration(Block control, ComboType type, int baseTicks) {
        return ComboScaling.duration(baseTicks, level(control, type, ComboUpgrade.DURATION),
                durationTicksPerLevel);
    }

    long cooldown(Block control, ComboType type, long baseTicks) {
        return ComboScaling.cooldown(baseTicks, level(control, type, ComboUpgrade.COOLDOWN),
                cooldownReductionPerLevel, minimumCooldownTicks);
    }

    void sendGuide(CommandSender sender) {
        guideLines(sender).forEach(line -> plugin.core().compat().platform().sendMessage(sender, line));
    }

    List<Component> guideLines(CommandSender viewer) {
        Lang lang = plugin.lang();
        List<Component> lines = new ArrayList<>();
        lines.add(lang.component(viewer, "combo.guide.header"));
        lines.add(lang.component(viewer, "combo.guide.open"));
        for (ComboType type : ComboType.values()) {
            lines.add(lang.component(viewer, "combo.guide.structure", "name", name(viewer, type),
                    "structure", lang.component(viewer, "combo." + type.configKey + ".structure")));
        }
        lines.add(lang.component(viewer, "combo.guide.paths", "max", maximumLevel));
        lines.add(lang.component(viewer, "combo.guide.float"));
        lines.add(lang.component(viewer, "combo.guide.heal",
                "seconds", decimal(viewer, healDamageCooldownTicks / 20.0D), "distance", healMinimumDistance));
        if (warpPadsEnabled) lines.add(lang.component(viewer, "combo.guide.warp"));
        if (xpCollectorsEnabled) lines.add(lang.component(viewer, "combo.guide.xp"));
        return lines;
    }

    static final int MINE_LIMIT = 20;

    /** {@code /bi combos mine}: the player's own combos, nearest first in their world, then the other worlds. */
    void sendOwnCombos(Player player) {
        Lang lang = plugin.lang();
        Location at = player.getLocation();
        List<Combo> own = nearestFirst(combosOwnedBy(player.getUniqueId()), player.getWorld().getUID(), at.getX(), at.getY(), at.getZ());
        lang.send(player, "combo.mine.header", "count", own.size());
        if (own.isEmpty()) lang.send(player, "combo.mine.empty");
        for (Combo combo : own.subList(0, Math.min(MINE_LIMIT, own.size()))) {
            Location location = ChatLinks.location(combo.key);
            Component line;
            if (location != null && location.getWorld().equals(player.getWorld())) {
                line = lang.component(player, "combo.mine.entry-here", "name", name(player, combo.type),
                        "x", combo.key.x(), "y", combo.key.y(), "z", combo.key.z(),
                        "blocks", (int) Math.round(Math.sqrt(distanceSquared(combo.key, at.getX(), at.getY(), at.getZ()))));
            } else {
                line = lang.component(player, "combo.mine.entry-elsewhere", "name", name(player, combo.type),
                        "world", location == null ? "?" : location.getWorld().getName(),
                        "x", combo.key.x(), "y", combo.key.y(), "z", combo.key.z());
            }
            plugin.core().compat().platform().sendMessage(player, location == null ? line : line.append(Component.space()).append(plugin.core().message().chat().location(location)));
        }
        if (own.size() > MINE_LIMIT) lang.send(player, "combo.mine.more", "count", own.size() - MINE_LIMIT);
        lang.send(player, "combo.mine.hint");
    }

    /** Combos in {@code world} by distance to (x, y, z), then those in other worlds in their usual order. */
    static List<Combo> nearestFirst(List<Combo> combos, UUID world, double x, double y, double z) {
        List<Combo> here = new ArrayList<>(), elsewhere = new ArrayList<>();
        for (Combo combo : combos) (world.equals(combo.key.worldId()) ? here : elsewhere).add(combo);
        here.sort(java.util.Comparator.comparingDouble(c -> distanceSquared(c.key, x, y, z)));
        here.addAll(elsewhere);
        return here;
    }

    private static double distanceSquared(BlockKey key, double x, double y, double z) {
        double dx = key.x() + 0.5D - x, dy = key.y() + 0.5D - y, dz = key.z() + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Coloured display name of a combination in the viewer's language. */
    private Component name(CommandSender viewer, ComboType type) {
        return plugin.lang().component(viewer, type.nameKey()).colorIfAbsent(type.color);
    }

    /**
     * Block break and place go through Core's stages, so Core decides when they run among all Vystorm plugins.
     * A heal block too close to another one is refused in ADJUST (a rule of this plugin, not a protection); the stored
     * combinations follow the final outcome in OBSERVE. None of the handlers sees a cancelled event.
     */
    void handleBlockEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, BlockPlaceEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.ADJUST, this::onHealBlockPlace);
        stages.handle(plugin, BlockPlaceEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onBlockPlace);
        stages.handle(plugin, BlockBreakEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onBlockBreak);
    }

    /** Stage ADJUST, registered in {@link #handleBlockEvents}. */
    public void onHealBlockPlace(BlockPlaceEvent event) {
        if (event instanceof BuildProbeEvent) return; // test placement, nothing was placed
        if (!healEnabled) {
            return;
        }
        Block control = completedHealControl(event.getBlockPlaced());
        if (control == null || !hasNearbyHealBlock(control)) {
            return;
        }
        event.setCancelled(true);
        plugin.lang().send(event.getPlayer(), "combo.heal-too-close", "distance", healMinimumDistance);
    }

    /** Stage OBSERVE, registered in {@link #handleBlockEvents}. */
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event instanceof BuildProbeEvent) return; // test placement, nothing was placed
        if (!tracking || event.isCancelled() || !event.canBuild()) {
            return;
        }
        ItemStack placedItem = event.getItemInHand().clone();
        UUID worldId = event.getBlockPlaced().getWorld().getUID();
        int x = event.getBlockPlaced().getX();
        int y = event.getBlockPlaced().getY();
        int z = event.getBlockPlaced().getZ();
        UUID playerId = event.getPlayer().getUniqueId();
        BlockKey placedKey = BlockKey.of(event.getBlockPlaced());
        Combo previous = combos.get(placedKey);
        Combo restored = restorePortable(event.getBlockPlaced(), placedItem);
        tasks.now(() -> {
            World world = Bukkit.getWorld(worldId);
            Player player = Bukkit.getPlayer(playerId);
            if (event.isCancelled() || !event.canBuild()) {
                if (restored != null && combos.remove(placedKey, restored)) {
                    if (previous != null) combos.put(placedKey, previous);
                    queueSave();
                }
                return;
            }
            if (world == null) return;
            for (int up = 0; up <= 2; up++) {
                Block candidate = world.getBlockAt(x, y + up, z);
                ComboType detected = ComboType.detect(candidate);
                if (detected == null) {
                    continue;
                }
                BlockKey comboKey = BlockKey.of(candidate);
                boolean created = !combos.containsKey(comboKey);
                if (!typeEnabled(detected)) return;
                if (created && detected == ComboType.WARP_PAD && player != null && warpLimitReached(player)) {
                    plugin.lang().send(player, "combo.warp-pad.limit", "max", warpMaxPerPlayer);
                    return;
                }
                Combo recognized = recognize(candidate);
                if (recognized != null && recognized.owner() == null && player != null) {
                    // Built (or moved here) by this player: he owns it on the web page.
                    recognized.owner(playerId);
                    queueSave();
                    plugin.structuresChanged(playerId);
                }
                // announced only while the combo works and its menu has something to offer
                if (created && player != null && featureOn(detected) && (enabled || ownMenu(detected))) {
                    Lang lang = plugin.lang();
                    plugin.core().compat().platform().sendMessage(player, lang.component(player, "combo.recognized",
                                    "name", lang.plain(player, detected.nameKey()))
                            .append(plugin.core().message().chat().button(lang.plain(player, "chat.upgrades"),
                                    lang.plain(player, "chat.upgrades-hover"),
                                    p -> openComboFromChat(p, comboKey))));
                }
                return;
            }
        });
    }

    /** Stage OBSERVE, registered in {@link #handleBlockEvents}. */
    public void onBlockBreak(BlockBreakEvent event) {
        if (!tracking || event.isCancelled()) return;
        BlockKey key = BlockKey.of(event.getBlock());
        Combo broken = combos.get(key);
        if (broken == null) return;
        tasks.now(() -> {
            if (!event.isCancelled() && combos.remove(key, broken)) {
                spillStoredXp(broken);
                queueSave();
                plugin.structuresChanged(broken.owner());
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (!tracking || event.isCancelled()) return;
        BlockKey key = BlockKey.of(event.getBlock());
        Combo combo = combos.get(key);
        if (combo == null || !combo.type.isControl(event.getBlockState().getType())) return;
        Map<String, Integer> levels = new HashMap<>();
        for (ComboUpgrade upgrade : ComboUpgrade.values()) {
            if (combo.type.supports(upgrade)) levels.put(upgrade.configKey, combo.level(upgrade));
        }
        for (var drop : event.getItems()) {
            ItemStack item = drop.getItemStack();
            if (item.getType() != event.getBlockState().getType() || item.getAmount() != 1) continue;
            drop.setItemStack(plugin.portableBlocks().write(item, event.getPlayer(), "combo", combo.type.configKey, levels, null, 0));
            DebugMessages.low("[ComboManager] Combination stored on the item: " + combo.type.configKey);
            break;
        }
        // BlockDropItemEvent confirms the block was broken, even if another plugin suppresses its drop.
        if (combos.remove(key, combo)) {
            spillStoredXp(combo);
            queueSave();
            plugin.structuresChanged(combo.owner());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onControlPhysics(BlockPhysicsEvent event) {
        // Block physics fires constantly: leave before building a key unless it can be a sorter/vacuum plate (2.41.2).
        if (!tracking || combos.isEmpty()) return;
        Material type = event.getBlock().getType();
        if (!ComboType.SORTER.isControl(type) && !ComboType.VACUUM.isControl(type)) return;
        Combo combo = combos.get(BlockKey.of(event.getBlock()));
        if (tracking && combo != null && (combo.type == ComboType.SORTER || combo.type == ComboType.VACUUM)
                && combo.type.isControl(event.getBlock().getType()) && !combo.type.matches(event.getBlock())) {
            // Keep the main pressure plate when its support is removed; the incomplete combo is inactive.
            event.setCancelled(true);
        }
    }

    private Combo restorePortable(Block control, ItemStack item) {
        PortableBlockData.State state = plugin.portableBlocks().read(item);
        if (state == null || !state.kind().equals("combo") || state.material() != control.getType()) return null;
        ComboType type = ComboType.byConfigKey(state.type());
        if (type == null || !type.isControl(control.getType())) return null;
        Map<ComboUpgrade, Integer> levels = new EnumMap<>(ComboUpgrade.class);
        for (ComboUpgrade upgrade : ComboUpgrade.values()) levels.put(upgrade, state.levels().getOrDefault(upgrade.configKey, 0));
        BlockKey key = BlockKey.of(control);
        Combo restored = new Combo(key, type, levels, maximumLevel);
        combos.put(key, restored);
        queueSave();
        DebugMessages.low("[ComboManager] Moved combination restored: " + type.configKey);
        return restored;
    }

    /* Stage OBSERVE, registered in handleSharedEvents */
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (healEnabled && event.getEntity() instanceof Player player && event.getFinalDamage() > 0.0D) {
            lastDamageTick.put(player.getUniqueId(), player.getWorld().getGameTime());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastDamageTick.remove(playerId);
        lastHealTick.remove(playerId);
        nextWarp.remove(playerId);
        nextWarpPrompt.remove(playerId);
    }

    /* Stage ACT, registered in handleSharedEvents */
    public void onComboInteract(PlayerInteractEvent event) {
        if (!tracking || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !event.getPlayer().isSneaking() || event.getClickedBlock() == null) {
            return;
        }
        Block control = event.getClickedBlock();
        Combo registered = combos.get(BlockKey.of(control));
        if (registered == null && control.getType() == Material.SLIME_BLOCK) {
            control = SlimeFieldStructure.controlFor(control);
            if (control == null) return;
        }
        Player player = event.getPlayer();
        registered = combos.get(BlockKey.of(control));
        ComboType detected = registered == null ? null : registered.type;
        if (detected == null) {
            Material controlMaterial = control.getType();
            for (ComboType candidate : ComboType.values()) {
                if (candidate.isControl(controlMaterial)) {
                    detected = candidate;
                    break;
                }
            }
        }
        if (detected == null || !typeEnabled(detected)) return;
        // its own switch off, or combos.enabled off and the menu would only hold upgrades: the click stays vanilla
        if (!featureOn(detected) || !enabled && !ownMenu(detected)) return;
        if (!player.hasPermission("blockintegrations.combos")) {
            if (registered != null || detected != ComboType.BOOSTER && !commonControl(detected)) {
                event.setCancelled(true);
                plugin.lang().send(player, "combo.no-use");
            }
            return;
        }
        if (!detected.matches(control)) {
            // Ice and slime blocks are common materials, so an unregistered candidate needs
            // a second structural clue before we label it as a machine attempt.
            if (registered == null && (detected == ComboType.BOOSTER || detected == ComboType.LAUNCHER || commonControl(detected))
                    && !MachineDiagnostics.hasComboEvidence(detected, control)) return;
            event.setCancelled(true);
            if (registered != null && registered.owner() != null
                    && !player.hasPermission(BlockIntegrations.ADMIN_PERMISSION)
                    && !player.getUniqueId().equals(registered.owner())) {
                plugin.lang().send(player, "combo.no-use");
                return;
            }
            sendMachineDiagnostic(player, detected, MachineDiagnostics.combo(detected, control));
            return;
        }
        event.setCancelled(true);
        if (registered == null && detected == ComboType.WARP_PAD && warpLimitReached(player)) {
            plugin.lang().send(player, "combo.warp-pad.limit", "max", warpMaxPerPlayer);
            return;
        }
        Combo combo = recognize(control);
        if (combo != null && combo.owner() == null && !BuildProbeEvent.mayBuild(player, control)) {
            // Ownerless (built before 2.20.0, or an XP collector built before 2.29.0): only someone who could have built
            // it here takes it over, so nobody claims another player's collector (and its XP) by clicking it first.
            plugin.lang().send(player, "combo.claim-denied");
        } else if (combo != null && combo.owner() == null) {
            // Combos from before 2.20.0 have no builder: the first user takes them over (only for the web page).
            combo.owner(player.getUniqueId());
            queueSave();
            plugin.structuresChanged(player.getUniqueId());
            plugin.lang().send(player, "combo.claimed");
        }
        showRange(player, control, combo);
        openMenu(player, combo);
    }

    private void sendMachineDiagnostic(Player player, ComboType type, MachineDiagnostics.Report report) {
        Lang lang = plugin.lang();
        String machine = lang.plain(player, type.nameKey());
        if (report.state() == MachineDiagnostics.State.UNLOADED) {
            lang.send(player, "machine-diagnostic.unloaded", "machine", machine);
            return;
        }
        if (report.state() == MachineDiagnostics.State.COMPLETE) return;
        lang.send(player, "machine-diagnostic.header", "machine", machine);
        for (MachineDiagnostics.Finding finding : report.findings()) {
            lang.send(player, "machine-diagnostic.finding",
                    "part", lang.plain(player, "machine-diagnostic.part." + finding.part()),
                    "expected", MachineDiagnostics.materialName(player, finding.expected()),
                    "actual", MachineDiagnostics.materialName(player, finding.actual()),
                    "x", finding.x(), "y", finding.y(), "z", finding.z());
        }
    }

    /** Uses the exact effective radius that the utility/heal operation uses. */
    private void showRange(Player player, Block control, Combo combo) {
        if (combo == null || plugin.radiusPreviews() == null) return;
        double radius;
        Location center;
        switch (combo.type) {
            case HEAL -> {
                radius = range(control, ComboType.HEAL, healAuraRadius);
                center = control.getLocation().add(0.5D, 1.0D, 0.5D);
            }
            case XP_COLLECTOR -> {
                radius = range(control, ComboType.XP_COLLECTOR, plugin.comboBaseRange(ComboType.XP_COLLECTOR));
                center = control.getLocation().add(0.5D, 0.5D, 0.5D);
            }
            case HARVESTER -> {
                radius = range(control, ComboType.HARVESTER, plugin.comboBaseRange(ComboType.HARVESTER));
                center = control.getLocation().add(0.5D, 0.5D, 0.5D);
            }
            case SORTER, VACUUM -> {
                Block origin = control.getRelative(0, -1, 0);
                radius = range(control, combo.type, plugin.comboBaseRange(combo.type));
                center = origin.getLocation().add(0.5D, 1.0D, 0.5D);
            }
            default -> { return; }
        }
        BlockKey key = combo.key;
        plugin.radiusPreviews().show(player, center, radius,
                viewer -> viewer.hasPermission("blockintegrations.combos") && validCombo(key) == combo);
    }

    private void upgradeCombo(Player player, BlockKey key, ComboUpgrade upgrade, int expectedLevel, int expectedCost) {
        Lang lang = plugin.lang();
        UpgradeResult result = applyUpgrade(player, key, upgrade, expectedLevel, expectedCost);
        Combo combo = combos.get(key);
        switch (result.outcome()) {
            case NO_PERMISSION -> lang.send(player, "combo.no-upgrade");
            case GONE -> {
                player.closeInventory();
                lang.send(player, "combo.incomplete");
            }
            case MAXED -> lang.send(player, "upgrade.maxed");
            case CHANGED -> {
                lang.send(player, "upgrade.offer-changed");
                if (combo != null) openMenu(player, combo);
            }
            case NEED_XP -> lang.send(player, "upgrade.need-xp", "cost", result.cost());
            case DONE -> {
                if (combo != null) openMenu(player, combo);
            }
            default -> { } // UNSUPPORTED: no button offers it
        }
    }

    /**
     * Checks and applies a combo upgrade: permissions, complete combo (chunk loaded), supported path, max level, the
     * offer the player saw ({@code expectedLevel < 0} skips it) and the XP cost. Used by the upgrade dialog and the web
     * page (which additionally requires the builder or an admin); on success the player gets the chat confirmation.
     */
    UpgradeResult applyUpgrade(Player player, BlockKey key, ComboUpgrade upgrade, int expectedLevel, int expectedCost) {
        return applyUpgrade(player, key, upgrade, expectedLevel, expectedCost, true);
    }

    /** As above; {@code announce = false} skips the per-step chat line and sound (blueprints report once). */
    UpgradeResult applyUpgrade(Player player, BlockKey key, ComboUpgrade upgrade, int expectedLevel, int expectedCost, boolean announce) {
        Lang lang = plugin.lang();
        if (!player.hasPermission("blockintegrations.combos") || !player.hasPermission("blockintegrations.combos.upgrade"))
            return UpgradeResult.of(UpgradeResult.Outcome.NO_PERMISSION);
        Combo combo = validCombo(key);
        if (combo == null) return UpgradeResult.of(UpgradeResult.Outcome.GONE);
        if (!combo.type.supports(upgrade)) return UpgradeResult.of(UpgradeResult.Outcome.UNSUPPORTED);
        int currentLevel = combo.level(upgrade);
        if (currentLevel >= maximumLevel) return new UpgradeResult(UpgradeResult.Outcome.MAXED, currentLevel, 0);
        int cost = upgradeCost(upgrade, currentLevel);
        if (expectedLevel >= 0 && (currentLevel != expectedLevel || cost != expectedCost))
            return new UpgradeResult(UpgradeResult.Outcome.CHANGED, currentLevel, cost);
        if (!takeExperience(player, cost)) return new UpgradeResult(UpgradeResult.Outcome.NEED_XP, currentLevel, cost);
        combo.upgrade(upgrade);
        queueSave();
        plugin.structuresChanged(combo.owner());
        DebugMessages.low("[ComboManager] Upgrade applied: " + combo.type.configKey
                + "/" + upgrade.configKey + " level=" + combo.level(upgrade));
        if (announce) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7F, 1.35F);
            lang.send(player, "upgrade.done", "upgrade", lang.plain(player, combo.type.upgradeNameKey(upgrade)),
                    "level", combo.level(upgrade), "cost", cost);
        }
        return new UpgradeResult(UpgradeResult.Outcome.DONE, combo.level(upgrade), cost);
    }

    /** Chat button: permissions, state and distance are checked on click. */
    private void openComboFromChat(Player player, BlockKey key) {
        if (!player.hasPermission("blockintegrations.combos")) {
            plugin.lang().send(player, "combo.no-use");
            return;
        }
        Combo combo = validCombo(key);
        if (combo == null) {
            plugin.lang().send(player, "combo.incomplete");
            return;
        }
        if (!featureOn(combo.type) || !enabled && !ownMenu(combo.type)) { plugin.lang().send(player, "feature-off"); return; }
        if (ChatLinks.near(plugin, player, key, "chat.too-far.combo")) openMenu(player, combo);
    }

    private void openMenu(Player player, Combo combo) {
        Lang lang = plugin.lang();
        BlockKey key = combo.key;
        boolean slimeField = combo.type == ComboType.LAUNCHER && SlimeFieldStructure.complete(loadedControl(key));
        String title = lang.plain(player, slimeField ? "combo.slime-field.name" : combo.type.nameKey());
        List<DialogButton> buttons = new ArrayList<>();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(lang.component(player, "combo.dialog.header", "name", title, "max", maximumLevel)));
        if (slimeField) body.add(DialogBody.plainMessage(lang.component(player, "combo.slime-field.info")));
        if (enabled) for (ComboUpgrade upgrade : ComboUpgrade.values()) if (combo.type.supports(upgrade)) { // combos.enabled: upgrades
            int shown = combo.level(upgrade); int cost = shown >= maximumLevel ? 0 : upgradeCost(upgrade, shown);
            String upgradeName = lang.plain(player, combo.type.upgradeNameKey(upgrade));
            String value = upgradeValue(player, combo.type, upgrade, shown);
            body.add(DialogBody.plainMessage(shown >= maximumLevel
                    ? lang.component(player, "combo.dialog.line-max", "upgrade", upgradeName, "level", shown, "max", maximumLevel, "value", value)
                    : lang.component(player, "combo.dialog.line", "upgrade", upgradeName, "level", shown, "max", maximumLevel, "value", value, "cost", cost)));
            if (shown < maximumLevel) buttons.add(new DialogButton(lang.component(player, "combo.dialog.button",
                    "upgrade", upgradeName, "cost", cost), (p, r) -> upgradeCombo(p, key, upgrade, shown, cost)));
        }
        if (enabled && BlockIntegrations.enabled(plugin, Feature.BLUEPRINTS) && player.hasPermission("blockintegrations.combos.upgrade")) {
            ComboPrint mine = blueprintOf(combo);
            buttons.add(new DialogButton(lang.component(player, "blueprint.save"), lang.component(player, "blueprint.save-hover"),
                    (p, r) -> { blueprints.put(p.getUniqueId(), mine); lang.send(p, "blueprint.saved"); }));
            ComboPrint print = blueprints.get(player.getUniqueId());
            if (print != null && print.type() == combo.type) {
                List<ComboUpgrade> steps = MachineBlueprints.steps(print.levels(), mine.levels());
                boolean profile = combo.type == ComboType.HARVESTER && print.profile() != combo.harvestProfile() && mayManage(player, combo);
                if (!steps.isEmpty() || profile) {
                    long total = MachineBlueprints.cost(steps, mine.levels(), this::upgradeCost);
                    buttons.add(new DialogButton(lang.component(player, "blueprint.apply", "steps", steps.size(), "cost", total),
                            lang.component(player, "blueprint.apply-hover"), (p, r) -> applyBlueprint(p, key)));
                }
            }
        }
        if (combo.type == ComboType.HARVESTER) {
            body.add(DialogBody.plainMessage(lang.component(player, "harvest-profile.current",
                    "profile", lang.plain(player, combo.harvestProfile().nameKey()))));
            if (mayManage(player, combo)) buttons.add(new DialogButton(lang.component(player, "harvest-profile.open"),
                    (p, r) -> openHarvestProfiles(p, combo)));
        }
        if (combo.type == ComboType.WARP_PAD) {
            body.add(DialogBody.plainMessage(lang.component(player, "combo.warp-pad.dialog.name", "name", warpName(player, combo))));
            body.add(DialogBody.plainMessage(lang.component(player, "combo.warp-pad.dialog.use",
                    "guests", combo.owner() == null ? 0 : warpGuests(combo.owner()).size())));
            if (mayManage(player, combo)) buttons.add(new DialogButton(lang.component(player, "combo.warp-pad.dialog.rename"),
                    lang.component(player, "combo.warp-pad.dialog.rename-tooltip"), (p, r) -> promptRename(p, key)));
        }
        if (combo.type == ComboType.XP_COLLECTOR) {
            body.add(DialogBody.plainMessage(lang.component(player, "combo.xp-collector.dialog.stored",
                    "stored", combo.storedXp(), "capacity", xpCapacity(combo))));
            if (mayManage(player, combo) && combo.storedXp() > 0) {
                buttons.add(new DialogButton(lang.component(player, "combo.xp-collector.dialog.take-level"),
                        (p, r) -> takeXp(p, key, false)));
                buttons.add(new DialogButton(lang.component(player, "combo.xp-collector.dialog.take-all", "stored", combo.storedXp()),
                        (p, r) -> takeXp(p, key, true)));
            } else if (!mayManage(player, combo)) {
                body.add(DialogBody.plainMessage(lang.component(player, "combo.xp-collector.dialog.owner-only")));
            }
        }
        plugin.addWebButton(buttons, player, "combo/" + key.webId());
        buttons.add(PortableMenuButtons.back(plugin.core(), plugin::openBlockGuideMenu));
        buttons.add(PortableMenuButtons.home(plugin.core()));
        plugin.core().dialogs().open(plugin, player, new DialogRequest(lang.component(player, "combo.dialog.title", "name", title),
                body, List.of(), buttons,
                p -> p.isOnline() && p.hasPermission("blockintegrations.combos") && validCombo(key) == combo
                        && (!slimeField || SlimeFieldStructure.complete(loadedControl(key))),
                () -> {}, BlockIntegrations.CLOSE_LABEL, 3));
    }

    private boolean canEditHarvestProfile(Player player, Combo combo) {
        return harvesterEnabled && player.isOnline()
                && player.hasPermission("blockintegrations.combos") && combo.type == ComboType.HARVESTER
                && validCombo(combo.key) == combo && mayManage(player, combo);
    }

    private void openHarvestProfiles(Player player, Combo combo) {
        if (!canEditHarvestProfile(player, combo)) {
            plugin.lang().send(player, "harvest-profile.unavailable");
            return;
        }
        if (!ChatLinks.near(plugin, player, combo.key, "chat.too-far.combo")) return;
        Lang lang = plugin.lang();
        List<DialogButton> buttons = new ArrayList<>();
        for (HarvestProfile profile : HarvestProfile.values()) {
            buttons.add(new DialogButton(lang.component(player, profile.nameKey()),
                    lang.component(player, "harvest-profile.details." + profile.key),
                    (p, r) -> {
                        if (setHarvestProfile(p, combo, profile)) openHarvestProfiles(p, combo);
                    }));
        }
        buttons.add(PortableMenuButtons.back(plugin.core(), p -> openComboFromChat(p, combo.key)));
        plugin.core().dialogs().open(plugin, player, new DialogRequest(lang.component(player, "harvest-profile.title"),
                List.of(DialogBody.plainMessage(lang.component(player, "harvest-profile.current",
                                "profile", lang.plain(player, combo.harvestProfile().nameKey()))),
                        DialogBody.plainMessage(lang.component(player, "harvest-profile.help"))),
                List.of(), buttons, p -> canEditHarvestProfile(p, combo), () -> {}, BlockIntegrations.CLOSE_LABEL, 2));
    }

    /**
     * Machine groups in the dashboard: the owner pauses or resumes a harvester from anywhere. Same rights as the
     * profile dialog, but no distance check. Quiet (the group page reports the count).
     */
    boolean setHarvestProfileRemote(Player player, Combo combo, HarvestProfile profile) {
        if (combo == null || !canEditHarvestProfile(player, combo)) return false;
        combo.harvestProfile(profile);
        queueSave();
        return true;
    }

    /** Recheck the actual machine, owner and distance when a dialog action arrives. */
    boolean setHarvestProfile(Player player, Combo combo, HarvestProfile profile) {
        if (!canEditHarvestProfile(player, combo)) {
            plugin.lang().send(player, "harvest-profile.unavailable");
            return false;
        }
        if (!ChatLinks.near(plugin, player, combo.key, "chat.too-far.combo")) return false;
        combo.harvestProfile(profile);
        queueSave();
        plugin.lang().send(player, "harvest-profile.changed", "profile", plugin.lang().plain(player, profile.nameKey()));
        return true;
    }

    private String upgradeValue(CommandSender viewer, ComboType type, ComboUpgrade upgrade, int level) {
        return upgradeValue(plugin.lang().language(viewer), type, upgrade, level);
    }

    private String upgradeValue(String viewer, ComboType type, ComboUpgrade upgrade, int level) {
        Lang lang = plugin.lang();
        if (type == ComboType.WARP_PAD && upgrade == ComboUpgrade.RANGE)
            return lang.plain(viewer, "combo.value.blocks", "blocks", WarpPads.reach(warpBaseDistance, warpDistancePerLevel, level));
        if (type == ComboType.XP_COLLECTOR && upgrade == ComboUpgrade.POWER)
            return lang.plain(viewer, "combo.value.xp", "points", XpCollector.capacity(xpBaseCapacity, xpCapacityPerLevel, level));
        if (type == ComboType.HEAL) {
            return switch (upgrade) {
                case RANGE -> lang.plain(viewer, "combo.value.aura", "blocks", ComboScaling.range(healAuraRadius, level,
                        rangeBlocksPerLevel));
                case POWER -> lang.plain(viewer, "combo.value.hearts", "hearts", decimal(viewer, ComboScaling.power(healAmount, level,
                        powerPerLevel.getOrDefault(type, 0.0D)) / 2.0D));
                case COOLDOWN -> lang.plain(viewer, "combo.value.heal-cooldown",
                        "pulse", decimal(viewer, ComboScaling.cooldown(healPulseCooldownTicks, level,
                                cooldownReductionPerLevel, minimumCooldownTicks) / 20.0D),
                        "damage", decimal(viewer, ComboScaling.cooldown(healDamageCooldownTicks, level,
                                cooldownReductionPerLevel, minimumCooldownTicks) / 20.0D));
                case DURATION -> "-";
            };
        }
        return switch (upgrade) {
            case RANGE -> lang.plain(viewer, "combo.value.blocks", "blocks", ComboScaling.range(plugin.comboBaseRange(type), level,
                    rangeBlocksPerLevel));
            case POWER -> lang.plain(viewer, "combo.value.velocity", "value", decimal(viewer, ComboScaling.power(plugin.comboBasePower(type), level,
                    powerPerLevel.getOrDefault(type, 0.0D))));
            case DURATION -> lang.plain(viewer, "combo.value.seconds", "value", decimal(viewer, ComboScaling.duration(plugin.comboBaseDuration(type), level,
                    durationTicksPerLevel) / 20.0D));
            case COOLDOWN -> lang.plain(viewer, "combo.value.seconds", "value", decimal(viewer, ComboScaling.cooldown(plugin.comboBaseCooldown(type), level,
                    cooldownReductionPerLevel, minimumCooldownTicks) / 20.0D));
        };
    }

    private Combo recognize(Block control) {
        ComboType detected = ComboType.detect(control);
        BlockKey key = BlockKey.of(control);
        Combo existing = combos.get(key);
        if (detected != null && !typeEnabled(detected)) return null;
        if (detected == null) {
            if (existing != null && !existing.type.isControl(control.getType())) {
                combos.remove(key);
                spillStoredXp(existing);
                queueSave();
                plugin.structuresChanged(existing.owner());
            }
            return null;
        }
        if (existing != null && existing.type == detected) {
            return existing;
        }
        Combo combo = new Combo(key, detected, Map.of(), maximumLevel);
        if (existing != null) combo.owner(existing.owner());
        combos.put(key, combo);
        queueSave();
        plugin.structuresChanged(combo.owner());
        return combo;
    }

    private Combo validCombo(BlockKey key) {
        Combo combo = combos.get(key);
        Block control = loadedControl(key);
        if (combo == null || control == null || ComboType.detect(control) != combo.type) {
            if (combo != null && control != null && !combo.type.isControl(control.getType())) {
                combos.remove(key);
                spillStoredXp(combo);
                queueSave();
                plugin.structuresChanged(combo.owner());
            }
            return null;
        }
        return combo;
    }

    private int level(Block control, ComboType expectedType, ComboUpgrade upgrade) {
        if (!enabled || !expectedType.supports(upgrade)) {
            return 0;
        }
        Combo combo = combos.get(BlockKey.of(control));
        if (combo == null || combo.type != expectedType || !expectedType.matches(control)) {
            return 0;
        }
        return combo.level(upgrade);
    }

    private void validateLoadedCombos() {
        boolean changed = combos.entrySet().removeIf(entry -> {
            Block control = loadedControl(entry.getKey());
            boolean gone = control != null && !entry.getValue().type.isControl(control.getType());
            if (gone) spillStoredXp(entry.getValue());
            return gone;
        });
        if (changed) {
            queueSave();
        }
    }

    private Block loadedControl(BlockKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
            return null;
        }
        return world.getBlockAt(key.x(), key.y(), key.z());
    }

    private void loadSettings() {
        FileConfiguration config = plugin.getConfig();
        enabled = BlockIntegrations.enabled(plugin, Feature.COMBO_UPGRADES);
        tracking = BlockIntegrations.enabled(plugin, Feature.BLOCKS);
        healEnabled = BlockIntegrations.enabled(plugin, Feature.HEAL);
        harvesterEnabled = BlockIntegrations.enabled(plugin, Feature.HARVESTER);
        maximumLevel = Math.max(1, Math.min(10,
                config.getInt("combos.upgrades.maximum-level", 10)));
        rangeBlocksPerLevel = Math.max(0, Math.min(5,
                config.getInt("combos.scaling.range-blocks-per-level", 1)));
        durationTicksPerLevel = Math.max(0, Math.min(200,
                config.getInt("combos.scaling.duration-ticks-per-level", 10)));
        cooldownReductionPerLevel = bounded(config.getDouble(
                "combos.scaling.cooldown-reduction-per-level", 0.05D), 0.0D, 0.1D);
        minimumCooldownTicks = Math.max(0L, config.getLong(
                "combos.scaling.minimum-cooldown-ticks", 5L));

        powerPerLevel.put(ComboType.LAUNCHER, bounded(config.getDouble(
                "combos.scaling.power-per-level.launcher", 0.08D), 0.0D, 1.0D));
        powerPerLevel.put(ComboType.BOOSTER, bounded(config.getDouble(
                "combos.scaling.power-per-level.booster", 0.05D), 0.0D, 1.0D));
        powerPerLevel.put(ComboType.FLOAT_LAUNCHER, bounded(config.getDouble(
                "combos.scaling.power-per-level.float-launcher", 0.08D), 0.0D, 1.0D));
        powerPerLevel.put(ComboType.HEAL, bounded(config.getDouble(
                "combos.scaling.power-per-level.heal", 0.50D), 0.0D, 4.0D));

        healAmount = bounded(config.getDouble("combos.heal.amount", 1.0D), 0.1D, 20.0D);
        healAuraRadius = Math.max(1, Math.min(32,
                config.getInt("combos.heal.aura-radius", 3)));
        healPulseCooldownTicks = Math.max(1L,
                config.getLong("combos.heal.pulse-cooldown-ticks", 60L));
        healDamageCooldownTicks = Math.max(0L,
                config.getLong("combos.heal.damage-cooldown-ticks", 100L));
        healMinimumDistance = Math.max(1, Math.min(64,
                config.getInt("combos.heal.minimum-distance", 10)));
        healTaskPeriodTicks = Math.max(1L, Math.min(20L,
                config.getLong("combos.heal.task-period-ticks", 5L)));
        healParticles = config.getBoolean("combos.heal.play-particles", true);
        healSound = config.getBoolean("combos.heal.play-sound", true);

        warpPadsEnabled = BlockIntegrations.enabled(plugin, Feature.WARP_PADS);
        warpBaseDistance = Math.max(8, Math.min(100_000, config.getInt("combos.warp-pad.base-distance", 256)));
        warpDistancePerLevel = Math.max(0, Math.min(10_000, config.getInt("combos.warp-pad.distance-per-level", 128)));
        warpMaxPerPlayer = Math.max(0, Math.min(1000, config.getInt("combos.warp-pad.max-per-player", 10)));
        warpParticles = config.getBoolean("combos.warp-pad.play-particles", true);
        xpCollectorsEnabled = BlockIntegrations.enabled(plugin, Feature.XP_COLLECTOR);
        xpBaseCapacity = Math.max(10, Math.min(1_000_000, config.getInt("combos.xp-collector.capacity", 1500)));
        xpCapacityPerLevel = Math.max(0, Math.min(1_000_000, config.getInt("combos.xp-collector.capacity-per-level", 1500)));
        xpPlayerSafeRadius = bounded(config.getDouble("combos.xp-collector.player-safe-radius", 3.0D), 0.0D, 16.0D);
        xpParticles = config.getBoolean("combos.xp-collector.play-particles", true);

        for (ComboUpgrade upgrade : ComboUpgrade.values()) {
            List<Integer> costs = new ArrayList<>(config.getIntegerList(
                    "combos.upgrades." + upgrade.configKey + "-costs"));
            if (costs.isEmpty()) {
                costs = new ArrayList<>(defaultCosts(upgrade));
            }
            costs.replaceAll(cost -> Math.max(0, cost));
            upgradeCosts.put(upgrade, List.copyOf(costs));
        }
    }

    private void startHarvestTask() {
        if (harvestTask != null) {
            harvestTask.cancel();
            harvestTask = null;
        }
        nextHarvest.clear();
        if (harvesterEnabled) harvestTask = tasks.timer(40L, 20L, this::harvestCrops);
    }

    /** Composter harvesters: one pass per combo whenever its (upgradable) cooldown has passed. */
    private void harvestCrops() {
        for (Combo combo : List.copyOf(combos.values())) {
            if (combo.type != ComboType.HARVESTER || combo.harvestProfile() == HarvestProfile.PAUSED) continue;
            Block control = loadedControl(combo.key);
            if (control == null || !ComboType.HARVESTER.matches(control)) continue;
            long now = control.getWorld().getGameTime();
            Long due = nextHarvest.get(combo.key);
            if (due != null && now < due) continue;
            nextHarvest.put(combo.key, now + cooldown(control, ComboType.HARVESTER, plugin.comboBaseCooldown(ComboType.HARVESTER)));
            Harvester.run(plugin.core(), control, range(control, ComboType.HARVESTER, plugin.comboBaseRange(ComboType.HARVESTER)),
                    plugin.harvestParticles(), plugin.harvestPauseWhenFull(), combo.harvestProfile(), Harvester.Particles.core(plugin));
        }
        nextHarvest.keySet().retainAll(combos.keySet());
    }

    private void startHealTask() {
        if (healTask != null) {
            healTask.cancel();
            healTask = null;
        }
        if (healEnabled) {
            healTask = tasks.timer(healTaskPeriodTicks, healTaskPeriodTicks, this::healPlayers);
        }
    }

    private void healPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || !player.hasPermission("blockintegrations.use")) {
                continue;
            }
            Block control = findHealingAura(player);
            if (control == null) {
                continue;
            }
            long currentTick = player.getWorld().getGameTime();
            int cooldownLevel = level(control, ComboType.HEAL, ComboUpgrade.COOLDOWN);
            long damageCooldown = ComboScaling.cooldown(healDamageCooldownTicks,
                    cooldownLevel, cooldownReductionPerLevel, minimumCooldownTicks);
            Long damagedAt = lastDamageTick.get(player.getUniqueId());
            if (damagedAt != null && currentTick - damagedAt < damageCooldown) {
                continue;
            }
            long pulseCooldown = ComboScaling.cooldown(healPulseCooldownTicks,
                    cooldownLevel, cooldownReductionPerLevel, minimumCooldownTicks);
            Long healedAt = lastHealTick.get(player.getUniqueId());
            if (healedAt != null && currentTick - healedAt < pulseCooldown) {
                continue;
            }
            AttributeInstance maximumHealth = player.getAttribute(plugin.core().compat().server().attribute("max_health", "generic.max_health"));
            if (maximumHealth == null || player.getHealth() <= 0.0D
                    || player.getHealth() >= maximumHealth.getValue()) {
                continue;
            }
            double amount = power(control, ComboType.HEAL, healAmount);
            player.setHealth(Math.min(maximumHealth.getValue(), player.getHealth() + amount));
            lastHealTick.put(player.getUniqueId(), currentTick);
            if (healParticles) {
                plugin.core().particle().spawn(Particle.HEART,
                        control.getLocation().add(0.5D, 1.15D, 0.5D),
                        3, 0.25D, 0.15D, 0.25D, 0.0D, null);
            }
            if (healSound) {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                        0.45F, 1.55F);
            }
        }
    }

    private Block findHealingAura(Player player) {
        Block closest = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        for (Combo combo : List.copyOf(combos.values())) {
            if (combo.type != ComboType.HEAL
                    || !combo.key.worldId().equals(player.getWorld().getUID())) {
                continue;
            }
            Block control = loadedControl(combo.key);
            if (control == null || !ComboType.HEAL.matches(control)) {
                continue;
            }
            int radius = range(control, ComboType.HEAL, healAuraRadius);
            double distanceSquared = player.getLocation().distanceSquared(
                    control.getLocation().add(0.5D, 1.0D, 0.5D));
            if (distanceSquared <= (double) radius * radius
                    && distanceSquared < closestDistanceSquared) {
                closest = control;
                closestDistanceSquared = distanceSquared;
            }
        }
        return closest;
    }

    private Block completedHealControl(Block placed) {
        if (ComboType.HEAL.matches(placed)) {
            return placed;
        }
        Block above = placed.getRelative(0, 1, 0);
        return ComboType.HEAL.matches(above) ? above : null;
    }

    private boolean hasNearbyHealBlock(Block control) {
        BlockKey currentKey = BlockKey.of(control);
        for (Combo combo : combos.values()) {
            if (combo.type != ComboType.HEAL || combo.key.equals(currentKey)
                    || !combo.key.worldId().equals(currentKey.worldId())) {
                continue;
            }
            if (ComboScaling.tooClose(currentKey.x(), currentKey.y(), currentKey.z(),
                    combo.key.x(), combo.key.y(), combo.key.z(), healMinimumDistance)) {
                return true;
            }
        }

        World world = control.getWorld();
        int minimumSquared = healMinimumDistance * healMinimumDistance;
        for (int x = -healMinimumDistance + 1; x < healMinimumDistance; x++) {
            for (int y = -healMinimumDistance + 1; y < healMinimumDistance; y++) {
                int blockY = control.getY() + y;
                if (blockY < world.getMinHeight() || blockY >= world.getMaxHeight()) {
                    continue;
                }
                for (int z = -healMinimumDistance + 1; z < healMinimumDistance; z++) {
                    int distanceSquared = x * x + y * y + z * z;
                    if (distanceSquared == 0 || distanceSquared >= minimumSquared) {
                        continue;
                    }
                    int blockX = control.getX() + x;
                    int blockZ = control.getZ() + z;
                    if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                        continue;
                    }
                    if (ComboType.HEAL.matches(world.getBlockAt(blockX, blockY, blockZ))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** {@code combos.json}, schema 1: block combos by block key with builder and upgrade levels. */
    /**
     * {@code warpAccess} (2.29.0, owner UUID to guest UUIDs) is simply missing in older files; older versions ignore it.
     */
    record CombosState(Map<String, ComboRecord> combos, Map<String, List<String>> warpAccess) {
        CombosState {
            combos = combos == null ? Map.of() : Map.copyOf(combos);
            warpAccess = warpAccess == null ? Map.of() : Map.copyOf(warpAccess);
        }

        CombosState(Map<String, ComboRecord> combos) {
            this(combos, Map.of());
        }

        static CombosState empty() {
            return new CombosState(Map.of());
        }
    }

    /** {@code name} (warp pad) and {@code xp} (XP collector) since 2.29.0; {@code null} in older entries. */
    record ComboRecord(String type, String owner, Map<String, Integer> levels, String name, Integer xp, String harvestProfile) {
        ComboRecord {
            levels = levels == null ? Map.of() : Map.copyOf(levels);
        }

        ComboRecord(String type, String owner, Map<String, Integer> levels) {
            this(type, owner, levels, null, null);
        }

        ComboRecord(String type, String owner, Map<String, Integer> levels, String name, Integer xp) {
            this(type, owner, levels, name, xp, null);
        }
    }

    static final int STATE_SCHEMA = 1;

    private void loadCombos() {
        combos.clear();
        warpGuests.clear();
        CombosState state = store.load();
        for (Map.Entry<String, List<String>> access : state.warpAccess().entrySet()) {
            try {
                java.util.Set<UUID> guests = new java.util.LinkedHashSet<>();
                if (access.getValue() != null) for (String guest : access.getValue()) guests.add(UUID.fromString(guest));
                if (!guests.isEmpty()) warpGuests.put(UUID.fromString(access.getKey()), guests);
            } catch (RuntimeException exception) {
                DebugMessages.mid("[BlockIntegrations/ComboManager] Invalid warp pad access entry in combos.json: " + access.getKey());
            }
        }
        for (Map.Entry<String, ComboRecord> saved : state.combos().entrySet()) {
            try {
                BlockKey key = BlockKey.parse(saved.getKey());
                ComboRecord record = saved.getValue();
                ComboType type = ComboType.byConfigKey(record.type() == null ? "" : record.type());
                if (type == null) {
                    throw new IllegalArgumentException("unknown type");
                }
                Map<ComboUpgrade, Integer> levels = new EnumMap<>(ComboUpgrade.class);
                for (ComboUpgrade upgrade : ComboUpgrade.values()) {
                    levels.put(upgrade, record.levels().getOrDefault(upgrade.configKey, 0));
                }
                Combo loaded = new Combo(key, type, levels, maximumLevel);
                if (record.owner() != null && !record.owner().isBlank()) loaded.owner(UUID.fromString(record.owner()));
                if (type == ComboType.WARP_PAD) loaded.name(WarpPads.cleanName(record.name()));
                if (type == ComboType.XP_COLLECTOR && record.xp() != null) loaded.storedXp(record.xp());
                if (type == ComboType.HARVESTER) loaded.harvestProfile(HarvestProfile.parse(record.harvestProfile()));
                combos.put(key, loaded);
            } catch (RuntimeException exception) {
                DebugMessages.mid("[BlockIntegrations/ComboManager] Invalid combination entry in combos.json: " + saved.getKey());
            }
        }
    }

    private void queueSave() {
        if (saveQueued || !plugin.isEnabled()) {
            return;
        }
        saveQueued = true;
        tasks.now(() -> {
            saveQueued = false;
            saveCombos();
        });
    }

    private void saveCombos() {
        Map<String, ComboRecord> records = new java.util.LinkedHashMap<>();
        for (Combo combo : combos.values()) {
            Map<String, Integer> levels = new java.util.TreeMap<>();
            for (ComboUpgrade upgrade : ComboUpgrade.values()) {
                if (combo.type.supports(upgrade)) {
                    levels.put(upgrade.configKey, combo.level(upgrade));
                }
            }
            records.put(combo.key.serialize(), new ComboRecord(combo.type.configKey,
                    combo.owner() == null ? null : combo.owner().toString(), levels, combo.name(),
                    combo.type == ComboType.XP_COLLECTOR ? Integer.valueOf(combo.storedXp()) : null,
                    combo.type == ComboType.HARVESTER ? combo.harvestProfile().key : null));
        }
        Map<String, List<String>> access = new java.util.TreeMap<>();
        warpGuests.forEach((owner, guests) -> {
            if (!guests.isEmpty()) access.put(owner.toString(), guests.stream().map(UUID::toString).toList());
        });
        store.save(new CombosState(records, access));
    }

    private int upgradeCost(ComboUpgrade upgrade, int currentLevel) {
        List<Integer> costs = upgradeCosts.get(upgrade);
        return costs.get(Math.min(currentLevel, costs.size() - 1));
    }

    private boolean takeExperience(Player player, int cost) {
        if (cost <= 0 || player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        return plugin.core().player().withdrawExperience(player, cost);
    }

    private List<Integer> defaultCosts(ComboUpgrade upgrade) {
        return switch (upgrade) {
            case RANGE -> List.of(50, 75, 110, 150, 200, 275, 375, 500, 650, 850);
            case POWER -> List.of(75, 110, 160, 225, 300, 400, 525, 675, 850, 1050);
            case DURATION -> List.of(60, 90, 130, 180, 250, 335, 440, 570, 725, 900);
            case COOLDOWN -> List.of(50, 80, 120, 170, 230, 310, 410, 530, 675, 850);
        };
    }

    /** A saved machine blueprint: the combo type, its upgrade levels and (harvesters) the harvest profile. */
    record ComboPrint(ComboType type, EnumMap<ComboUpgrade, Integer> levels, HarvestProfile profile) {}

    /** Blueprints saved per player this server session (machine blueprints). */
    private final Map<UUID, ComboPrint> blueprints = new java.util.concurrent.ConcurrentHashMap<>();

    private static ComboPrint blueprintOf(Combo combo) {
        EnumMap<ComboUpgrade, Integer> levels = new EnumMap<>(ComboUpgrade.class);
        for (ComboUpgrade upgrade : ComboUpgrade.values()) if (combo.type.supports(upgrade)) levels.put(upgrade, combo.level(upgrade));
        return new ComboPrint(combo.type, levels, combo.harvestProfile());
    }

    /** Raises this combo to the player's blueprint, one paid upgrade at a time, and copies the harvest profile. */
    private void applyBlueprint(Player player, BlockKey key) {
        Lang lang = plugin.lang();
        ComboPrint print = blueprints.get(player.getUniqueId());
        Combo combo = validCombo(key);
        if (print == null || combo == null || print.type() != combo.type) { lang.send(player, "combo.incomplete"); return; }
        int done = 0; long spent = 0; UpgradeResult last = null;
        for (ComboUpgrade step : MachineBlueprints.steps(print.levels(), blueprintOf(combo).levels())) {
            last = applyUpgrade(player, key, step, -1, 0, false);
            if (!last.done()) break;
            done++; spent += last.cost();
        }
        if (combo.type == ComboType.HARVESTER && mayManage(player, combo) && combo.harvestProfile() != print.profile()) {
            combo.harvestProfile(print.profile());
            queueSave();
        }
        if (done > 0) player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7F, 1.35F);
        if (last == null || last.done()) lang.send(player, "blueprint.applied", "steps", done, "cost", spent);
        else if (last.outcome() == UpgradeResult.Outcome.NEED_XP) lang.send(player, "blueprint.partial", "steps", done, "cost", spent, "need", last.cost());
        else lang.send(player, "blueprint.stopped", "steps", done, "cost", spent);
        Combo after = validCombo(key);
        if (after != null) openMenu(player, after);
    }

    /** Owner or admin: renames warp pads and takes XP out of collectors. Ownerless combos are claimed on first use. */
    private boolean mayManage(Player player, Combo combo) {
        return player.hasPermission(BlockIntegrations.ADMIN_PERMISSION) || player.getUniqueId().equals(combo.owner());
    }

    /** Lodestones and enchanting tables are everyday blocks: no diagnostic without a second structural clue. */
    private static boolean commonControl(ComboType type) {
        return type == ComboType.WARP_PAD || type == ComboType.XP_COLLECTOR;
    }

    // ---------------------------------------------------------------------------------------------
    // XP collector

    private int xpCapacity(Combo combo) {
        return XpCollector.capacity(xpBaseCapacity, xpCapacityPerLevel, combo.level(ComboUpgrade.POWER));
    }

    private void startXpTask() {
        if (xpTask != null) {
            xpTask.cancel();
            xpTask = null;
        }
        if (xpCollectorsEnabled) xpTask = tasks.timer(30L, 10L, this::collectXp);
    }

    /** Every half second: each complete, loaded collector pulls the orbs around it. */
    private void collectXp() {
        XpCollector.Particles effects = xpParticles ? XpCollector.Particles.core(plugin) : null;
        for (Combo combo : List.copyOf(combos.values())) {
            if (combo.type != ComboType.XP_COLLECTOR) continue;
            Block control = loadedControl(combo.key);
            if (control == null || !ComboType.XP_COLLECTOR.matches(control)) continue;
            int before = combo.storedXp();
            int after = XpCollector.run(plugin.core(), control, range(control, ComboType.XP_COLLECTOR, plugin.comboBaseRange(ComboType.XP_COLLECTOR)),
                    before, xpCapacity(combo), xpPlayerSafeRadius, deathOrbs, effects);
            if (after != before) {
                combo.storedXp(after);
                queueXpSave();
            }
        }
    }

    private void queueXpSave() {
        if (xpSaveQueued || !plugin.isEnabled()) return;
        xpSaveQueued = true;
        tasks.later(100L, () -> {
            xpSaveQueued = false;
            saveCombos();
        });
    }

    /** A player's death drop is his: XP collectors leave the orbs around the death place alone for a while. */
    /* Stage OBSERVE, registered in handleSharedEvents */
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (event.getDroppedExp() <= 0) return;
        Location at = event.getEntity().getLocation();
        deathOrbs.died(at.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), System.currentTimeMillis());
        Bukkit.getScheduler().runTask(plugin, () -> BukkitFeatures.markDeathOrbs(at));
    }

    /** A collector that is gone spills what it held on top of its block (nothing is lost). */
    private void spillStoredXp(Combo combo) {
        if (combo.type != ComboType.XP_COLLECTOR || combo.storedXp() <= 0) return;
        Block control = loadedControl(combo.key);
        int stored = combo.storedXp();
        combo.storedXp(0);
        if (control == null) return;
        XpCollector.spillAt(control.getLocation().add(0.5D, 1.0D, 0.5D), stored);
        DebugMessages.low("[ComboManager] XP collector removed, spilled " + stored + " XP");
    }

    /** Menu button: one level (or everything) for the owner; checked again here on click. */
    private void takeXp(Player player, BlockKey key, boolean all) {
        Combo combo = validCombo(key);
        if (combo == null || combo.type != ComboType.XP_COLLECTOR) {
            plugin.lang().send(player, "combo.incomplete");
            return;
        }
        if (!mayManage(player, combo)) {
            plugin.lang().send(player, "combo.xp-collector.owner-only");
            return;
        }
        if (!ChatLinks.near(plugin, player, key, "chat.too-far.combo")) return;
        int wanted = all ? combo.storedXp() : XpCollector.pointsToNextLevel(player.getExpToLevel(), player.getExp());
        int payout = Math.min(combo.storedXp(), wanted);
        if (payout <= 0) {
            plugin.lang().send(player, "combo.xp-collector.empty");
            return;
        }
        combo.storedXp(combo.storedXp() - payout);
        plugin.core().player().giveExperience(player, payout);
        queueSave();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.4F);
        plugin.lang().send(player, "combo.xp-collector.taken", "points", payout, "left", combo.storedXp());
        openMenu(player, combo);
    }

    // ---------------------------------------------------------------------------------------------
    // Warp pads

    /** The warp pad limit of a player, -1 for none (admins, or max-per-player 0). */
    int warpPadLimit(Player player) {
        return warpMaxPerPlayer <= 0 || player.hasPermission(BlockIntegrations.ADMIN_PERMISSION) ? -1 : warpMaxPerPlayer;
    }

    private boolean warpLimitReached(Player player) {
        if (warpMaxPerPlayer <= 0 || player.hasPermission(BlockIntegrations.ADMIN_PERMISSION)) return false;
        UUID id = player.getUniqueId();
        return combos.values().stream().filter(c -> c.type == ComboType.WARP_PAD && id.equals(c.owner())).count() >= warpMaxPerPlayer;
    }

    /** Pad name, or its coordinates when it has none. */
    String warpName(CommandSender viewer, Combo combo) {
        return combo.name() != null ? combo.name()
                : plugin.lang().plain(viewer, "combo.warp-pad.unnamed", "x", combo.key.x(), "y", combo.key.y(), "z", combo.key.z());
    }

    private void promptRename(Player player, BlockKey key) {
        Combo combo = validCombo(key);
        if (combo == null || combo.type != ComboType.WARP_PAD || !mayManage(player, combo)) {
            plugin.lang().send(player, "combo.warp-pad.not-yours");
            return;
        }
        Lang lang = plugin.lang();
        plugin.core().dialogs().prompt(plugin, player, lang.ref("combo.warp-pad.rename.title"), lang.ref("combo.warp-pad.rename.question"),
                combo.name() == null ? "" : combo.name(), text -> rename(player, key, text), () -> {},
                p -> p.isOnline() && validCombo(key) == combo);
    }

    private void rename(Player player, BlockKey key, String text) {
        Combo combo = validCombo(key);
        if (combo == null || combo.type != ComboType.WARP_PAD || !mayManage(player, combo)) return;
        String name = WarpPads.cleanName(text);
        if (name == null) {
            plugin.lang().send(player, "combo.warp-pad.rename.invalid", "max", WarpPads.MAX_NAME_LENGTH);
            return;
        }
        combo.name(name);
        queueSave();
        plugin.structuresChanged(combo.owner());
        plugin.lang().send(player, "combo.warp-pad.rename.done", "name", name);
    }

    /** Sneaking on top of a warp pad opens the travel dialog. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarpSneak(PlayerToggleSneakEvent event) {
        if (!warpPadsEnabled || !event.isSneaking()) return;
        Player player = event.getPlayer();
        if (player.isInsideVehicle() || player.isFlying() || !player.hasPermission(WARP_PERMISSION)) return;
        Block below = player.getLocation().getBlock().getRelative(0, -1, 0);
        Combo pad = combos.get(BlockKey.of(below));
        if (pad == null || pad.type != ComboType.WARP_PAD || !WarpPads.standingOn(player.getLocation(), pad.key)) return;
        long now = plugin.core().compat().platform().getCurrentTick();
        Long next = nextWarpPrompt.get(player.getUniqueId());
        if (next != null && now < next) return;
        nextWarpPrompt.put(player.getUniqueId(), now + 20L);
        openWarpDialog(player, pad);
    }

    private boolean mayTravel(Player player, Combo pad) {
        return WarpPads.mayUse(player.getUniqueId(), pad.owner(), pad.owner() == null ? null : warpGuests.get(pad.owner()));
    }

    private int warpReach(Combo pad) {
        return WarpPads.reach(warpBaseDistance, warpDistancePerLevel, pad.level(ComboUpgrade.RANGE));
    }

    private void openWarpDialog(Player player, Combo source) {
        Lang lang = plugin.lang();
        if (validCombo(source.key) != source) {
            lang.send(player, "combo.incomplete");
            return;
        }
        if (!mayTravel(player, source)) {
            lang.send(player, "combo.warp-pad.no-access");
            return;
        }
        int reach = warpReach(source);
        List<Combo> targets = WarpPads.destinations(source.key, combos.values(), c -> c.key,
                c -> c.type == ComboType.WARP_PAD && mayTravel(player, c), reach);
        if (targets.isEmpty()) {
            lang.send(player, "combo.warp-pad.no-destinations", "blocks", reach);
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(lang.component(player, "combo.warp-pad.travel.body",
                "name", warpName(player, source), "blocks", reach)));
        List<DialogButton> buttons = new ArrayList<>();
        BlockKey sourceKey = source.key;
        for (Combo target : targets) {
            BlockKey targetKey = target.key;
            String owner = player.getUniqueId().equals(target.owner()) ? lang.plain(player, "combo.warp-pad.travel.own")
                    : lang.plain(player, "combo.warp-pad.travel.shared", "owner", ownerName(target.owner()));
            buttons.add(new DialogButton(lang.component(player, "combo.warp-pad.travel.button", "name", warpName(player, target),
                    "distance", (int) Math.round(WarpPads.distance(sourceKey, targetKey))),
                    lang.component(player, "combo.warp-pad.travel.tooltip", "owner", owner,
                            "x", targetKey.x(), "y", targetKey.y(), "z", targetKey.z()),
                    (p, r) -> warp(p, sourceKey, targetKey)));
        }
        plugin.core().dialogs().open(plugin, player, new DialogRequest(lang.component(player, "combo.warp-pad.travel.title"),
                body, List.of(), buttons, p -> p.isOnline() && validCombo(sourceKey) == source, () -> {},
                BlockIntegrations.CLOSE_LABEL, 2));
    }

    private static String ownerName(UUID owner) {
        if (owner == null) return "?";
        String name = Bukkit.getOfflinePlayer(owner).getName();
        return name == null ? owner.toString().substring(0, 8) : name;
    }

    /** Checks everything again on click, loads the target chunk without blocking and teleports on top of the pad. */
    private void warp(Player player, BlockKey sourceKey, BlockKey targetKey) {
        Lang lang = plugin.lang();
        Combo source = validCombo(sourceKey);
        Combo target = combos.get(targetKey);
        if (source == null || source.type != ComboType.WARP_PAD || target == null || target.type != ComboType.WARP_PAD) {
            lang.send(player, "combo.warp-pad.gone");
            return;
        }
        if (!WarpPads.standingOn(player.getLocation(), sourceKey)) {
            lang.send(player, "combo.warp-pad.stand-on-pad");
            return;
        }
        if (!player.hasPermission(WARP_PERMISSION) || !mayTravel(player, source) || !mayTravel(player, target)) {
            lang.send(player, "combo.warp-pad.no-access");
            return;
        }
        if (WarpPads.distance(sourceKey, targetKey) > warpReach(source)) {
            lang.send(player, "combo.warp-pad.too-far", "blocks", warpReach(source));
            return;
        }
        long now = plugin.core().compat().platform().getCurrentTick();
        Long ready = nextWarp.get(player.getUniqueId());
        if (ready != null && now < ready) {
            lang.send(player, "combo.warp-pad.cooldown", "seconds", decimal(player, (ready - now) / 20.0D));
            return;
        }
        World world = Bukkit.getWorld(targetKey.worldId());
        if (world == null) {
            lang.send(player, "combo.warp-pad.gone");
            return;
        }
        Block sourceBlock = loadedControl(sourceKey);
        long cooldown = sourceBlock == null ? plugin.comboBaseCooldown(ComboType.WARP_PAD)
                : cooldown(sourceBlock, ComboType.WARP_PAD, plugin.comboBaseCooldown(ComboType.WARP_PAD));
        nextWarp.put(player.getUniqueId(), now + cooldown);
        UUID playerId = player.getUniqueId();
        plugin.core().compat().platform().getChunkAtAsync(world, targetKey.chunkX(), targetKey.chunkZ()).thenAccept(chunk -> {
            // Paper completes on the server thread, while the chunk is surely loaded; otherwise hop over.
            if (Bukkit.isPrimaryThread()) {
                if (plugin.isEnabled()) finishWarp(playerId, sourceKey, targetKey);
                return;
            }
            try {
                tasks.now(() -> finishWarp(playerId, sourceKey, targetKey));
            } catch (IllegalStateException disabled) {
                // Plugin is shutting down: no teleport.
            }
        });
    }

    private void finishWarp(UUID playerId, BlockKey sourceKey, BlockKey targetKey) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        Lang lang = plugin.lang();
        Combo target = validCombo(targetKey);
        Block pad = loadedControl(targetKey);
        if (target == null || pad == null || target.type != ComboType.WARP_PAD || !mayTravel(player, target)) {
            nextWarp.remove(playerId);
            lang.send(player, "combo.warp-pad.gone");
            return;
        }
        if (!passable(pad.getRelative(0, 1, 0)) || !passable(pad.getRelative(0, 2, 0))) {
            nextWarp.remove(playerId);
            lang.send(player, "combo.warp-pad.blocked", "name", warpName(player, target));
            return;
        }
        if (!WarpPads.standingOn(player.getLocation(), sourceKey)) {
            nextWarp.remove(playerId);
            lang.send(player, "combo.warp-pad.stand-on-pad");
            return;
        }
        Location from = player.getLocation();
        Location to = pad.getLocation().add(0.5D, 1.0D, 0.5D);
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
        effects(from);
        plugin.core().compat().platform().teleportAsync(player, to, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(moved -> {
            if (!Boolean.TRUE.equals(moved)) return;
            try {
                tasks.now(() -> {
                    Player arrived = Bukkit.getPlayer(playerId);
                    if (arrived == null) return;
                    effects(to);
                    lang.send(arrived, "combo.warp-pad.arrived", "name", warpName(arrived, target));
                });
            } catch (IllegalStateException disabled) {
                // Plugin is shutting down: no effects.
            }
        });
    }

    private static boolean passable(Block block) {
        return WarpPads.passable(block.getType(), block.getType().isSolid());
    }

    private void effects(Location at) {
        World world = at.getWorld();
        if (world == null) return;
        world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.2F);
        if (warpParticles)
            plugin.core().particle().spawn(Particle.REVERSE_PORTAL, at.clone().add(0.0D, 1.0D, 0.0D), 40, 0.3D, 0.6D, 0.3D, 0.05D, null);
    }

    static final String WARP_PERMISSION = "blockintegrations.warppads";

    boolean warpPadsEnabled() {
        return warpPadsEnabled;
    }

    List<Combo> warpPadsOwnedBy(UUID owner) {
        return combos.values().stream().filter(c -> c.type == ComboType.WARP_PAD && owner.equals(c.owner())).sorted(COMBO_ORDER).toList();
    }

    java.util.Set<UUID> warpGuests(UUID owner) {
        return java.util.Collections.unmodifiableSet(warpGuests.getOrDefault(owner, java.util.Set.of()));
    }

    /** Lets {@code guest} travel on all pads of {@code owner} (or takes it back). */
    WarpPads.ShareResult shareWarpPads(UUID owner, UUID guest, boolean allow) {
        java.util.Set<UUID> guests = warpGuests.computeIfAbsent(owner, id -> new java.util.LinkedHashSet<>());
        WarpPads.ShareResult result = WarpPads.share(guests, owner, guest, allow);
        if (guests.isEmpty()) warpGuests.remove(owner);
        if (result == WarpPads.ShareResult.ADDED || result == WarpPads.ShareResult.REMOVED) queueSave();
        return result;
    }

    /** {@code /bi warppads [list|share NAME|unshare NAME]}; the player menu does the same without typing. */
    void warpCommand(Player player, String[] args) {
        Lang lang = plugin.lang();
        if (!warpPadsEnabled()) {
            lang.send(player, "feature-off");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if ((action.equals("share") || action.equals("unshare")) && args.length > 2) {
            org.bukkit.OfflinePlayer guest = Bukkit.getPlayerExact(args[2]);
            if (guest == null) guest = plugin.core().compat().platform().getOfflinePlayerIfCached(args[2]);
            if (guest == null) {
                lang.send(player, "combo.warp-pad.share.unknown", "player", args[2]);
                return;
            }
            String name = guest.getName() == null ? args[2] : guest.getName();
            sendShareResult(player, name, shareWarpPads(player.getUniqueId(), guest.getUniqueId(), action.equals("share")));
            return;
        }
        if (!action.equals("list")) {
            lang.send(player, "combo.warp-pad.usage");
            return;
        }
        List<Combo> pads = warpPadsOwnedBy(player.getUniqueId());
        lang.send(player, "combo.warp-pad.list.header", "count", pads.size(),
                "max", warpMaxPerPlayer <= 0 ? "∞" : Integer.toString(warpMaxPerPlayer));
        if (pads.isEmpty()) lang.send(player, "combo.warp-pad.list.empty");
        for (Combo pad : pads) {
            Component line = lang.component(player, "combo.warp-pad.list.entry", "name", warpName(player, pad),
                    "blocks", warpReach(pad), "state", lang.plain(player, "combo.warp-pad.state." + state(pad)));
            Location location = ChatLinks.location(pad.key);
            plugin.core().compat().platform().sendMessage(player, location == null ? line : line.append(Component.space()).append(plugin.core().message().chat().location(location)));
        }
        List<String> guests = warpGuests(player.getUniqueId()).stream().map(ComboManager::ownerName).sorted().toList();
        if (guests.isEmpty()) lang.send(player, "combo.warp-pad.list.no-guests");
        else lang.send(player, "combo.warp-pad.list.guests", "players", String.join(", ", guests));
        lang.send(player, "combo.warp-pad.list.hint");
    }

    void sendShareResult(Player owner, String guestName, WarpPads.ShareResult result) {
        Lang lang = plugin.lang();
        switch (result) {
            case ADDED -> lang.send(owner, "combo.warp-pad.share.added", "player", guestName);
            case REMOVED -> lang.send(owner, "combo.warp-pad.share.removed", "player", guestName);
            case FULL -> lang.send(owner, "combo.warp-pad.share.full", "max", WarpPads.MAX_GUESTS);
            case SELF -> lang.send(owner, "combo.warp-pad.share.self");
            case UNCHANGED -> lang.send(owner, "combo.warp-pad.share.unchanged", "player", guestName);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Web page (called by BlocksWeb on the server thread)

    private static final java.util.Comparator<Combo> COMBO_ORDER = java.util.Comparator
            .<Combo, String>comparing(c -> c.key.worldId().toString())
            .thenComparingInt(c -> c.key.x()).thenComparingInt(c -> c.key.z()).thenComparingInt(c -> c.key.y());

    List<Combo> combosOwnedBy(UUID owner) {
        return combos.values().stream().filter(c -> owner.equals(c.owner())).sorted(COMBO_ORDER).toList();
    }

    List<Combo> allCombos() {
        return combos.values().stream().sorted(COMBO_ORDER).toList();
    }

    Combo combo(BlockKey key) {
        return combos.get(key);
    }

    /** Remote upgrades from the web page: the builder or an admin (at the block itself every permitted player may). */
    boolean webAccess(Player player, Combo combo) {
        return player.hasPermission(BlockIntegrations.ADMIN_PERMISSION) || player.getUniqueId().equals(combo.owner());
    }

    /** {@code active}, {@code incomplete} or {@code unloaded} (never loads the chunk). */
    String state(Combo combo) {
        Block control = loadedControl(combo.key);
        if (control == null) return "unloaded";
        return ComboType.detect(control) == combo.type ? "active" : "incomplete";
    }

    com.google.gson.JsonObject webView(Combo combo, String language) {
        Lang lang = plugin.lang();
        Block control = loadedControl(combo.key);
        boolean slimeField = combo.type == ComboType.LAUNCHER && control != null && SlimeFieldStructure.complete(control);
        com.google.gson.JsonObject json = WebJson.structure("combo", combo.key, combo.type.configKey,
                lang.plain(language, slimeField ? "combo.slime-field.name" : combo.type.nameKey()), WebJson.hex(combo.type.color));
        json.addProperty("state", state(combo));
        if (combo.owner() != null) {
            String name = Bukkit.getOfflinePlayer(combo.owner()).getName();
            json.addProperty("owner", name == null ? combo.owner().toString().substring(0, 8) : name);
            json.addProperty("ownerId", combo.owner().toString());
        }
        com.google.gson.JsonArray upgrades = new com.google.gson.JsonArray();
        for (ComboUpgrade upgrade : ComboUpgrade.values()) {
            if (!combo.type.supports(upgrade)) continue;
            int level = combo.level(upgrade);
            boolean maxed = level >= maximumLevel;
            upgrades.add(WebJson.upgrade(upgrade.configKey, lang.plain(language, combo.type.upgradeNameKey(upgrade)), level, maximumLevel,
                    upgradeValue(language, combo.type, upgrade, level),
                    maxed ? null : upgradeValue(language, combo.type, upgrade, level + 1),
                    maxed ? null : upgradeCost(upgrade, level)));
        }
        json.add("upgrades", upgrades);
        return json;
    }

    /** Guide of all combos: layout, what they do and upgrade tables from the current settings. */
    com.google.gson.JsonObject webGuide(String language) {
        Lang lang = plugin.lang();
        com.google.gson.JsonObject guide = new com.google.gson.JsonObject();
        com.google.gson.JsonArray types = new com.google.gson.JsonArray();
        for (ComboType type : ComboType.values()) {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("type", type.configKey);
            json.addProperty("name", lang.plain(language, type.nameKey()));
            json.addProperty("color", WebJson.hex(type.color));
            json.addProperty("description", lang.plain(language, "web.guide.combo." + type.configKey));
            json.addProperty("structure", lang.plain(language, "combo." + type.configKey + ".structure"));
            Material[] layout = type.layout();
            com.google.gson.JsonArray blocks = new com.google.gson.JsonArray();
            String[][] side = new String[layout.length][1];
            for (int i = 0; i < layout.length; i++) {
                String key = "b" + i;
                String name = CopperMaterials.isFullBuildingBlock(layout[i]) ? lang.plain(language, "tower.any-copper")
                        : ComboType.anyIce(layout[i]) ? lang.plain(language, "web.guide.any-ice") : TowerType.friendly(layout[i]);
                blocks.add(WebJson.block(key, layout[i], name));
                side[i][0] = key;
            }
            json.add("blocks", blocks);
            com.google.gson.JsonArray views = new com.google.gson.JsonArray();
            views.add(WebJson.view(lang.plain(language, "web.guide.view.side"), side));
            if (type == ComboType.LAUNCHER)
                views.add(WebJson.view(lang.plain(language, "web.guide.view.slime-field"),
                        new String[][] {{"b0", "b0", "b0"}, {"b0", "b0", "b0"}, {"b0", "b0", "b0"}}));
            json.add("views", views);
            com.google.gson.JsonArray upgrades = new com.google.gson.JsonArray();
            for (ComboUpgrade upgrade : ComboUpgrade.values()) {
                if (!type.supports(upgrade)) continue;
                com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
                for (int level = 0; level <= maximumLevel; level++)
                    rows.add(WebJson.levelRow(level, upgradeValue(language, type, upgrade, level),
                            level >= maximumLevel ? null : upgradeCost(upgrade, level)));
                upgrades.add(WebJson.upgradeTable(upgrade.configKey, lang.plain(language, type.upgradeNameKey(upgrade)), maximumLevel, rows));
            }
            json.add("upgrades", upgrades);
            types.add(json);
        }
        guide.add("types", types);
        List<String> notes = new ArrayList<>();
        notes.add(lang.plain(language, "combo.guide.open"));
        notes.add(lang.plain(language, "combo.guide.paths", "max", maximumLevel));
        notes.add(lang.plain(language, "combo.slime-field.info"));
        notes.add(lang.plain(language, "combo.guide.float"));
        notes.add(lang.plain(language, "combo.guide.heal",
                "seconds", decimal(language, healDamageCooldownTicks / 20.0D), "distance", healMinimumDistance));
        if (warpPadsEnabled) notes.add(lang.plain(language, "combo.guide.warp"));
        if (xpCollectorsEnabled) notes.add(lang.plain(language, "combo.guide.xp"));
        guide.add("notes", WebJson.strings(notes));
        return guide;
    }

    private String decimal(String language, double value) {
        return plugin.number(language, "%.1f", value);
    }

    private static double bounded(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** One decimal place with the viewer's decimal separator. */
    private String decimal(CommandSender viewer, double value) {
        return plugin.number(viewer, "%.1f", value);
    }
}
