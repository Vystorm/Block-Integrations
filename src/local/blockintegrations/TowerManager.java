package local.blockintegrations;

import Vystorm.vystorm_core.schedule.TaskGroup;
import Vystorm.vystorm_core._uiMethods.autoGui.GuiCallbacks;
import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.compat.dialog.DialogButton;
import Vystorm.vystorm_core.compat.dialog.DialogRequest;

import Vystorm.vystorm_core._parsersDebuggerEtc.debugger.DebugMessages;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import Vystorm.vystorm_core.compat.dialog.DialogBody;
import net.kyori.adventure.text.Component;

final class TowerManager implements Listener {
    void registerStorageDiagnostics() { store.registerDiagnostics(plugin.core().admin().diagnostics(), plugin); }

    private final BlockIntegrations plugin;
    private final Map<BlockKey, Tower> towers = new HashMap<>();
    private final Map<BlockKey, Inventory> lootInventories = new HashMap<>();
    private final Map<UUID, TowerDamageCredit> towerDamageCredits = new HashMap<>();
    private final Set<UUID> towerDamageInProgress = new HashSet<>();
    private final Map<TowerType, TowerCombatStats> combatStats = new EnumMap<>(TowerType.class);
    private final Map<TowerUpgrade, List<Integer>> upgradeCosts = new EnumMap<>(TowerUpgrade.class);
    /** {@code towers.json} as Core VersionedStore (atomic writes, schema envelope). */
    private final StateFile<TowersState> store;

    private boolean enabled;
    private boolean pauseWhenLootFull;
    private int maximumUpgradeLevel;
    private long attackTaskPeriod;
    private int lootInventorySize;
    private long lootCreditDurationTicks;
    private boolean captureNearbyPluginDrops;
    private double nearbyPluginDropRadius;
    private boolean dropLootWhenBroken;
    // Besitz, XP-Regeln und Zielauswahl (siehe config towers.*)
    private int maximumTowersPerPlayer;
    private boolean experienceRequiresPlayer;
    private double experiencePlayerRadius;
    private boolean experienceFromSpawners;
    private boolean lineOfSight;
    private boolean ignoreNamedMobs;
    private long saveDelayTicks;
    /** Wer zuletzt einen Turmkopf gesetzt hat (für den Besitz bei der Erkennung). */
    private final Map<BlockKey, UUID> recentPlacers = new HashMap<>();
    private static final long LOOT_FULL_NOTICE_MILLIS = 10L * 60L * 1000L;
    private final Map<BlockKey, Long> lootFullNotices = new HashMap<>();
    /** Core task group of this manager (attack loop, deferred block checks, batched saves); closed on shutdown. */
    private final TaskGroup tasks;
    private final TowerEffects effects;
    private BukkitTask attackTask;
    private boolean saveQueued;
    private long nextSaveRevision;
    private long lastWrittenRevision;
    private long towerTick;
    private long attackCycles;
    private long attacksFired;

    private record TowerDamageCredit(BlockKey towerKey, long expiresAtTick) {
    }

    TowerManager(BlockIntegrations plugin) {
        this.plugin = plugin;
        this.tasks = plugin.core().tasks().of(plugin, "BlockIntegrations/TowerManager");
        this.effects = new TowerEffects(plugin);
        this.store = new StateFile<>(new File(plugin.getDataFolder(), "towers.json").toPath(), "BlockIntegrations/TowerManager",
                TowersState.class, STATE_SCHEMA, STATE_MIGRATIONS, TowersState::empty, plugin.core().data().files());
    }

    /**
     * The other shared events go through Core's stages too. Explosions, pistons and fluids may not take a tower
     * apart (PROTECT); the sneak click on a tower is the feature acting on an allowed click (ACT); the kill credit
     * follows the final damage (OBSERVE). None of the handlers sees a cancelled event. Taking over the drops of a
     * tower kill stays a listener at MONITOR, see {@link #onEntityDeath}.
     */
    void handleSharedEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, org.bukkit.event.entity.EntityExplodeEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.PROTECT, this::onEntityExplode);
        stages.handle(plugin, org.bukkit.event.block.BlockExplodeEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.PROTECT, this::onBlockExplode);
        stages.handle(plugin, org.bukkit.event.block.BlockPistonExtendEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.PROTECT, this::onPistonExtend);
        stages.handle(plugin, org.bukkit.event.block.BlockPistonRetractEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.PROTECT, this::onPistonRetract);
        stages.handle(plugin, org.bukkit.event.block.BlockFromToEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.PROTECT, this::onFluid);
        stages.handle(plugin, PlayerInteractEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.ACT, this::onTowerInteract);
        stages.handle(plugin, EntityDamageEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onEntityDamaged);
    }
    void start() {
        loadSettings();
        loadTowers();
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
        handleBlockEvents(plugin.core().priority().stages());
        handleSharedEvents(plugin.core().priority().stages());
        scanLoadedChunks();
        startAttackTask();
    }

    void shutdown() {
        effects.close();
        tasks.close();
        attackTask = null;
        closeAndSyncLootInventories();
        saveTowers();
    }

    void reloadSettings() {
        closeAndSyncLootInventories();
        loadSettings();
        validateLoadedTowers();
        scanLoadedChunks();
        startAttackTask();
    }

    int towerCount() {
        return towers.size();
    }

    long attackCycles() {
        return attackCycles;
    }

    long attacksFired() {
        return attacksFired;
    }

    int maximumUpgradeLevel() {
        return maximumUpgradeLevel;
    }

    void sendTowerGuide(CommandSender sender) {
        towerGuideLines(sender).forEach(line -> plugin.core().compat().platform().sendMessage(sender, line));
    }

    List<Component> towerGuideLines(CommandSender viewer) {
        Lang lang = plugin.lang();
        List<Component> lines = new ArrayList<>();
        lines.add(lang.component(viewer, "tower.guide.header"));
        lines.add(lang.component(viewer, "tower.guide.intro"));
        for (TowerType type : TowerType.values()) {
            lines.add(structureLine(viewer, type));
        }
        lines.add(lang.component(viewer, "tower.guide.levels", "max", maximumUpgradeLevel,
                "looting", maximumLevel(TowerUpgrade.LOOTING)));
        lines.add(lang.component(viewer, "tower.guide.loot"));
        lines.add(lang.component(viewer, "tower.guide.ring"));
        return lines;
    }

    /** Coloured tower name in the viewer's language. */
    private Component name(CommandSender viewer, TowerType type) {
        return plugin.lang().component(viewer, type.nameKey()).colorIfAbsent(type.color);
    }

    private String plainName(CommandSender viewer, TowerType type) {
        return plugin.lang().plain(viewer, type.nameKey());
    }

    /** Building instructions of one tower type (block names as Minecraft material names). */
    Component structureLine(CommandSender viewer, TowerType type) {
        Lang lang = plugin.lang();
        Object foundation = type == TowerType.ARCHER
                ? lang.component(viewer, "tower.any-copper") : materialName(viewer, type.foundation);
        return lang.component(viewer, "tower.guide.structure", "name", name(viewer, type),
                "core", materialName(viewer, type.core), "pedestal", materialName(viewer, type.pedestal),
                "foundation", foundation, "ring", materialName(viewer, type.ring),
                "upgrades", lang.component(viewer, type.upgradeable ? "tower.guide.upgradeable" : "tower.guide.starter"));
    }

    /** Players see Minecraft's own block names; the console keeps a readable fallback. */
    private static Component materialName(CommandSender viewer, Material material) {
        return viewer instanceof Player
                ? Component.translatable(material.getTranslationKey())
                : Component.text(TowerType.friendly(material));
    }

    /**
     * Block break and place go through Core's stages, so Core decides when they run among all Vystorm plugins.
     * A tower of another owner denies its break first (PROTECT); the stored towers follow the final outcome in
     * OBSERVE. None of the handlers sees a cancelled event.
     */
    void handleBlockEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, BlockBreakEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.PROTECT, this::onBreakOthers);
        stages.handle(plugin, BlockPlaceEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onBlockPlace);
        stages.handle(plugin, BlockBreakEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onBlockBreak);
    }

    /** Stage OBSERVE, registered in {@link #handleBlockEvents}. */
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event instanceof BuildProbeEvent) return; // test placement, nothing was placed
        if (!enabled || event.isCancelled() || !event.canBuild()) return;
        ItemStack item = event.getItemInHand().clone();
        int before = towers.size();
        BlockKey placedKey = BlockKey.of(event.getBlockPlaced());
        if (TowerType.isCore(event.getBlockPlaced().getType()))
            recentPlacers.put(placedKey, event.getPlayer().getUniqueId());
        Tower previous = towers.get(placedKey);
        Tower restored = restorePortable(event.getBlockPlaced(), item);
        tasks.now(() -> {
            if (event.isCancelled() || !event.canBuild()) {
                if (restored != null && towers.remove(placedKey, restored)) {
                    if (previous != null) towers.put(placedKey, previous);
                    queueSave();
                }
                return;
            }
            scheduleScanAround(event.getBlockPlaced());
            tasks.now(() -> {
                recentPlacers.remove(placedKey);
                if (towers.size() > before) {
                    Tower built = towers.get(placedKey);
                    Player builder = event.getPlayer();
                    Lang lang = plugin.lang();
                    Component message = lang.component(builder, "tower.built");
                    plugin.core().compat().platform().sendMessage(builder, built == null ? message : message.append(plugin.core().message().chat().button(
                            lang.plain(builder, "chat.upgrades"), lang.plain(builder, "tower.open-hover"),
                            p -> openTowerFromChat(p, placedKey))));
                }
            });
        });
    }

    /** Stage OBSERVE, registered in {@link #handleBlockEvents}. */
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled || event.isCancelled()) return;
        BlockKey key = BlockKey.of(event.getBlock());
        Tower broken = towers.get(key);
        tasks.now(() -> {
            if (event.isCancelled()) return;
            if (broken != null && towers.get(key) == broken) removeTower(key);
            scheduleScanAround(event.getBlock());
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (!enabled || event.isCancelled()) return;
        BlockKey key = BlockKey.of(event.getBlock());
        Tower tower = towers.get(key);
        if (tower == null || event.getBlockState().getType() != tower.type.core) return;
        Map<String, Integer> levels = new HashMap<>();
        for (TowerUpgrade upgrade : TowerUpgrade.values()) levels.put(upgrade.configKey, tower.level(upgrade));
        for (Item drop : event.getItems()) {
            ItemStack item = drop.getItemStack();
            if (item.getType() != tower.type.core || item.getAmount() != 1) continue;
            drop.setItemStack(plugin.portableBlocks().write(item, event.getPlayer(), "tower", tower.type.configKey, levels, null, 0));
            DebugMessages.low("[TowerManager] Tower upgrades stored on the item: " + tower.type.configKey);
            break;
        }
        // Loot and kill XP retain their configured, separate drops; never copy them into the head as well.
        removeTower(key);
    }

    private Tower restorePortable(Block core, ItemStack item) {
        PortableBlockData.State state = plugin.portableBlocks().read(item);
        if (state == null || !state.kind().equals("tower") || state.material() != core.getType()) return null;
        TowerType type = TowerType.byConfigKey(state.type());
        if (type == null || core.getType() != type.core) return null;
        BlockKey key = BlockKey.of(core);
        Map<String, Integer> levels = state.levels();
        Tower restored = new Tower(key, type, levels.getOrDefault("damage", 0),
                levels.getOrDefault("range", 0), levels.getOrDefault("speed", 0),
                levels.getOrDefault("looting", 0), maximumUpgradeLevel, 0, new ItemStack[0]);
        restored.owner(recentPlacers.get(key));
        towers.put(key, restored);
        queueSave();
        plugin.structuresChanged(restored.owner());
        DebugMessages.low("[TowerManager] Moved tower restored: " + type.configKey);
        return restored;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (enabled) {
            tasks.now(() -> scanChunk(event.getChunk()));
        }
    }

    /* Stage ACT, registered in handleSharedEvents */
    public void onTowerInteract(PlayerInteractEvent event) {
        if (!enabled || event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()
                || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || !TowerType.isCore(event.getClickedBlock().getType())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("blockintegrations.towers")) {
            plugin.lang().send(player, "tower.no-use");
            return;
        }
        Block core = event.getClickedBlock();
        BlockKey key = BlockKey.of(core);
        Tower registered = towers.get(key);
        TowerType type = registered == null ? TowerType.byCore(core.getType()) : registered.type;
        if (type == null) return;
        if (registered != null && !canInspectIncomplete(player, registered, core)) {
            plugin.lang().send(player, "tower.owned-by", "owner", ownerName(player, registered.owner()));
            return;
        }
        if (registered == null && !MachineDiagnostics.hasTowerEvidence(type, core)) return;
        MachineDiagnostics.Report diagnostic = MachineDiagnostics.tower(type, core);
        if (diagnostic.state() == MachineDiagnostics.State.UNLOADED) {
            event.setCancelled(true);
            plugin.lang().send(player, "machine-diagnostic.unloaded", "machine",
                    plugin.lang().plain(player, type.nameKey()));
            return;
        }
        if (diagnostic.state() == MachineDiagnostics.State.INCOMPLETE) {
            event.setCancelled(true);
            sendMachineDiagnostic(player, type, diagnostic);
            return;
        }
        Tower tower = recognize(core, null);
        if (tower == null) return;
        event.setCancelled(true);
        if (!mayUse(player, tower, true)) return;
        showRange(player, tower);
        openMenu(player, tower);
    }

    /** Pure counterpart of mayUse for diagnostics; unlike mayUse it never claims an ownerless tower. */
    private boolean canInspectIncomplete(Player player, Tower tower, Block core) {
        if (player.hasPermission("blockintegrations.admin") || tower.owner() == null
                || player.getUniqueId().equals(tower.owner()) || tower.controls.allows(player.getUniqueId(), TowerControls.Right.USE)) return true;
        java.util.Optional<String> city = cityAt(core.getLocation());
        return city.isPresent() && city.equals(cityAt(player.getLocation()));
    }

    private void sendMachineDiagnostic(Player player, TowerType type, MachineDiagnostics.Report report) {
        Lang lang = plugin.lang();
        String machine = lang.plain(player, type.nameKey());
        lang.send(player, "machine-diagnostic.header", "machine", machine);
        for (MachineDiagnostics.Finding finding : report.findings()) {
            lang.send(player, "machine-diagnostic.finding",
                    "part", lang.plain(player, "machine-diagnostic.part." + finding.part()),
                    "expected", MachineDiagnostics.materialName(player, finding.expected()),
                    "actual", MachineDiagnostics.materialName(player, finding.actual()),
                    "x", finding.x(), "y", finding.y(), "z", finding.z());
        }
    }

    private void onLootClick(InventoryClickEvent event, TowerLootHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.hasPermission("blockintegrations.towers") || !validTower(holder.key)
                || !mayAccess(player, towers.get(holder.key), TowerControls.Right.LOOT, false)) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }
        tasks.now(this::queueSave);
    }

    private void onLootDrag(org.bukkit.event.inventory.InventoryDragEvent event, TowerLootHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.hasPermission("blockintegrations.towers") || !validTower(holder.key)
                || !mayAccess(player, towers.get(holder.key), TowerControls.Right.LOOT, false)) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }
        tasks.now(this::queueSave);
    }

    private void onLootClose(org.bukkit.event.inventory.InventoryCloseEvent event, TowerLootHolder holder) {
        Tower tower = towers.get(holder.key);
        if (tower != null) {
            tower.setLootContents(event.getInventory().getContents());
            queueSave();
            plugin.structuresChanged(tower.owner());
        }
    }

    private void loadSettings() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("towers.enabled", true);
        pauseWhenLootFull = config.getBoolean("towers.pause-when-loot-full", true);
        maximumUpgradeLevel = Math.max(1, Math.min(10,
                config.getInt("towers.upgrades.maximum-level", 10)));
        attackTaskPeriod = Math.max(1L, Math.min(20L,
                config.getLong("towers.attack-task-period-ticks", 2L)));
        int lootRows = Math.max(1, Math.min(6,
                config.getInt("towers.loot.inventory-rows", 6)));
        lootInventorySize = lootRows * 9;
        lootCreditDurationTicks = Math.max(1L,
                config.getLong("towers.loot.kill-credit-duration-ticks", 200L));
        captureNearbyPluginDrops = config.getBoolean(
                "towers.loot.capture-nearby-plugin-drops", true);
        nearbyPluginDropRadius = bounded(config.getDouble(
                "towers.loot.nearby-plugin-drop-radius", 2.0D), 0.5D, 5.0D);
        dropLootWhenBroken = config.getBoolean(
                "towers.loot.drop-contents-when-broken", true);
        maximumTowersPerPlayer = Math.max(0, config.getInt("towers.max-per-player", 8));
        experienceRequiresPlayer = config.getBoolean("towers.experience.require-nearby-player", true);
        experiencePlayerRadius = bounded(config.getDouble("towers.experience.player-radius", 48.0D), 8.0D, 128.0D);
        experienceFromSpawners = config.getBoolean("towers.experience.spawner-mobs", false);
        lineOfSight = config.getBoolean("towers.targeting.line-of-sight", true);
        ignoreNamedMobs = config.getBoolean("towers.targeting.ignore-named-mobs", true);
        saveDelayTicks = Math.max(1L, Math.min(1200L, config.getLong("towers.save-delay-ticks", 100L)));

        for (TowerUpgrade upgrade : TowerUpgrade.values()) {
            List<Integer> costs = new ArrayList<>(config.getIntegerList(
                    "towers.upgrades." + upgrade.configKey + "-costs"));
            if (costs.isEmpty()) {
                costs = new ArrayList<>(defaultCosts(upgrade));
            }
            costs.replaceAll(cost -> Math.max(0, cost));
            upgradeCosts.put(upgrade, List.copyOf(costs));
        }

        for (TowerType type : TowerType.values()) {
            String path = "towers.types." + type.configKey + ".";
            combatStats.put(type, new TowerCombatStats(
                    positive(config.getDouble(path + "base-damage", type.defaultDamage), 0.1D),
                    positive(config.getDouble(path + "damage-per-level", 2.0D), 0.0D),
                    positive(config.getDouble(path + "base-range", type.defaultRange), 1.0D),
                    positive(config.getDouble(path + "range-per-level", 1.5D), 0.0D),
                    Math.max(2, config.getInt(path + "base-cooldown-ticks", type.defaultCooldownTicks)),
                    bounded(config.getDouble(path + "speed-multiplier-per-level", 0.86D), 0.25D, 1.0D),
                    positive(config.getDouble(path + "area-radius", 3.0D), 0.5D),
                    Math.max(0, config.getInt(path + "fire-ticks", 60))));
        }
    }

    private void startAttackTask() {
        if (attackTask != null) {
            attackTask.cancel();
            attackTask = null;
        }
        if (enabled) {
            attackTask = tasks.timer(attackTaskPeriod, attackTaskPeriod, this::attackTick);
        }
    }

    private void attackTick() {
        towerTick += attackTaskPeriod;
        attackCycles++;
        towerDamageCredits.values().removeIf(credit -> towerTick > credit.expiresAtTick()
                || !towers.containsKey(credit.towerKey()));
        List<BlockKey> invalid = new ArrayList<>();
        for (Tower tower : List.copyOf(towers.values())) {
            Block core = loadedCore(tower.key);
            if (core == null) {
                continue;
            }
            if (TowerType.detect(core) != tower.type) {
                if (core.getType() != tower.type.core) invalid.add(tower.key);
                else if (attackCycles % 20 == 0) // Unvollständiger Turm: sichtbarer Hinweis statt stiller Pause.
                    plugin.core().particle().spawn(Particle.SMOKE, core.getLocation().add(0.5D, 1.0D, 0.5D), 4, 0.15D, 0.1D, 0.15D, 0.0D, null);
                continue;
            }
            if (!redstoneActive(tower, core)) continue;
            if (towerTick < tower.nextAttackTick) {
                continue;
            }
            if (pauseWhenLootFull && TowerLootCapacity.isFull(currentLoot(tower))) {
                notifyLootFull(tower);
                continue;
            }
            TowerCombatStats stats = combatStats.get(tower.type);
            double range = stats.range(tower.level(TowerUpgrade.RANGE));
            LivingEntity target = findTarget(tower, core, range);
            if (target == null) {
                continue;
            }
            attack(tower, core, target, stats);
            attacksFired++;
            tower.nextAttackTick = towerTick
                    + stats.cooldownTicks(tower.level(TowerUpgrade.SPEED));
        }
        for (BlockKey key : invalid) {
            removeTower(key);
        }
    }

    /** Separate input at head +3 east, outside all existing tower patterns. Never loads a chunk. */
    private boolean redstoneActive(Tower tower, Block core) {
        if (tower.redstoneMode == TowerControls.RedstoneMode.ALWAYS) return true;
        if (tower.redstoneMode == TowerControls.RedstoneMode.DISABLED) return false;
        int x = core.getX() + 3;
        if (!core.getWorld().isChunkLoaded(x >> 4, core.getZ() >> 4)) return false;
        Block input = core.getWorld().getBlockAt(x, core.getY(), core.getZ());
        return tower.redstoneMode.active(input.isBlockPowered() || input.isBlockIndirectlyPowered() || input.getBlockPower() > 0);
    }

    private boolean mayManageControls(Player player, Tower tower) {
        return player.isOnline() && player.hasPermission("blockintegrations.towers") && towers.get(tower.key) == tower
                && validTower(tower.key) && near(player, tower.key) && !store.readOnly()
                && (player.hasPermission("blockintegrations.admin") || player.getUniqueId().equals(tower.owner()));
    }

    private void openSharing(Player player, Tower tower) {
        if (!mayManageControls(player, tower)) return;
        Lang lang = plugin.lang();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(lang.component(player, "tower-controls.share-help")));
        for (var grant : tower.controls.snapshot().entrySet()) {
            UUID id = UUID.fromString(grant.getKey());
            List<String> rights = new ArrayList<>();
            for (var right : TowerControls.Right.values()) if (tower.controls.allows(id, right))
                rights.add(lang.plain(player, "tower-controls.right." + right.name().toLowerCase(Locale.ROOT)));
            body.add(DialogBody.plainMessage(Component.text(ownerName(player, id) + " (" + id + "): " + String.join(", ", rights))));
        }
        List<Vystorm.vystorm_core.compat.dialog.DialogInput> inputs = List.of(Vystorm.vystorm_core.compat.dialog.DialogInput.text("member",
                lang.component(player, "tower-controls.member")).maxLength(36).build());
        List<DialogButton> buttons = new ArrayList<>();
        for (var right : TowerControls.Right.values()) buttons.add(new DialogButton(lang.component(player,
                "tower-controls.toggle-right", "right", lang.plain(player, "tower-controls.right." + right.name().toLowerCase(Locale.ROOT))),
                (p, response) -> {
                    if (!mayManageControls(p, tower)) return;
                    UUID member = sharingMember(response.getText("member"));
                    if (member == null || !tower.controls.toggle(member, right)) { lang.send(p, "tower-controls.invalid-member"); return; }
                    queueSave(); openSharing(p, tower);
                }));
        buttons.add(new DialogButton(lang.component(player, "tower-controls.revoke"), (p, response) -> {
            if (!mayManageControls(p, tower)) return;
            UUID member = sharingMember(response.getText("member"));
            if (member == null) { lang.send(p, "tower-controls.invalid-member"); return; }
            tower.controls.remove(member); queueSave(); openSharing(p, tower);
        }));
        buttons.add(PortableMenuButtons.back(plugin.core(), p -> openMenu(p, tower)));
        plugin.core().dialogs().open(plugin, player, new DialogRequest(lang.component(player, "tower-controls.share"), body,
                inputs, buttons, p -> mayManageControls(p, tower), () -> {}, BlockIntegrations.CLOSE_LABEL, 2));
    }

    private static UUID sharingMember(String raw) {
        if (raw == null) return null;
        Player online = Bukkit.getPlayerExact(raw.trim());
        if (online != null) return online.getUniqueId();
        try { return UUID.fromString(raw.trim()); } catch (IllegalArgumentException invalid) { return null; }
    }

    private LivingEntity findTarget(Tower tower, Block core, double range) {
        Location source = core.getLocation().add(0.5D, 0.55D, 0.5D);
        double rangeSquared = range * range;
        java.util.Optional<String> towerCity = cityAt(core.getLocation());
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : core.getWorld().getNearbyEntities(source, range, range, range)) {
            if (!(entity instanceof Enemy enemy) || enemy.isDead() || !enemy.isValid()) {
                continue;
            }
            double distance = enemy.getLocation().distanceSquared(source);
            if (distance > rangeSquared || distance >= closestDistance || !tower.targetProfile.includes(enemy.getType()) || !targetable(enemy, towerCity)) continue;
            if (lineOfSight && !visible(source, enemy)) continue;
            closest = enemy;
            closestDistance = distance;
        }
        return closest;
    }

    /**
     * Keine benannten Mobs (Haustiere, Boss-/Eventmobs), keine neutralen Mobs, solange sie nicht angreifen, und nichts
     * in Arena-, Spawn- oder Schutzzonen oder in einer fremden Stadt.
     */
    /** SpawnDirector-Mobs tragen einen Namen ("[Lv. 5] Zombie", "[Elite] …"), sind aber normale Gegner – trotzdem angreifen. */
    private static final org.bukkit.NamespacedKey DIRECTOR_MOB = org.bukkit.NamespacedKey.fromString("spawndirector:director_mob");
    private static final org.bukkit.NamespacedKey DIRECTOR_SLOT = org.bukkit.NamespacedKey.fromString("spawndirector:structure_slot");

    static boolean directorMob(org.bukkit.entity.Entity entity) {
        var data = entity.getPersistentDataContainer();
        return data.getKeys().contains(DIRECTOR_MOB) || data.getKeys().contains(DIRECTOR_SLOT);
    }

    private boolean targetable(LivingEntity enemy, java.util.Optional<String> towerCity) {
        if (enemy instanceof org.bukkit.entity.Tameable pet && pet.isTamed()) return false;
        if (ignoreNamedMobs && plugin.core().compat().platform().customName(enemy) != null && !directorMob(enemy)) return false;
        if (enemy instanceof org.bukkit.entity.Enderman enderman && !BukkitFeatures.screaming(enderman) && enderman.getTarget() == null) return false;
        if (enemy instanceof org.bukkit.entity.PigZombie pig && !pig.isAngry()) return false;
        if (enemy instanceof org.bukkit.entity.Piglin piglin && piglin.getTarget() == null) return false;
        for (var region : plugin.core().area().regions().at(enemy.getLocation())) {
            switch (region.kind()) {
                case "arena", "arena-zone", "spawn", "safe", "market" -> { return false; }
                case "city" -> { if (!towerCity.equals(java.util.Optional.of(region.id()))) return false; }
                default -> { }
            }
        }
        return true;
    }

    /** Freie Sichtlinie vom Turmkopf zum Ziel (keine Schüsse durch Wände). */
    private static boolean visible(Location source, LivingEntity target) {
        Location eye = target.getEyeLocation();
        Vector direction = eye.toVector().subtract(source.toVector());
        double distance = direction.length();
        if (distance < 0.5D) return true;
        var hit = source.getWorld().rayTraceBlocks(source, direction.normalize(), distance,
                org.bukkit.FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }

    private void attack(Tower tower, Block core, LivingEntity target, TowerCombatStats stats) {
        double damage = stats.damage(tower.level(TowerUpgrade.DAMAGE));
        Location source = core.getLocation().add(0.5D, 0.65D, 0.5D);
        Location targetLocation = target.getEyeLocation();
        World world = core.getWorld();
        effects.shot(tower.type, source, targetLocation);
        switch (tower.type.attackStyle) {
            case PRECISION -> {
                damageFromTower(tower, target, damage);
                world.playSound(source, Sound.ENTITY_ARROW_SHOOT, 0.55F, 1.5F);
            }
            case AREA -> {
                world.playSound(targetLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.65F, 1.25F);
                damageFromTower(tower, target, damage);
                double radius = stats.areaRadius();
                double radiusSquared = radius * radius;
                for (Entity nearby : world.getNearbyEntities(targetLocation, radius, radius, radius)) {
                    if (nearby instanceof Enemy enemy && enemy != target
                            && enemy.isValid() && !enemy.isDead()
                            && tower.targetProfile.includes(enemy.getType())
                            && targetable(enemy, cityAt(core.getLocation()))
                            && enemy.getLocation().distanceSquared(targetLocation) <= radiusSquared) {
                        damageFromTower(tower, enemy, damage * 0.65D);
                    }
                }
            }
            case FIRE -> {
                damageFromTower(tower, target, damage);
                target.setFireTicks(Math.max(target.getFireTicks(), stats.fireTicks()));
                world.playSound(source, Sound.ENTITY_BLAZE_SHOOT, 0.6F, 1.15F);
            }
        }
    }

    private void damageFromTower(Tower tower, LivingEntity target, double damage) {
        double before = target.getHealth() + target.getAbsorptionAmount();
        Location impact = target.getEyeLocation().clone();
        UUID targetId = target.getUniqueId();
        towerDamageCredits.put(targetId, new TowerDamageCredit(
                tower.key, towerTick + lootCreditDurationTicks));
        towerDamageInProgress.add(targetId);
        try {
            // Mehrere Türme und Flächenschaden sollen treffen, statt an den Unverwundbarkeits-Ticks zu verpuffen.
            target.setNoDamageTicks(0);
            target.damage(damage);
        } finally {
            towerDamageInProgress.remove(targetId);
        }
        if (target.getHealth() + target.getAbsorptionAmount() < before) effects.impact(tower.type, impact);
    }

    /* Stage OBSERVE, registered in handleSharedEvents */
    public void onEntityDamaged(EntityDamageEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        TowerDamageCredit credit = towerDamageCredits.get(entityId);
        if (credit == null || towerDamageInProgress.contains(entityId)) {
            return;
        }
        // Brennschaden gehört weiterhin zum Flammenturm. Jeder andere spätere
        // Schaden ersetzt dagegen sauber die bisherige Turm-Killzuordnung.
        Tower tower = towers.get(credit.towerKey());
        boolean flameTowerDamage = tower != null && tower.type == TowerType.FLAME
                && (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK);
        if (!flameTowerDamage) {
            towerDamageCredits.remove(entityId);
        }
    }

    /**
     * Takes the drops and the experience of a tower kill into the tower. Deliberately still a Bukkit listener at
     * MONITOR and not a stage handler: it has to see what loot plugins add up to HIGHEST, and an OBSERVE handler must
     * not change the event.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!enabled) {
            return;
        }
        TowerDamageCredit credit = towerDamageCredits.remove(event.getEntity().getUniqueId());
        if (credit == null || towerTick > credit.expiresAtTick()) {
            return;
        }
        Tower tower = towers.get(credit.towerKey());
        if (tower == null) {
            return;
        }

        storeLoot(tower, event.getDrops(), true);
        event.getDrops().clear();
        int experience = Math.max(0, event.getDroppedExp());
        if (experience == 0 && event.getEntity() instanceof Mob mob) {
            // Vanilla vergibt Mob-XP normalerweise nur bei Spielerkills. Türme
            // erhalten stattdessen die vom Server berechnete mögliche Belohnung.
            experience = Math.max(0, plugin.core().compat().gameplay().getPossibleExperienceReward(mob));
        }
        Block core = loadedCore(tower.key);
        boolean spawner = plugin.core().compat().gameplay().fromMobSpawner(event.getEntity()) && !experienceFromSpawners;
        if (spawner || core == null || !eligiblePlayerNearby(tower, core)) experience = 0;
        tower.addStoredExperience(experience);
        event.setDroppedExp(0);
        queueSave();
        plugin.structuresChanged(tower.owner());

        if (captureNearbyPluginDrops) {
            Location deathLocation = event.getEntity().getLocation().clone();
            tasks.now(() -> collectNearbyPluginDrops(credit.towerKey(), deathLocation));
        }
    }

    private void collectNearbyPluginDrops(BlockKey key, Location deathLocation) {
        Tower tower = towers.get(key);
        if (tower == null || !validTower(key) || deathLocation.getWorld() == null) {
            return;
        }
        List<ItemStack> itemDrops = new ArrayList<>();
        int experience = 0;
        for (Entity entity : deathLocation.getWorld().getNearbyEntities(deathLocation,
                nearbyPluginDropRadius, nearbyPluginDropRadius, nearbyPluginDropRadius)) {
            if (entity.getTicksLived() > 5) {
                continue;
            }
            // Von Spielern geworfene oder einem Spieler gehörende Items (z. B. Todesbeute) nie einsammeln.
            if (entity instanceof Item thrown && (thrown.getThrower() != null || thrown.getOwner() != null)) {
                continue;
            }
            if (entity instanceof Item item) {
                itemDrops.add(item.getItemStack().clone());
                item.remove();
            } else if (entity instanceof ExperienceOrb orb) {
                if (BukkitFeatures.deathOrb(orb)) continue;
                experience = (int) Math.min(Integer.MAX_VALUE,
                        (long) experience + (long)Math.max(0, orb.getExperience()) * Math.max(1, BukkitFeatures.orbCount(orb)));
                orb.remove();
            }
        }
        if (!itemDrops.isEmpty()) {
            storeLoot(tower, itemDrops, false);
        }
        Block towerCore = loadedCore(key);
        if (towerCore == null || !eligiblePlayerNearby(tower, towerCore)) experience = 0;
        if (experience > 0) {
            tower.addStoredExperience(experience);
        }
        if (!itemDrops.isEmpty() || experience > 0) {
            queueSave();
        }
    }

    /** {@code looting}: Bonus nur auf echte Mob-Drops, nie auf eingesammelte Plugin-Drops. */
    private void storeLoot(Tower tower, List<ItemStack> drops, boolean looting) {
        if (drops.isEmpty()) {
            return;
        }
        List<ItemStack> copies = new ArrayList<>();
        int lootingLevel = looting ? tower.level(TowerUpgrade.LOOTING) : 0;
        for (ItemStack drop : drops) {
            if (drop != null && !drop.getType().isAir() && drop.getAmount() > 0) {
                copies.add(drop.clone());
                if (lootingLevel > 0 && drop.getMaxStackSize() > 1) {
                    int bonus = TowerLooting.bonusAmount(lootingLevel,
                            ThreadLocalRandom.current().nextDouble());
                    if (bonus > 0) {
                        ItemStack bonusDrop = drop.clone();
                        bonusDrop.setAmount(bonus);
                        copies.add(bonusDrop);
                    }
                }
            }
        }
        if (!copies.isEmpty()) {
            // Inventory#addItem gibt den nicht passenden Rest zurück. Dieser Rest wird
            // absichtlich nicht in die Welt gesetzt und ist damit der zerstörte Überschuss.
            Map<Integer, ItemStack> overflow = lootInventory(tower).addItem(copies.toArray(ItemStack[]::new));
            if (!overflow.isEmpty()) notifyLootFull(tower);
        }
    }

    /** Hinweis an den Besitzer, höchstens alle {@link #LOOT_FULL_NOTICE_MILLIS} je Turm. */
    private void notifyLootFull(Tower tower) {
        Player owner = tower.owner() == null ? null : Bukkit.getPlayer(tower.owner());
        if (owner == null) return;
        long now = System.currentTimeMillis();
        Long last = lootFullNotices.get(tower.key);
        if (last != null && now - last < LOOT_FULL_NOTICE_MILLIS) return;
        lootFullNotices.put(tower.key, now);
        BlockKey key = tower.key;
        Lang lang = plugin.lang();
        // Chat button and toast action (key Y) run the same checks at click time: tower complete, owner/admin, distance.
        java.util.function.Consumer<Player> openLoot = p -> {
            Tower current = towers.get(key);
            if (current == null || !validTower(key)) lang.send(p, "tower.not-complete");
            else if (ownsOrAdmin(p, current) && ChatLinks.near(plugin, p, key, "chat.too-far.tower")) openTowerLootFromDialog(p, key);
        };
        if (plugin.client() != null) plugin.client().lootFull(owner, tower.type, openLoot);
        Component message = lang.component(owner, "tower.loot-full", "name", plainName(owner, tower.type));
        Location location = ChatLinks.location(key);
        if (location != null) message = message.append(plugin.core().message().chat().location(location)).append(Component.space());
        plugin.core().compat().platform().sendMessage(owner, message.append(plugin.core().message().chat().button(lang.plain(owner, "tower.loot-button"),
                lang.plain(owner, "tower.loot-hover"), openLoot::accept)));
    }

    /** Opens the tower dialog from a chat button; state, permissions and distance are checked on click. */
    private void openTowerFromChat(Player player, BlockKey key) {
        Tower tower = towers.get(key);
        if (!player.hasPermission("blockintegrations.towers")) {
            plugin.lang().send(player, "tower.no-use");
            return;
        }
        if (tower == null || !validTower(key)) {
            plugin.lang().send(player, "tower.not-complete");
            return;
        }
        if (!ownsOrAdmin(player, tower) || !ChatLinks.near(plugin, player, key, "chat.too-far.tower")) return;
        openMenu(player, tower);
    }

    /** Chat buttons replace the physical interaction only for owners and admins (no remote city access). */
    private boolean ownsOrAdmin(Player player, Tower tower) {
        if (player.hasPermission("blockintegrations.admin") || player.getUniqueId().equals(tower.owner())) return true;
        plugin.lang().send(player, "tower.owned-by", "owner", ownerName(player, tower.owner()));
        return false;
    }



    /**
     * {@code /bi towers list [player]} – towers of a player (or all); {@code near} – towers within 48 blocks with owner,
     * levels and state; {@code remove} – remove the tower you are looking at (admin only).
     */
    boolean towerCommand(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                if (!sender.hasPermission("blockintegrations.admin") && !(sender instanceof Player)) {
                    plugin.lang().send(sender, "general.no-permission");
                    return true;
                }
                UUID filter = null;
                if (args.length >= 3 && sender.hasPermission("blockintegrations.admin")) filter = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
                else if (!sender.hasPermission("blockintegrations.admin") && sender instanceof Player p) filter = p.getUniqueId();
                UUID only = filter;
                var list = towers.values().stream().filter(t -> only == null || only.equals(t.owner())).toList();
                plugin.lang().send(sender, "tower.list.header", "count", list.size());
                list.stream().limit(30).forEach(t -> sendDescription(sender, t));
                return true;
            }
            case "near" -> {
                if (!(sender instanceof Player player)) {
                    plugin.lang().send(sender, "general.players-only");
                    return true;
                }
                var near = nearbyTowers(towers.values(), player.getLocation());
                plugin.lang().send(sender, "tower.list.near-header", "count", near.size(), "shown", Math.min(30, near.size()));
                near.stream().limit(30).forEach(t -> sendDescription(sender, t));
                return true;
            }
            case "remove" -> {
                if (!sender.hasPermission("blockintegrations.admin") || !(sender instanceof Player player)) {
                    plugin.lang().send(sender, "general.no-permission");
                    return true;
                }
                Block target = player.getTargetBlockExact(6);
                Tower tower = target == null ? null : towers.get(BlockKey.of(target));
                if (tower == null) { plugin.lang().send(sender, "tower.remove.look"); return true; }
                removeTower(tower.key);
                plugin.lang().send(sender, "tower.remove.done");
                return true;
            }
            default -> { return false; }
        }
    }

    /** Horizontal range matches the command's existing 48-block search; nearest entries are shown first. */
    static List<Tower> nearbyTowers(java.util.Collection<Tower> candidates, Location origin) {
        UUID world = origin.getWorld().getUID();
        java.util.function.ToDoubleFunction<Tower> distance = tower ->
                Math.hypot(tower.key.x() - origin.getX(), tower.key.z() - origin.getZ());
        return candidates.stream().filter(tower -> tower.key.worldId().equals(world) && distance.applyAsDouble(tower) <= 48)
                .sorted(java.util.Comparator.comparingDouble(distance).thenComparing(TOWER_ORDER)).toList();
    }

    /** Players get the location as clickable coordinates (teleport with the Core permission), the console plain text. */
    private void sendDescription(CommandSender sender, Tower t) {
        Lang lang = plugin.lang();
        Location location = sender instanceof Player ? ChatLinks.location(t.key) : null;
        Component where = location == null
                ? Component.text(t.key.x() + " " + t.key.y() + " " + t.key.z(), net.kyori.adventure.text.format.NamedTextColor.WHITE)
                : plugin.core().message().chat().location(location);
        plugin.core().compat().platform().sendMessage(sender, lang.component(sender, "tower.list.entry", "name", plainName(sender, t.type),
                "location", where, "details", describeDetails(sender, t)));
    }

    private Component describeDetails(CommandSender viewer, Tower t) {
        Lang lang = plugin.lang();
        Block core = loadedCore(t.key);
        String state = core == null ? "tower.state.unloaded" : TowerType.detect(core) == t.type ? "tower.state.active" : "tower.state.incomplete";
        return lang.component(viewer, "tower.list.details", "owner", ownerName(viewer, t.owner()),
                "damage", t.level(TowerUpgrade.DAMAGE), "range", t.level(TowerUpgrade.RANGE),
                "speed", t.level(TowerUpgrade.SPEED), "looting", t.level(TowerUpgrade.LOOTING),
                "state", lang.component(viewer, state), "xp", t.storedExperience());
    }

    // ---------------------------------------------------------------------------------------------
    // Web page (called by BlocksWeb on the server thread)

    /** Towers of one owner, sorted by world and position. */
    List<Tower> towersOwnedBy(UUID owner) {
        return towers.values().stream().filter(t -> owner.equals(t.owner())).sorted(TOWER_ORDER).toList();
    }

    List<Tower> allTowers() {
        return towers.values().stream().sorted(TOWER_ORDER).toList();
    }

    Tower tower(BlockKey key) {
        return towers.get(key);
    }

    int maximumTowersPerPlayer() {
        return maximumTowersPerPlayer;
    }

    private static final java.util.Comparator<Tower> TOWER_ORDER = java.util.Comparator
            .<Tower, String>comparing(t -> t.key.worldId().toString())
            .thenComparingInt(t -> t.key.x()).thenComparingInt(t -> t.key.z()).thenComparingInt(t -> t.key.y());

    /**
     * Remote access from the web page: the owner or an admin, like the chat buttons (city members only get access
     * standing at the tower in game).
     */
    boolean webAccess(Player player, Tower tower) {
        return player.hasPermission(BlockIntegrations.ADMIN_PERMISSION) || player.getUniqueId().equals(tower.owner());
    }

    /** {@code active}, {@code incomplete} or {@code unloaded} (the chunk is never loaded for this). */
    String state(Tower tower) {
        Block core = loadedCore(tower.key);
        if (core == null) return "unloaded";
        return TowerType.detect(core) == tower.type ? "active" : "incomplete";
    }

    /** The tower as the dialog shows it (values in {@code language}). */
    com.google.gson.JsonObject webView(Tower tower, String language) {
        Lang lang = plugin.lang();
        TowerCombatStats stats = combatStats.get(tower.type);
        com.google.gson.JsonObject json = WebJson.structure("tower", tower.key, tower.type.configKey,
                lang.plain(language, tower.type.nameKey()), WebJson.hex(tower.type.color));
        json.addProperty("state", state(tower));
        json.addProperty("owner", ownerName(language, tower.owner()));
        if (tower.owner() != null) json.addProperty("ownerId", tower.owner().toString());
        json.addProperty("xp", tower.storedExperience());
        json.addProperty("upgradeable", tower.type.upgradeable);
        json.addProperty("damage", oneDecimal(stats.damage(tower.level(TowerUpgrade.DAMAGE))));
        json.addProperty("range", oneDecimal(stats.range(tower.level(TowerUpgrade.RANGE))));
        json.addProperty("ticks", stats.cooldownTicks(tower.level(TowerUpgrade.SPEED)));
        ItemStack[] loot = currentLoot(tower);
        int stacks = 0;
        for (ItemStack item : loot) if (item != null && !item.getType().isAir() && item.getAmount() > 0) stacks++;
        json.addProperty("lootStacks", stacks);
        json.addProperty("lootSlots", loot.length);
        com.google.gson.JsonArray upgrades = new com.google.gson.JsonArray();
        if (tower.type.upgradeable) {
            for (TowerUpgrade upgrade : TowerUpgrade.values()) {
                int level = tower.level(upgrade);
                int maximum = maximumLevel(upgrade);
                boolean maxed = level >= maximum;
                upgrades.add(WebJson.upgrade(upgrade.configKey, lang.plain(language, upgrade.nameKey()), level, maximum,
                        upgradeValue(language, upgrade, stats, level),
                        maxed ? null : upgradeValue(language, upgrade, stats, level + 1),
                        maxed ? null : upgradeCost(upgrade, level)));
            }
        }
        json.add("upgrades", upgrades);
        return json;
    }

    /** Loot chest contents: the open chest if a player has it open, else the stored contents. */
    private ItemStack[] currentLoot(Tower tower) {
        Inventory open = lootInventories.get(tower.key);
        return open != null ? open.getContents() : tower.lootContents(lootSize(tower));
    }

    /** Loot chest items for the web page. */
    com.google.gson.JsonArray lootItems(Tower tower) {
        com.google.gson.JsonArray items = new com.google.gson.JsonArray();
        ItemStack[] loot = currentLoot(tower);
        for (int slot = 0; slot < loot.length; slot++) {
            ItemStack item = loot[slot];
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) items.add(WebJson.item(plugin.core().compat().platform(), slot, item));
        }
        return items;
    }

    /**
     * Why the player cannot take loot from the web page right now (a language key), or {@code null} if he can. Same
     * rules as the loot chat button: towers permission, owner or admin, complete tower and within
     * {@link ChatLinks#REMOTE_RANGE} blocks of it.
     */
    String lootBlocker(Player player, Tower tower) {
        if (player == null) return "web.loot.offline";
        if (!player.hasPermission("blockintegrations.towers") || !webAccess(player, tower)) return "web.error.not-yours";
        if (!validTower(tower.key)) return "web.loot.not-loaded";
        if (!near(player, tower.key)) return "web.loot.too-far";
        return null;
    }

    private static boolean near(Player player, BlockKey key) {
        Location target = ChatLinks.location(key);
        return target != null && player.getWorld().equals(target.getWorld())
                && player.getLocation().distanceSquared(target.clone().add(0.5D, 0.5D, 0.5D))
                <= ChatLinks.REMOTE_RANGE * ChatLinks.REMOTE_RANGE;
    }

    /** Result of taking loot from the web page. */
    record LootTake(String blocker, boolean changed, int moved, boolean inventoryFull) {}

    /**
     * Moves loot into the player's inventory on the server thread, through the same shared chest inventory the in-game
     * viewers use (their view updates at once). {@code slot < 0} takes everything that fits. For one slot the item must
     * still be what the page showed ({@code material}/{@code amount}), otherwise nothing moves. Items that do not fit
     * stay in the chest.
     */
    LootTake takeLoot(Player player, BlockKey key, int slot, String material, int amount) {
        Tower tower = towers.get(key);
        if (tower == null) return new LootTake("web.error.unknown", false, 0, false);
        String blocker = lootBlocker(player, tower);
        if (blocker != null) return new LootTake(blocker, false, 0, false);
        Inventory chest = lootInventory(tower);
        List<Integer> slots = new ArrayList<>();
        if (slot < 0) {
            for (int i = 0; i < chest.getSize(); i++) slots.add(i);
        } else {
            ItemStack shown = slot < chest.getSize() ? chest.getItem(slot) : null;
            if (shown == null || plugin.core().compat().platform().isEmpty(shown) || !shown.getType().name().equals(material) || shown.getAmount() != amount)
                return new LootTake(null, true, 0, false);
            slots.add(slot);
        }
        int moved = 0;
        boolean full = false;
        for (int index : slots) {
            ItemStack item = chest.getItem(index);
            if (item == null || plugin.core().compat().platform().isEmpty(item)) continue;
            int left = 0;
            for (ItemStack rest : player.getInventory().addItem(item.clone()).values()) left += rest == null ? 0 : rest.getAmount();
            moved += item.getAmount() - left;
            if (left <= 0) {
                chest.setItem(index, null);
            } else {
                ItemStack rest = item.clone();
                rest.setAmount(left);
                chest.setItem(index, rest);
                full = true;
                break;
            }
        }
        if (moved > 0) {
            tower.setLootContents(chest.getContents());
            queueSave();
            plugin.structuresChanged(tower.owner());
            lootSound(player);
        }
        return new LootTake(null, false, moved, full);
    }

    /** Building guide of all tower types: layout, what they do, base values and upgrade tables. */
    com.google.gson.JsonObject webGuide(String language) {
        Lang lang = plugin.lang();
        com.google.gson.JsonObject guide = new com.google.gson.JsonObject();
        com.google.gson.JsonArray types = new com.google.gson.JsonArray();
        for (TowerType type : TowerType.values()) {
            TowerCombatStats stats = combatStats.get(type);
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("type", type.configKey);
            json.addProperty("name", lang.plain(language, type.nameKey()));
            json.addProperty("color", WebJson.hex(type.color));
            json.addProperty("upgradeable", type.upgradeable);
            json.addProperty("style", lang.plain(language, "web.guide.tower.style." + type.attackStyle.name().toLowerCase(Locale.ROOT)));
            json.addProperty("damage", oneDecimal(stats.damage(0)));
            json.addProperty("range", oneDecimal(stats.range(0)));
            json.addProperty("ticks", stats.cooldownTicks(0));
            com.google.gson.JsonArray blocks = new com.google.gson.JsonArray();
            blocks.add(WebJson.block("core", type.core, TowerType.friendly(type.core)));
            blocks.add(WebJson.block("pedestal", type.pedestal, TowerType.friendly(type.pedestal)));
            blocks.add(WebJson.block("foundation", type.foundation, type == TowerType.ARCHER
                    ? lang.plain(language, "tower.any-copper") : TowerType.friendly(type.foundation)));
            blocks.add(WebJson.block("ring", type.ring, TowerType.friendly(type.ring)));
            json.add("blocks", blocks);
            com.google.gson.JsonArray views = new com.google.gson.JsonArray();
            views.add(WebJson.view(lang.plain(language, "web.guide.view.side"), towerSideView()));
            views.add(WebJson.view(lang.plain(language, "web.guide.view.foundation"), towerFoundationView()));
            json.add("views", views);
            com.google.gson.JsonArray upgrades = new com.google.gson.JsonArray();
            if (type.upgradeable) {
                for (TowerUpgrade upgrade : TowerUpgrade.values()) {
                    int maximum = maximumLevel(upgrade);
                    com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
                    for (int level = 0; level <= maximum; level++)
                        rows.add(WebJson.levelRow(level, upgradeValue(language, upgrade, stats, level),
                                level >= maximum ? null : upgradeCost(upgrade, level)));
                    upgrades.add(WebJson.upgradeTable(upgrade.configKey, lang.plain(language, upgrade.nameKey()), maximum, rows));
                }
            }
            json.add("upgrades", upgrades);
            types.add(json);
        }
        guide.add("types", types);
        List<String> notes = new ArrayList<>();
        notes.add(lang.plain(language, "tower.guide.intro"));
        notes.add(lang.plain(language, "tower.guide.ring"));
        notes.add(lang.plain(language, "tower.guide.levels", "max", maximumUpgradeLevel, "looting", maximumLevel(TowerUpgrade.LOOTING)));
        notes.add(lang.plain(language, "tower.guide.loot"));
        if (experienceRequiresPlayer)
            notes.add(lang.plain(language, "web.guide.tower.xp-rule", "radius", (int) experiencePlayerRadius));
        if (maximumTowersPerPlayer > 0)
            notes.add(lang.plain(language, "web.guide.tower.limit", "max", maximumTowersPerPlayer));
        guide.add("notes", WebJson.strings(notes));
        return guide;
    }

    /** Side view, top to bottom: head on pedestal on foundation, ring blocks left and right of the foundation. */
    static String[][] towerSideView() {
        return new String[][] {
                {null, "core", null},
                {null, "pedestal", null},
                {"ring", "foundation", "ring"}};
    }

    /** Foundation layer from above: the ring blocks north, south, east and west, corners free. */
    static String[][] towerFoundationView() {
        return new String[][] {
                {null, "ring", null},
                {"ring", "foundation", "ring"},
                {null, "ring", null}};
    }

    private String ownerName(String language, UUID owner) {
        if (owner == null) return plugin.lang().plain(language, "tower.nobody");
        String name = Bukkit.getOfflinePlayer(owner).getName();
        return name == null ? owner.toString().substring(0, 8) : name;
    }

    // ---------------------------------------------------------------------------------------------
    // Besitz und Zugriff

    /** Stadt (Core-Regionsregister), in der ein Block steht; leer in der Wildnis. */
    private java.util.Optional<String> cityAt(Location location) {
        return plugin.core().area().regions().top(location, "city").map(r -> r.id());
    }

    /**
     * Wer einen Turm nutzen darf: Admins, der Erbauer, und in einer Stadt die Mitglieder mit Baurecht (die
     * CityClaim-Prüfung hat die Interaktion dann bereits zugelassen). Ein Turm ohne Besitzer (ältere Daten) geht an
     * den ersten berechtigten Nutzer über.
     */
    private boolean mayUse(Player player, Tower tower, boolean message) {
        return mayAccess(player, tower, TowerControls.Right.USE, message);
    }

    private boolean mayAccess(Player player, Tower tower, TowerControls.Right right, boolean message) {
        if (!player.hasPermission("blockintegrations.towers")) return false;
        if (tower == null) return false;
        if (player.hasPermission("blockintegrations.admin")) return true;
        UUID owner = tower.owner();
        if (owner == null) {
            tower.owner(player.getUniqueId());
            queueSave();
            plugin.structuresChanged(player.getUniqueId());
            plugin.lang().send(player, "tower.claimed");
            return true;
        }
        if (owner.equals(player.getUniqueId()) || tower.controls.allows(player.getUniqueId(), right)) return true;
        Block core = loadedCore(tower.key);
        if (core != null && cityAt(core.getLocation()).isPresent()
                && cityAt(core.getLocation()).equals(cityAt(player.getLocation()))) return true;
        if (message) plugin.lang().send(player, "tower.owned-by", "owner", ownerName(player, owner));
        return false;
    }

    private String ownerName(CommandSender viewer, UUID owner) {
        if (owner == null) return plugin.lang().plain(viewer, "tower.nobody");
        String name = Bukkit.getOfflinePlayer(owner).getName();
        return name == null ? owner.toString().substring(0, 8) : name;
    }

    /** XP gibt es nur, wenn ein berechtigter Spieler in der Nähe ist (keine AFK-Farmen). */
    private boolean eligiblePlayerNearby(Tower tower, Block core) {
        if (!experienceRequiresPlayer) return true;
        double radiusSquared = experiencePlayerRadius * experiencePlayerRadius;
        java.util.Optional<String> city = cityAt(core.getLocation());
        for (Player player : core.getWorld().getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (player.getLocation().distanceSquared(core.getLocation()) > radiusSquared) continue;
            if (player.getUniqueId().equals(tower.owner()) || tower.owner() == null) return true;
            if (city.isPresent() && city.equals(cityAt(player.getLocation()))) return true;
        }
        return false;
    }

    /** Reichweite kurz nur dem berechtigten Betrachter als Partikelring anzeigen. */
    private void showRange(Player player, Tower tower) {
        Block core = loadedCore(tower.key);
        if (core == null) return;
        double range = combatStats.get(tower.type).range(tower.level(TowerUpgrade.RANGE));
        Location center = core.getLocation().add(0.5D, 0.2D, 0.5D);
        BlockKey key = tower.key;
        RadiusPreviewManager previews = plugin.radiusPreviews();
        if (previews == null) return;
        previews.show(player, center, range,
                viewer -> viewer.hasPermission("blockintegrations.towers") && validTower(key)
                        && towers.get(key) == tower && mayUse(viewer, tower, false));
    }

    // ---------------------------------------------------------------------------------------------
    // Schutz der Turmblöcke

    /** Kopf, Sockel, Fundament und Ring eines erkannten Turms. */
    private boolean towerBlock(Block block) {
        UUID world = block.getWorld().getUID();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 2; dy++) {
            if (Math.abs(dx) + Math.abs(dz) > 1 && dy != 2) continue;
            Tower tower = towers.get(new BlockKey(world, block.getX() + dx, block.getY() + dy, block.getZ() + dz));
            if (tower == null) continue;
            int rx = -dx, ry = -dy, rz = -dz; // Lage des Blocks relativ zum Kopf
            if (rx == 0 && rz == 0 && ry >= -2 && ry <= 0) return true;
            if (ry == -2 && Math.abs(rx) + Math.abs(rz) == 1) return true;
        }
        return false;
    }

    /* Stage PROTECT, registered in handleSharedEvents */
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (enabled) event.blockList().removeIf(this::towerBlock);
    }

    /* Stage PROTECT, registered in handleSharedEvents */
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        if (enabled) event.blockList().removeIf(this::towerBlock);
    }

    /* Stage PROTECT, registered in handleSharedEvents */
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        if (enabled && event.getBlocks().stream().anyMatch(this::towerBlock)) event.setCancelled(true);
    }

    /* Stage PROTECT, registered in handleSharedEvents */
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        if (enabled && event.getBlocks().stream().anyMatch(this::towerBlock)) event.setCancelled(true);
    }

    /* Stage PROTECT, registered in handleSharedEvents */
    public void onFluid(org.bukkit.event.block.BlockFromToEvent event) {
        if (enabled && towerBlock(event.getToBlock())) event.setCancelled(true);
    }

    /** Stage PROTECT, registered in {@link #handleBlockEvents}. */
    public void onBreakOthers(BlockBreakEvent event) {
        if (!enabled || !towerBlock(event.getBlock())) return;
        BlockKey key = BlockKey.of(event.getBlock());
        Tower own = towers.get(key);
        Tower tower = own;
        if (tower == null)
            for (Tower candidate : towers.values())
                if (candidate.key.worldId().equals(key.worldId()) && Math.abs(candidate.key.x() - key.x()) <= 1
                        && Math.abs(candidate.key.z() - key.z()) <= 1 && candidate.key.y() - key.y() >= 0
                        && candidate.key.y() - key.y() <= 2) { tower = candidate; break; }
        if (tower == null || tower.owner() == null) return;
        Player player = event.getPlayer();
        if (tower.owner().equals(player.getUniqueId()) || player.hasPermission("blockintegrations.admin")) return;
        // Stadtmitglieder dürfen – wie beim Nutzen – auch abbauen; CityClaim prüft ihr Baurecht selbst.
        if (cityAt(event.getBlock().getLocation()).isPresent()
                && cityAt(event.getBlock().getLocation()).equals(cityAt(player.getLocation()))) return;
        event.setCancelled(true);
        plugin.lang().send(player, "tower.owned-by", "owner", ownerName(player, tower.owner()));
    }

    private void openMenu(Player player, Tower tower) {
        Lang lang = plugin.lang();
        BlockKey key = tower.key;
        TowerCombatStats stats = combatStats.get(tower.type);
        String towerName = plainName(player, tower.type);
        List<DialogBody> body = new ArrayList<>();
        List<DialogButton> buttons = new ArrayList<>();
        body.add(DialogBody.plainMessage(lang.component(player, "tower.dialog.header", "name", towerName,
                "owner", ownerName(player, tower.owner()), "xp", tower.storedExperience())));
        body.add(DialogBody.plainMessage(lang.component(player, "tower.dialog.stats",
                "damage", oneDecimal(stats.damage(tower.level(TowerUpgrade.DAMAGE))),
                "range", oneDecimal(stats.range(tower.level(TowerUpgrade.RANGE))),
                "ticks", stats.cooldownTicks(tower.level(TowerUpgrade.SPEED)),
                "xp_rule", experienceRequiresPlayer
                        ? lang.component(player, "tower.dialog.xp-rule", "radius", (int) experiencePlayerRadius)
                        : Component.empty())));
        body.add(DialogBody.plainMessage(lang.component(player, "tower-controls.status",
                "profile", lang.plain(player, "tower-controls.profile." + tower.targetProfile.name().toLowerCase(Locale.ROOT)),
                "mode", lang.plain(player, "tower-controls.redstone." + tower.redstoneMode.name().toLowerCase(Locale.ROOT)),
                "x", key.x() + 3, "y", key.y(), "z", key.z())));
        if (mayManageControls(player, tower)) {
            buttons.add(new DialogButton(lang.component(player, "tower-controls.change-profile"), (p, response) -> {
                if (!mayManageControls(p, tower)) return;
                var values = TowerControls.TargetProfile.values();
                tower.targetProfile = values[(tower.targetProfile.ordinal() + 1) % values.length];
                queueSave(); openMenu(p, tower);
            }));
            buttons.add(new DialogButton(lang.component(player, "tower-controls.change-redstone"), (p, response) -> {
                if (!mayManageControls(p, tower)) return;
                var values = TowerControls.RedstoneMode.values();
                tower.redstoneMode = values[(tower.redstoneMode.ordinal() + 1) % values.length];
                queueSave(); openMenu(p, tower);
            }));
            buttons.add(new DialogButton(lang.component(player, "tower-controls.share"), (p, response) -> openSharing(p, tower)));
        }
        if (tower.type.upgradeable) {
            for (TowerUpgrade upgrade : TowerUpgrade.values()) {
                int shown = tower.level(upgrade);
                int maximum = maximumLevel(upgrade);
                int cost = shown >= maximum ? 0 : upgradeCost(upgrade, shown);
                String upgradeName = lang.plain(player, upgrade.nameKey());
                String effect = upgradeValue(player, upgrade, stats, shown);
                if (shown >= maximum) {
                    body.add(DialogBody.plainMessage(lang.component(player, "upgrade.line-max", "upgrade", upgradeName,
                            "level", shown, "max", maximum, "value", effect)));
                    continue;
                }
                String next = upgradeValue(player, upgrade, stats, shown + 1);
                body.add(DialogBody.plainMessage(lang.component(player, "upgrade.line", "upgrade", upgradeName,
                        "level", shown, "max", maximum, "value", effect, "next", next, "cost", cost)));
                buttons.add(new DialogButton(lang.component(player, "upgrade.button", "upgrade", upgradeName,
                        "next", next, "cost", cost),
                        (p, response) -> upgradeTower(p, key, upgrade, shown, cost)));
            }
        } else {
            body.add(DialogBody.plainMessage(lang.component(player, "tower.dialog.starter")));
        }
        buttons.add(new DialogButton(lang.component(player, "tower.dialog.loot"),
                (p, response) -> openTowerLootFromDialog(p, key)));
        buttons.add(new DialogButton(lang.component(player, "tower.dialog.withdraw"),
                (p, response) -> withdrawTowerExperience(p, key)));
        plugin.addWebButton(buttons, player, "tower/" + key.webId());
        buttons.add(PortableMenuButtons.back(plugin.core(), plugin::openBlockGuideMenu));
        buttons.add(PortableMenuButtons.home(plugin.core()));
        plugin.core().dialogs().open(plugin, player, new DialogRequest(
                lang.component(player, "tower.dialog.title", "name", towerName), body, List.of(), buttons,
                p -> p.isOnline() && p.hasPermission("blockintegrations.towers") && towers.get(key) == tower && validTower(key) && mayUse(p, tower, false),
                () -> {}, BlockIntegrations.CLOSE_LABEL, 3));
    }

    /** The loot chest is shared by all viewers, so its title is set per viewer after opening. */
    private void openLootMenu(Player player, Tower tower) {
        org.bukkit.inventory.InventoryView view = player.openInventory(lootInventory(tower));
        if (view != null) {
            try {
                PlatformAccess.title(view, plugin.lang().text(player, "tower.loot-title", "name", name(player, tower.type)));
            } catch (RuntimeException unsupported) {
                // Title stays in the server default language.
            }
        }
    }

    private Inventory lootInventory(Tower tower) {
        return lootInventories.computeIfAbsent(tower.key, ignored -> {
            TowerLootHolder holder = new TowerLootHolder(tower.key);
            Inventory inventory = plugin.core().gui().createInventory(plugin, holder, null, lootSize(tower),
                    plugin.lang() == null ? tower.type.configKey
                            : plugin.lang().text(Bukkit.getConsoleSender(), "tower.loot-title", "name",
                            plugin.lang().component(Bukkit.getConsoleSender(), tower.type.nameKey()).colorIfAbsent(tower.type.color)),
                    new GuiCallbacks() {
                        @Override public void click(InventoryClickEvent event) { onLootClick(event, holder); }
                        @Override public void drag(org.bukkit.event.inventory.InventoryDragEvent event) { onLootDrag(event, holder); }
                        @Override public void close(org.bukkit.event.inventory.InventoryCloseEvent event) { onLootClose(event, holder); }
                    });
            holder.setInventory(inventory);
            inventory.setContents(tower.lootContents(lootSize(tower)));
            return inventory;
        });
    }

    private void openTowerLootFromDialog(Player player, BlockKey key) {
        Tower tower = towers.get(key);
        if (tower != null && !mayAccess(player, tower, TowerControls.Right.LOOT, true)) return;
        if (tower == null || !validTower(key)) {
            plugin.lang().send(player, "tower.not-complete");
            return;
        }
        tasks.now(() -> {
            if (player.isOnline() && player.hasPermission("blockintegrations.towers") && towers.get(key) == tower && validTower(key)
                    && mayAccess(player, tower, TowerControls.Right.LOOT, false)) {
                openLootMenu(player, tower);
            }
        });
    }

    private void upgradeTower(Player player, BlockKey key, TowerUpgrade upgrade, int expectedLevel, int expectedCost) {
        Lang lang = plugin.lang();
        UpgradeResult result = applyUpgrade(player, key, upgrade, expectedLevel, expectedCost, true);
        Tower tower = towers.get(key);
        switch (result.outcome()) {
            case NO_PERMISSION -> lang.send(player, "tower.no-upgrade-permission");
            case GONE -> {
                player.closeInventory();
                lang.send(player, "tower.not-complete");
            }
            case NOT_UPGRADEABLE -> lang.send(player, "tower.starter-no-upgrades");
            case MAXED -> lang.send(player, "upgrade.maxed");
            case CHANGED -> {
                lang.send(player, "upgrade.offer-changed");
                if (tower != null) openMenu(player, tower);
            }
            case NEED_XP -> lang.send(player, "upgrade.need-xp", "cost", result.cost());
            case DONE -> {
                if (tower != null) openMenu(player, tower);
            }
            default -> { } // DENIED: mayUse already told the player
        }
    }

    /**
     * Checks and applies a tower upgrade: permission, access, complete structure (chunk loaded), max level, the offer
     * the player saw ({@code expectedLevel < 0} skips that check) and the XP cost. Used by the upgrade dialog and the web
     * page. On success the player gets the chat confirmation and the anvil sound.
     *
     * @param chat whether access denials are explained in chat (dialog) or only returned (web)
     */
    UpgradeResult applyUpgrade(Player player, BlockKey key, TowerUpgrade upgrade, int expectedLevel, int expectedCost,
            boolean chat) {
        Lang lang = plugin.lang();
        if (!player.hasPermission("blockintegrations.towers.upgrade")) return UpgradeResult.of(UpgradeResult.Outcome.NO_PERMISSION);
        Tower tower = towers.get(key);
        if (tower != null && !mayAccess(player, tower, TowerControls.Right.UPGRADE, chat)) return UpgradeResult.of(UpgradeResult.Outcome.DENIED);
        Block core = loadedCore(key);
        if (tower == null || core == null || TowerType.detect(core) != tower.type) {
            if (tower != null && core != null && core.getType() != tower.type.core) removeTower(key);
            return UpgradeResult.of(UpgradeResult.Outcome.GONE);
        }
        if (!tower.type.upgradeable) return UpgradeResult.of(UpgradeResult.Outcome.NOT_UPGRADEABLE);
        int current = tower.level(upgrade);
        if (current >= maximumLevel(upgrade)) return new UpgradeResult(UpgradeResult.Outcome.MAXED, current, 0);
        int cost = upgradeCost(upgrade, current);
        if (expectedLevel >= 0 && (current != expectedLevel || cost != expectedCost))
            return new UpgradeResult(UpgradeResult.Outcome.CHANGED, current, cost);
        if (!takeExperience(player, cost)) return new UpgradeResult(UpgradeResult.Outcome.NEED_XP, current, cost);
        tower.upgrade(upgrade);
        queueSave();
        plugin.structuresChanged(tower.owner());
        upgradeSound(player);
        lang.send(player, "upgrade.done", "upgrade", lang.plain(player, upgrade.nameKey()),
                "level", tower.level(upgrade), "cost", cost);
        return new UpgradeResult(UpgradeResult.Outcome.DONE, tower.level(upgrade), cost);
    }

    void upgradeSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7F, 1.35F);
    }

    void lootSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6F, 1.2F);
    }

    private void withdrawTowerExperience(Player player, BlockKey key) {
        Lang lang = plugin.lang();
        Tower tower = towers.get(key);
        if (tower != null && !mayAccess(player, tower, TowerControls.Right.LOOT, true)) return;
        if (tower == null || !validTower(key)) {
            player.closeInventory();
            lang.send(player, "tower.not-complete");
            return;
        }
        int payout = tower.withdrawStoredExperience();
        if (payout <= 0) {
            lang.send(player, "tower.no-xp");
            return;
        }
        plugin.core().player().giveExperience(player, payout);
        queueSave();
        plugin.structuresChanged(tower.owner());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                0.8F, 1.15F);
        lang.send(player, "tower.withdrawn", "xp", payout);
        openMenu(player, tower);
    }

    private String upgradeValue(CommandSender viewer, TowerUpgrade upgrade, TowerCombatStats stats, int level) {
        return upgradeValue(plugin.lang().language(viewer), upgrade, stats, level);
    }

    private String upgradeValue(String language, TowerUpgrade upgrade, TowerCombatStats stats, int level) {
        Lang lang = plugin.lang();
        return switch (upgrade) {
            case DAMAGE -> lang.plain(language, "tower.value.damage", "value", oneDecimal(stats.damage(level)));
            case RANGE -> lang.plain(language, "tower.value.range", "value", oneDecimal(stats.range(level)));
            case SPEED -> lang.plain(language, "tower.value.speed", "ticks", stats.cooldownTicks(level));
            case LOOTING -> level <= 0
                    ? lang.plain(language, "tower.value.looting-none") : lang.plain(language, "tower.value.looting", "level", level);
        };
    }

    private int maximumLevel(TowerUpgrade upgrade) {
        return Math.min(maximumUpgradeLevel, upgrade.maximumLevel);
    }

    private boolean takeExperience(Player player, int cost) {
        if (cost <= 0 || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return true;
        }
        return plugin.core().player().withdrawExperience(player, cost);
    }

    private int upgradeCost(TowerUpgrade upgrade, int currentLevel) {
        List<Integer> costs = upgradeCosts.get(upgrade);
        return costs.get(Math.min(currentLevel, costs.size() - 1));
    }

    private Tower recognize(Block core) {
        return recognize(core, recentPlacers.get(BlockKey.of(core)));
    }

    private Tower recognize(Block core, UUID placer) {
        TowerType detected = TowerType.detect(core);
        BlockKey key = BlockKey.of(core);
        Tower existing = towers.get(key);
        if (detected == null) {
            if (existing != null && core.getType() != existing.type.core) {
                removeTower(key);
            }
            return null;
        }
        if (existing != null && existing.type == detected) {
            return existing;
        }
        UUID owner = placer != null ? placer : recentPlacers.get(key);
        if (owner != null) {
            Player builder = Bukkit.getPlayer(owner);
            if (builder != null && !builder.hasPermission("blockintegrations.towers")) {
                plugin.lang().send(builder, "tower.no-build");
                return null;
            }
            long owned = towers.values().stream().filter(t -> owner.equals(t.owner())).count();
            if (maximumTowersPerPlayer > 0 && owned >= maximumTowersPerPlayer
                    && (builder == null || !builder.hasPermission("blockintegrations.admin"))) {
                if (builder != null) plugin.core().compat().platform().sendMessage(builder, plugin.lang().component(builder, "tower.limit",
                                "owned", owned, "max", maximumTowersPerPlayer)
                        .append(plugin.core().message().chat().command(plugin.lang().plain(builder, "tower.my-towers"),
                                plugin.lang().plain(builder, "tower.my-towers-hover"),
                                "/blockintegrations towers list")));
                return null;
            }
        }
        Tower tower = new Tower(key, detected, 0, 0, 0, maximumUpgradeLevel);
        tower.owner(owner);
        towers.put(key, tower);
        queueSave();
        plugin.structuresChanged(owner);
        return tower;
    }

    private void scheduleScanAround(Block changedBlock) {
        UUID worldId = changedBlock.getWorld().getUID();
        int x = changedBlock.getX();
        int y = changedBlock.getY();
        int z = changedBlock.getZ();
        tasks.now(() -> {
            World world = Bukkit.getWorld(worldId);
            if (world == null) {
                return;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        Block candidate = world.getBlockAt(x + dx, y + dy, z + dz);
                        if (TowerType.isCore(candidate.getType())) {
                            recognize(candidate);
                        }
                    }
                }
            }
            validateNearby(worldId, x, y, z);
            notifyNearby(worldId, x, y, z);
        });
    }

    private void validateNearby(UUID worldId, int x, int y, int z) {
        for (Tower tower : List.copyOf(towers.values())) {
            if (!tower.key.worldId().equals(worldId)
                    || Math.abs(tower.key.x() - x) > 1
                    || Math.abs(tower.key.y() - y) > 2
                    || Math.abs(tower.key.z() - z) > 1) {
                continue;
            }
            Block core = loadedCore(tower.key);
            if (core != null && core.getType() != tower.type.core) {
                removeTower(tower.key);
            }
        }
    }

    /** A block next to a tower changed: its owner's page may show a different state (active/incomplete). */
    private void notifyNearby(UUID worldId, int x, int y, int z) {
        for (Tower tower : towers.values()) {
            if (tower.key.worldId().equals(worldId) && Math.abs(tower.key.x() - x) <= 1
                    && Math.abs(tower.key.y() - y) <= 2 && Math.abs(tower.key.z() - z) <= 1) {
                plugin.structuresChanged(tower.owner());
            }
        }
    }

    private void scanLoadedChunks() {
        if (!enabled) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    private void validateLoadedTowers() {
        for (Tower tower : List.copyOf(towers.values())) {
            Block core = loadedCore(tower.key);
            if (core != null && core.getType() != tower.type.core) {
                removeTower(tower.key);
            }
        }
    }

    private void scanChunk(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (TowerType.isCore(state.getType())) {
                recognize(state.getBlock());
            }
        }
    }

    private Block loadedCore(BlockKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
            return null;
        }
        return world.getBlockAt(key.x(), key.y(), key.z());
    }

    private boolean validTower(BlockKey key) {
        Tower tower = towers.get(key);
        Block core = loadedCore(key);
        return tower != null && core != null && TowerType.detect(core) == tower.type;
    }

    private void removeTower(BlockKey key) {
        Tower tower = towers.remove(key);
        lootFullNotices.remove(key);
        if (tower == null) {
            return;
        }
        plugin.structuresChanged(tower.owner());
        Inventory loot = lootInventories.remove(key);
        if (loot != null) {
            tower.setLootContents(loot.getContents());
            plugin.core().gui().releaseInventory(loot);
        }
        towerDamageCredits.values().removeIf(credit -> credit.towerKey().equals(key));
        if (dropLootWhenBroken) {
            World world = Bukkit.getWorld(key.worldId());
            if (world != null && world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                Location location = new Location(world, key.x() + 0.5D,
                        key.y() + 0.5D, key.z() + 0.5D);
                for (ItemStack item : tower.lootContents(lootSize(tower))) {
                    if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                        world.dropItemNaturally(location, item);
                    }
                }
                int storedExperience = tower.withdrawStoredExperience();
                if (storedExperience > 0) {
                    world.spawn(location, ExperienceOrb.class).setExperience(storedExperience);
                }
            }
        }
        queueSave();
    }

    /** Konfigurierte Größe, aber nie kleiner als die schon belegten Plätze (kein Itemverlust beim Verkleinern). */
    private int lootSize(Tower tower) {
        ItemStack[] stored = tower.lootContents(54);
        int last = -1;
        for (int slot = 0; slot < stored.length; slot++)
            if (stored[slot] != null && !stored[slot].getType().isAir()) last = slot;
        int needed = ((last + 1 + 8) / 9) * 9;
        return Math.max(lootInventorySize, Math.min(54, needed));
    }

    private void closeAndSyncLootInventories() {
        for (Map.Entry<BlockKey, Inventory> entry : List.copyOf(lootInventories.entrySet())) {
            Tower tower = towers.get(entry.getKey());
            Inventory inventory = entry.getValue();
            if (tower != null) {
                tower.setLootContents(inventory.getContents());
            }
            plugin.core().gui().releaseInventory(inventory);
        }
        lootInventories.clear();
    }

    /** {@code towers.json}, schema 1: towers by block key; loot slots hold Core-encoded items (Paper serialization). */
    record TowersState(Map<String, TowerRecord> towers) {
        TowersState {
            towers = towers == null ? Map.of() : Map.copyOf(towers);
        }

        static TowersState empty() {
            return new TowersState(Map.of());
        }
    }

    record TowerRecord(String type, int damageLevel, int rangeLevel, int speedLevel, int lootingLevel, int storedExperience,
                       String owner, Map<Integer, String> loot, String targetProfile, String redstoneMode, Map<String, Integer> grants) {
        TowerRecord(String type, int damageLevel, int rangeLevel, int speedLevel, int lootingLevel, int storedExperience,
                String owner, Map<Integer, String> loot) {
            this(type, damageLevel, rangeLevel, speedLevel, lootingLevel, storedExperience, owner, loot, null, null, Map.of());
        }
        TowerRecord {
            grants = grants == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(grants));
            loot = loot == null ? Map.of() : Map.copyOf(loot);
        }
    }

    static final int STATE_SCHEMA = 2;
    // Additive fields default to legacy ALL/ALWAYS/no personal grants. Schema bump prevents older writers dropping controls.
    static final Map<Integer, java.util.function.UnaryOperator<com.google.gson.JsonObject>> STATE_MIGRATIONS = Map.of(2, value -> value);

    private void loadTowers() {
        towers.clear();
        TowersState state = store.load();
        var encoder = plugin.core().data().dataEncoder();
        for (Map.Entry<String, TowerRecord> saved : state.towers().entrySet()) {
            try {
                BlockKey key = BlockKey.parse(saved.getKey());
                TowerRecord record = saved.getValue();
                TowerType type = TowerType.byConfigKey(record.type() == null ? "" : record.type());
                if (type == null) {
                    throw new IllegalArgumentException("unknown type");
                }
                // Always up to 6 rows: if the loot chest was made smaller, older items are kept.
                ItemStack[] contents = new ItemStack[54];
                for (Map.Entry<Integer, String> slot : record.loot().entrySet()) {
                    if (slot.getKey() == null || slot.getKey() < 0 || slot.getKey() >= contents.length) continue;
                    ItemStack item = encoder.decodeItem(slot.getValue());
                    if (item != null && !item.getType().isAir() && item.getAmount() > 0) contents[slot.getKey()] = item;
                }
                Tower loaded = new Tower(key, type, record.damageLevel(), record.rangeLevel(), record.speedLevel(),
                        record.lootingLevel(), maximumUpgradeLevel, record.storedExperience(), contents);
                if (record.owner() != null && !record.owner().isBlank()) loaded.owner(UUID.fromString(record.owner()));
                loaded.targetProfile = TowerControls.TargetProfile.load(record.targetProfile());
                loaded.redstoneMode = TowerControls.RedstoneMode.load(record.redstoneMode());
                loaded.controls.load(record.grants());
                towers.put(key, loaded);
            } catch (RuntimeException exception) {
                DebugMessages.mid("[BlockIntegrations/TowerManager] Invalid tower entry in towers.json: " + saved.getKey());
            }
        }
    }

    private void queueSave() {
        if (saveQueued || !plugin.isEnabled()) {
            return;
        }
        saveQueued = true;
        // Batched (not after every kill); the file itself is written asynchronously.
        tasks.later(saveDelayTicks, () -> {
            saveQueued = false;
            saveTowers(true);
        });
    }

    private void saveTowers() {
        saveTowers(false);
    }

    /** Snapshot on the server thread (items are encoded here), file write asynchronously or directly. */
    private void saveTowers(boolean async) {
        var encoder = plugin.core().data().dataEncoder();
        Map<String, TowerRecord> records = new java.util.LinkedHashMap<>();
        for (Tower tower : towers.values()) {
            Inventory openLoot = lootInventories.get(tower.key);
            if (openLoot != null) {
                tower.setLootContents(openLoot.getContents());
            }
            Map<Integer, String> loot = new java.util.TreeMap<>();
            ItemStack[] contents = tower.lootContents(lootSize(tower));
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                    loot.put(slot, encoder.encodeItem(item));
                }
            }
            records.put(tower.key.serialize(), new TowerRecord(tower.type.configKey,
                    tower.level(TowerUpgrade.DAMAGE), tower.level(TowerUpgrade.RANGE), tower.level(TowerUpgrade.SPEED),
                    tower.level(TowerUpgrade.LOOTING), tower.storedExperience(),
                    tower.owner() == null ? null : tower.owner().toString(), loot,
                    tower.targetProfile.name(), tower.redstoneMode.name(), tower.controls.snapshot()));
        }
        TowersState state = new TowersState(records);
        long revision = ++nextSaveRevision;
        if (async && plugin.isEnabled()) tasks.async(() -> persistSnapshot(revision, state));
        else persistSnapshot(revision, state);
    }

    /** A delayed async snapshot must not overwrite newer rights, loot, or the final shutdown snapshot. */
    private synchronized void persistSnapshot(long revision, TowersState state) {
        if (revision <= lastWrittenRevision) return;
        lastWrittenRevision = revision;
        store.save(state);
    }

    private List<Integer> defaultCosts(TowerUpgrade upgrade) {
        return switch (upgrade) {
            case DAMAGE -> List.of(100, 175, 275, 400, 550,
                    750, 1000, 1300, 1650, 2050);
            case RANGE -> List.of(75, 125, 200, 300, 425,
                    575, 775, 1000, 1300, 1650);
            case SPEED -> List.of(125, 200, 300, 425, 600,
                    825, 1100, 1450, 1850, 2300);
            case LOOTING -> List.of(500, 1250, 2500);
        };
    }

    private double positive(double value, double minimum) {
        return Math.max(minimum, value);
    }

    private double bounded(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
