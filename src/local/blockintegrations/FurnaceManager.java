package local.blockintegrations;


import Vystorm.vystorm_core.schedule.TaskGroup;
import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.compat.dialog.DialogButton;
import Vystorm.vystorm_core.compat.dialog.DialogRequest;

import Vystorm.vystorm_core._parsersDebuggerEtc.debugger.DebugMessages;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import Vystorm.vystorm_core.compat.dialog.DialogBody;
import net.kyori.adventure.text.Component;

final class FurnaceManager implements Listener {
    void registerStorageDiagnostics() { pendingStore.registerDiagnostics(plugin.core().admin().diagnostics(), plugin); indexStore.registerDiagnostics(plugin.core().admin().diagnostics(), plugin); }

    private final BlockIntegrations plugin;
    private final NamespacedKey fixedOwnerKey;
    private final NamespacedKey experienceRemainderKey;
    private final Map<FurnaceUpgrade, NamespacedKey> levelKeys =
            new EnumMap<>(FurnaceUpgrade.class);
    private final Map<FurnaceUpgrade, List<Integer>> upgradeCosts =
            new EnumMap<>(FurnaceUpgrade.class);
    private final Map<String, Double> pendingSkillExperience = new HashMap<>();
    private final FurnaceMetrics metrics = new FurnaceMetrics();
    private final Set<FurnaceSmeltEvent> pendingManagedSmelts =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<FurnaceSmeltEvent> pendingYieldBonuses =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /** Pending Vystorm Skills furnace XP ({@code pending-furnace-skill-xp.json}, Core VersionedStore). */
    private final StateFile<PendingState> pendingStore;
    private final File legacyPendingFile;
    /** Stored with the imported balance, so an unsuccessful archive cannot credit it again. */
    private boolean legacyPendingImported;

    private boolean enabled;
    private int maximumLevel;
    private double xpBoostPerLevel;
    private FurnaceUpgradeStats stats;
    /** Core task group of this manager (deferred checks, XP delivery, batched saves); closed on shutdown. */
    private final TaskGroup tasks;
    private BukkitTask pendingSaveTask;
    /** Owned furnaces for the web page ({@code furnaces.json}); the furnace state itself stays in the block. */
    private final FurnaceIndex index = new FurnaceIndex();
    private final StateFile<FurnaceIndex.State> indexStore;
    private boolean indexSaveQueued;

    FurnaceManager(BlockIntegrations plugin) {
        this.plugin = plugin;
        this.fixedOwnerKey = new NamespacedKey(plugin, "furnace_fixed_owner");
        this.experienceRemainderKey = new NamespacedKey(plugin,
                "furnace_experience_remainder");
        this.tasks = plugin.core().tasks().of(plugin, "BlockIntegrations/FurnaceManager");
        this.pendingStore = new StateFile<>(new File(plugin.getDataFolder(), "pending-furnace-skill-xp.json").toPath(),
                "BlockIntegrations/FurnaceManager", PendingState.class, PENDING_SCHEMA, Map.of(), PendingState::empty, plugin.core().data().files());
        this.legacyPendingFile = new File(plugin.getDataFolder(),
                "pending-mcmmo-furnace-xp.yml");
        this.indexStore = new StateFile<>(new File(plugin.getDataFolder(), "furnaces.json").toPath(),
                "BlockIntegrations/FurnaceManager", FurnaceIndex.State.class, FurnaceIndex.SCHEMA, Map.of(), FurnaceIndex.State::empty, plugin.core().data().files());
        for (FurnaceUpgrade upgrade : FurnaceUpgrade.values()) {
            levelKeys.put(upgrade, new NamespacedKey(plugin,
                    "furnace_" + upgrade.configKey + "_level"));
        }
    }

    /**
     * The other shared events go through Core's stages too: the sneak click on a furnace is the feature acting on an
     * allowed click (ACT); the owner tag for Skills and the index follow the final outcome (OBSERVE). None of the
     * handlers sees a cancelled event.
     */
    void handleSharedEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, PlayerInteractEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.ACT, this::onFurnaceInteract);
        stages.handle(plugin, InventoryClickEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onFurnaceInventoryClick);
        stages.handle(plugin, org.bukkit.event.entity.EntityExplodeEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onEntityExplode);
        stages.handle(plugin, org.bukkit.event.block.BlockExplodeEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onBlockExplode);
    }
    void start() {
        loadSettings();
        loadPendingSkillExperience();
        loadIndex();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        handleBlockEvents(plugin.core().priority().stages());
        handleSharedEvents(plugin.core().priority().stages());
        tasks.later(20L, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                deliverPendingSkillExperience(player.getUniqueId());
            }
        });
        tasks.now(this::scanLoadedChunks);
    }

    void shutdown() {
        tasks.close();
        metrics.clear();
        pendingManagedSmelts.clear();
        pendingYieldBonuses.clear();
        pendingSaveTask = null;
        savePendingSkillExperience();
        indexStore.save(index.save());
    }

    void reloadSettings() {
        loadSettings();
    }

    /** Upgraded furnaces with an owner in the index (never loads chunks). */
    int furnaceCount() {
        return index.size();
    }

    int maximumLevel() {
        return maximumLevel;
    }

    void sendGuide(CommandSender sender) {
        guideLines(sender).forEach(line -> plugin.core().compat().platform().sendMessage(sender, line));
    }

    List<Component> guideLines(CommandSender viewer) {
        Lang lang = plugin.lang();
        List<Component> lines = new ArrayList<>(lang.lines(viewer, "furnace.guide.intro"));
        lines.add(lang.component(viewer, "furnace.guide.paths", "max", maximumLevel));
        lines.addAll(lang.lines(viewer, "furnace.guide.outro"));
        return lines;
    }

    /**
     * Block break and place go through Core's stages, so Core decides when they run among all Vystorm plugins.
     * Both only set up or recheck the stored furnace once the outcome is final: OBSERVE, never a cancelled event.
     */
    void handleBlockEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, BlockPlaceEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onFurnacePlace);
        stages.handle(plugin, org.bukkit.event.block.BlockBreakEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::onFurnaceBreak);
    }

    /** Stage OBSERVE, registered in {@link #handleBlockEvents}. */
    public void onFurnacePlace(BlockPlaceEvent event) {
        if (event instanceof BuildProbeEvent) return; // test placement, nothing was placed
        if (event.isCancelled() || !event.canBuild() || !enabled
                || !isFurnace(event.getBlockPlaced().getType())) {
            return;
        }
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        ItemStack sourceItem = event.getItemInHand().clone();
        Furnace furnace = furnace(block);
        if (furnace == null || event.isCancelled()) {
            return;
        }
        PortableBlockData.State portable = plugin.portableBlocks().read(sourceItem);
        if (portable != null && "furnace".equals(portable.kind())
                && portable.material() == block.getType()
                && portable.type().equals(block.getType().name())) {
            restorePortableFurnace(furnace, portable);
            return;
        }
        if (!player.hasPermission("blockintegrations.furnaces")) {
            return;
        }
        setFixedOwner(furnace, player.getUniqueId());
        if (furnace.update(false, false)) {
            remember(furnace);
            BlockKey key = BlockKey.of(block);
            Lang lang = plugin.lang();
            plugin.core().compat().platform().sendMessage(player, lang.component(player, "furnace.placed", "name", furnaceName(player, block.getType()))
                    .append(plugin.core().message().chat().button(lang.plain(player, "chat.upgrades"), lang.plain(player, "chat.upgrades-hover"),
                            p -> openFurnaceFromChat(p, key))));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFurnaceDrop(BlockDropItemEvent event) {
        if (event.isCancelled() || !enabled || !(event.getBlockState() instanceof Furnace furnace)
                || !isFurnace(furnace.getType())) {
            return;
        }
        for (org.bukkit.entity.Item dropped : event.getItems()) {
            ItemStack item = dropped.getItemStack();
            if (item == null || item.getType() != furnace.getType() || item.getAmount() != 1) {
                continue;
            }
            dropped.setItemStack(plugin.portableBlocks().write(item, event.getPlayer(), "furnace",
                    furnace.getType().name(), portableLevels(furnace), fixedOwner(furnace),
                    portableRemainder(furnace)));
            return;
        }
    }

    /* Stage ACT, registered in handleSharedEvents */
    public void onFurnaceInteract(PlayerInteractEvent event) {
        if (!enabled || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !event.getPlayer().isSneaking() || event.getClickedBlock() == null
                || !isFurnace(event.getClickedBlock().getType())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("blockintegrations.furnaces")) {
            plugin.lang().send(player, "furnace.no-use");
            return;
        }
        Furnace furnace = furnace(event.getClickedBlock());
        if (furnace == null) {
            return;
        }

        UUID owner = fixedOwner(furnace);
        if (owner == null) {
            owner = player.getUniqueId();
            setFixedOwner(furnace, owner);
            // The block state is a snapshot: write the claim to the block, otherwise it is lost again.
            furnace.update(true, false);
            remember(furnace);
            if (owner.equals(player.getUniqueId())) {
                plugin.lang().send(player, "furnace.claimed");
            }
        }
        if (!owner.equals(player.getUniqueId())
                && !player.hasPermission("blockintegrations.furnaces.manage-others")) {
            plugin.lang().send(player, "furnace.owned-by", "owner", ownerName(player, owner));
            return;
        }
        ensureSkillsOwner(furnace);
        openMenu(player, furnace);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurnOwnership(FurnaceBurnEvent event) {
        ensureManagedOwner(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFuelEfficiency(FurnaceBurnEvent event) {
        Furnace furnace = managedFurnace(event.getBlock());
        if (furnace == null) {
            return;
        }
        int level = level(furnace, FurnaceUpgrade.FUEL);
        if (level > 0) {
            event.setBurnTime(Math.max(1, (int) Math.round(
                    event.getBurnTime() * stats.fuelMultiplier(level))));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFuelMetrics(FurnaceBurnEvent event) {
        if (event.isBurning() && BukkitFeatures.consumesFuel(event) && managedFurnace(event.getBlock()) != null) {
            metrics.fuel(BlockKey.of(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSmeltOwnership(FurnaceSmeltEvent event) {
        ensureManagedOwner(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmeltUpgrades(FurnaceSmeltEvent event) {
        Furnace furnace = managedFurnace(event.getBlock());
        if (furnace == null) {
            return;
        }
        pendingManagedSmelts.add(event);
        int additionalInputs = applyMultiSmelt(furnace, event);
        int level = level(furnace, FurnaceUpgrade.YIELD);
        boolean yieldBonus = level > 0 && ThreadLocalRandom.current().nextDouble() < stats.yieldChance(level)
                && canAddBonus(furnace, event.getResult());
        if (yieldBonus) {
            ItemStack result = event.getResult().clone();
            result.setAmount(result.getAmount() + 1);
            event.setResult(result);
            plugin.core().particle().spawn(Particle.HAPPY_VILLAGER,
                    event.getBlock().getLocation().add(0.5D, 1.0D, 0.5D),
                    5, 0.25D, 0.2D, 0.25D, 0.0D, null);
        }
        if (yieldBonus) pendingYieldBonuses.add(event);
        if (additionalInputs > 0) {
            plugin.core().particle().spawn(Particle.FLAME,
                    event.getBlock().getLocation().add(0.5D, 0.8D, 0.5D),
                    Math.min(20, additionalInputs * 2), 0.25D, 0.2D, 0.25D, 0.01D, null);
        }
        scheduleAdditionalExperience(furnace, event, additionalInputs);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmeltMetrics(FurnaceSmeltEvent event) {
        boolean managed = pendingManagedSmelts.remove(event);
        if (!managed || event.isCancelled()) {
            pendingYieldBonuses.remove(event);
            return;
        }
        boolean yieldBonus = pendingYieldBonuses.remove(event);
        ItemStack result = event.getResult();
        int outputItems = result == null || result.getType().isAir() ? 0 : result.getAmount();
        metrics.smelt(BlockKey.of(event.getBlock()), outputItems, yieldBonus);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFastSmelt(FurnaceStartSmeltEvent event) {
        Furnace furnace = managedFurnace(event.getBlock());
        if (furnace == null) {
            return;
        }
        int level = level(furnace, FurnaceUpgrade.SPEED);
        if (level > 0) {
            event.setTotalCookTime(stats.cookTime(event.getTotalCookTime(), level));
        }
    }

    /* Stage OBSERVE, registered in handleSharedEvents */
    public void onFurnaceInventoryClick(InventoryClickEvent event) {
        Inventory top = plugin.core().compat().inventory().top(event.getView());
        if (plugin.core().gui().isManaged(top)) {
            return;
        }
        if (top instanceof FurnaceInventory furnaceInventory) {
            ensureSkillsOwner(furnaceInventory.getHolder());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof FurnaceInventory furnaceInventory) {
            ensureSkillsOwner(furnaceInventory.getHolder());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID owner = event.getPlayer().getUniqueId();
        tasks.later(20L, () -> deliverPendingSkillExperience(owner));
    }

    /** Chat button: permissions, ownership and distance are checked on click. */
    private void openFurnaceFromChat(Player player, BlockKey key) {
        Lang lang = plugin.lang();
        if (!player.hasPermission("blockintegrations.furnaces")) {
            lang.send(player, "furnace.no-use");
            return;
        }
        Furnace furnace = loadedFurnace(key);
        UUID owner = furnace == null ? null : fixedOwner(furnace);
        if (owner == null) {
            lang.send(player, "furnace.gone");
            return;
        }
        if (!owner.equals(player.getUniqueId())
                && !player.hasPermission("blockintegrations.furnaces.manage-others")) {
            lang.send(player, "furnace.owned-by", "owner", ownerName(player, owner));
            return;
        }
        if (!ChatLinks.near(plugin, player, key, "chat.too-far.furnace")) return;
        ensureSkillsOwner(furnace);
        openMenu(player, furnace);
    }

    private void openMenu(Player player, Furnace furnace) {
        Lang lang = plugin.lang();
        BlockKey key = BlockKey.of(furnace.getBlock());
        UUID shownOwner = fixedOwner(furnace);
        Material shownType = furnace.getType();
        String name = furnaceName(player, furnace.getType());
        List<DialogBody> body = new ArrayList<>();
        List<DialogButton> buttons = new ArrayList<>();
        body.add(DialogBody.plainMessage(lang.component(player, "furnace.dialog.header", "name", name,
                "owner", ownerName(player, fixedOwner(furnace)))));
        FurnaceMetrics.Snapshot observed = metrics.snapshot(key);
        body.add(DialogBody.plainMessage(lang.component(player, "furnace.metrics.observed",
                "seconds", 60, "smelts", observed.smeltCycles(), "fuel", observed.fuelItems(),
                "outputs", observed.outputItems(), "yield", observed.yieldBonusItems())));
        double queuedSkillXp = queuedSkillExperience(shownOwner);
        if (queuedSkillXp > 0.0D)
            body.add(DialogBody.plainMessage(lang.component(player, "furnace.metrics.pending-skill",
                    "xp", plugin.number(player, "%.2f", queuedSkillXp))));
        for (FurnaceUpgrade upgrade : FurnaceUpgrade.values()) {
            int shown = level(furnace, upgrade);
            int maximum = maximumLevel(upgrade);
            int cost = shown >= maximum ? 0 : upgradeCost(upgrade, shown);
            String upgradeName = lang.plain(player, upgrade.nameKey());
            String effect = upgradeValue(player, upgrade, shown);
            if (shown >= maximum) {
                body.add(DialogBody.plainMessage(lang.component(player, "upgrade.line-max", "upgrade", upgradeName,
                        "level", shown, "max", maximum, "value", effect)));
                continue;
            }
            String next = upgradeValue(player, upgrade, shown + 1);
            if (upgrade == FurnaceUpgrade.SPEED) {
                double current = stats.speedMultiplier(shown);
                double after = stats.speedMultiplier(shown + 1);
                body.add(DialogBody.plainMessage(lang.component(player, "furnace.metrics.speed-preview",
                        "current", plugin.number(player, "%.2f", current),
                        "next", plugin.number(player, "%.2f", after))));
            } else if (upgrade == FurnaceUpgrade.YIELD) {
                body.add(DialogBody.plainMessage(lang.component(player, "furnace.metrics.yield-preview",
                        "current", plugin.number(player, "%.1f %%", stats.yieldChance(shown) * 100.0D),
                        "next", plugin.number(player, "%.1f %%", stats.yieldChance(shown + 1) * 100.0D))));
            }
            body.add(DialogBody.plainMessage(lang.component(player, "upgrade.line", "upgrade", upgradeName,
                    "level", shown, "max", maximum, "value", effect, "next", next, "cost", cost)));
            buttons.add(new DialogButton(lang.component(player, "upgrade.button", "upgrade", upgradeName,
                    "next", next, "cost", cost),
                    (p, response) -> upgradeFurnace(p, key, upgrade, shown, cost)));
        }
        if (BlockIntegrations.enabled(plugin, Feature.BLUEPRINTS) && player.hasPermission("blockintegrations.furnaces.upgrade")) {
            EnumMap<FurnaceUpgrade, Integer> mine = blueprintLevels(furnace);
            buttons.add(new DialogButton(lang.component(player, "blueprint.save"), lang.component(player, "blueprint.save-hover"),
                    (p, response) -> { blueprints.put(p.getUniqueId(), mine); lang.send(p, "blueprint.saved"); }));
            EnumMap<FurnaceUpgrade, Integer> print = blueprints.get(player.getUniqueId());
            if (print != null) {
                List<FurnaceUpgrade> steps = MachineBlueprints.steps(print, mine);
                if (!steps.isEmpty()) {
                    long total = MachineBlueprints.cost(steps, mine, this::upgradeCost);
                    buttons.add(new DialogButton(lang.component(player, "blueprint.apply", "steps", steps.size(), "cost", total),
                            lang.component(player, "blueprint.apply-hover"), (p, response) -> applyBlueprint(p, key)));
                }
            }
        }
        String stored = plugin.number(player, "%.2f", storedExperience(furnace));
        buttons.add(new DialogButton(lang.component(player, "furnace.dialog.withdraw", "xp", stored),
                (p, response) -> withdrawFurnaceExperience(p, key)));
        plugin.addWebButton(buttons, player, "furnace/" + key.webId());
        buttons.add(PortableMenuButtons.back(plugin.core(), plugin::openBlockGuideMenu));
        buttons.add(PortableMenuButtons.home(plugin.core()));
        plugin.core().dialogs().open(plugin, player, new DialogRequest(
                lang.component(player, "furnace.dialog.title", "name", name), body, List.of(), buttons,
                p -> p.isOnline() && p.hasPermission("blockintegrations.furnaces")
                        && loadedFurnace(key) instanceof Furnace current && current.getType() == shownType
                        && java.util.Objects.equals(shownOwner, fixedOwner(current)),
                () -> {}, BlockIntegrations.CLOSE_LABEL, 3));
    }

    private void upgradeFurnace(Player player, BlockKey key, FurnaceUpgrade upgrade, int expectedLevel, int expectedCost) {
        Lang lang = plugin.lang();
        UpgradeResult result = applyUpgrade(player, key, upgrade, expectedLevel, expectedCost);
        Furnace furnace = loadedFurnace(key);
        switch (result.outcome()) {
            case NO_PERMISSION -> lang.send(player, "furnace.no-upgrade-permission");
            case GONE -> {
                player.closeInventory();
                lang.send(player, "furnace.gone");
            }
            case DENIED -> {
                player.closeInventory();
                lang.send(player, "furnace.owned-by", "owner", ownerName(player, furnace == null ? null : fixedOwner(furnace)));
            }
            case MAXED -> lang.send(player, "furnace.maxed");
            case CHANGED -> {
                lang.send(player, "upgrade.offer-changed");
                if (furnace != null) openMenu(player, furnace);
            }
            case NEED_XP -> lang.send(player, "upgrade.need-xp", "cost", result.cost());
            case DONE -> {
                if (furnace != null) openMenu(player, furnace);
            }
            default -> { }
        }
    }

    /**
     * Checks and applies a furnace upgrade: permission, loaded owned furnace, owner or
     * {@code blockintegrations.furnaces.manage-others}, max level, the offer the player saw ({@code expectedLevel < 0}
     * skips it) and the XP cost. Used by the upgrade dialog and the web page; on success the player gets the chat
     * confirmation and the anvil sound.
     */
    UpgradeResult applyUpgrade(Player player, BlockKey key, FurnaceUpgrade upgrade, int expectedLevel, int expectedCost) {
        return applyUpgrade(player, key, upgrade, expectedLevel, expectedCost, true);
    }

    /** As above; {@code announce = false} skips the per-step chat line and sound (blueprints report once). */
    UpgradeResult applyUpgrade(Player player, BlockKey key, FurnaceUpgrade upgrade, int expectedLevel, int expectedCost, boolean announce) {
        Lang lang = plugin.lang();
        if (!player.hasPermission("blockintegrations.furnaces.upgrade")) return UpgradeResult.of(UpgradeResult.Outcome.NO_PERMISSION);
        Furnace furnace = loadedFurnace(key);
        if (furnace == null || fixedOwner(furnace) == null) return UpgradeResult.of(UpgradeResult.Outcome.GONE);
        UUID owner = fixedOwner(furnace);
        if (!owner.equals(player.getUniqueId())
                && !player.hasPermission("blockintegrations.furnaces.manage-others")) {
            return UpgradeResult.of(UpgradeResult.Outcome.DENIED);
        }
        int current = level(furnace, upgrade);
        if (current >= maximumLevel(upgrade)) return new UpgradeResult(UpgradeResult.Outcome.MAXED, current, 0);
        int cost = upgradeCost(upgrade, current);
        if (expectedLevel >= 0 && (current != expectedLevel || cost != expectedCost))
            return new UpgradeResult(UpgradeResult.Outcome.CHANGED, current, cost);
        if (!takeExperience(player, cost)) return new UpgradeResult(UpgradeResult.Outcome.NEED_XP, current, cost);
        setLevel(furnace, upgrade, current + 1);
        ensureSkillsOwner(furnace);
        remember(furnace);
        if (announce) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7F, 1.35F);
            lang.send(player, "upgrade.done", "upgrade", lang.plain(player, upgrade.nameKey()),
                    "level", current + 1, "cost", cost);
        }
        return new UpgradeResult(UpgradeResult.Outcome.DONE, current + 1, cost);
    }

    private void withdrawFurnaceExperience(Player player, BlockKey key) {
        Lang lang = plugin.lang();
        Furnace furnace = loadedFurnace(key);
        if (furnace == null) {
            player.closeInventory();
            lang.send(player, "furnace.gone");
            return;
        }
        UUID owner = fixedOwner(furnace);
        if (owner == null) {
            player.closeInventory();
            lang.send(player, "furnace.no-owner");
            return;
        }
        if (!owner.equals(player.getUniqueId())) {
            lang.send(player, "furnace.only-owner");
            return;
        }

        double stored = storedExperience(furnace);
        int payout = FurnaceStoredExperience.payablePoints(stored);
        if (payout <= 0) {
            lang.send(player, "furnace.no-xp");
            return;
        }

        BukkitFeatures.clearRecipes(furnace);
        furnace.getPersistentDataContainer().set(experienceRemainderKey,
                PersistentDataType.DOUBLE,
                FurnaceStoredExperience.remainder(stored, payout));
        ensureSkillsOwner(furnace);
        if (!furnace.update(false, false)) {
            lang.send(player, "furnace.withdraw-failed");
            return;
        }
        plugin.core().player().giveExperience(player, payout);
        plugin.structuresChanged(owner);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                0.8F, 1.15F);
        lang.send(player, "furnace.withdrawn", "xp", payout, "name", furnaceName(player, furnace.getType()));
        openMenu(player, furnace);
    }

    private double storedExperience(Furnace furnace) {
        Double remainder = furnace.getPersistentDataContainer().get(
                experienceRemainderKey, PersistentDataType.DOUBLE);
        return FurnaceStoredExperience.total(remainder == null ? 0.0D : remainder,
                furnace.getRecipesUsed());
    }

    private String upgradeValue(CommandSender viewer, FurnaceUpgrade upgrade, int level) {
        return upgradeValue(plugin.lang().language(viewer), upgrade, level);
    }

    private String upgradeValue(String language, FurnaceUpgrade upgrade, int level) {
        Lang lang = plugin.lang();
        return switch (upgrade) {
            case SPEED -> lang.plain(language, "furnace.value.speed", "value", plugin.number(language, "%.1f", stats.speedMultiplier(level)));
            case FUEL -> lang.plain(language, "furnace.value.fuel", "value", plugin.number(language, "%.1f", stats.fuelMultiplier(level)));
            case YIELD -> lang.plain(language, "furnace.value.yield", "value", plugin.number(language, "%.1f %%", stats.yieldChance(level) * 100.0D));
            case MULTISMELT -> lang.plain(language, "furnace.value.multismelt", "items", FurnaceMultiSmelt.itemsPerCycle(level));
            case XP_BOOST -> lang.plain(language, "furnace.value.xp-boost", "value", plugin.number(language, "%.1f", FurnaceExperienceBoost.multiplier(
                    level, xpBoostPerLevel)));
        };
    }

    private int maximumLevel(FurnaceUpgrade upgrade) {
        return Math.min(maximumLevel, upgrade.maximumLevel);
    }

    private void loadSettings() {
        FileConfiguration config = plugin.getConfig();
        enabled = BlockIntegrations.enabled(plugin, Feature.FURNACES);
        maximumLevel = Math.max(1, Math.min(10,
                config.getInt("furnaces.upgrades.maximum-level", 10)));
        xpBoostPerLevel = bounded(config.getDouble(
                "furnaces.xp-boost-per-level", 0.20D), 0.0D, 2.0D);
        stats = new FurnaceUpgradeStats(
                bounded(config.getDouble("furnaces.speed-multiplier-per-level", 0.12D),
                        0.0D, 1.0D),
                bounded(config.getDouble("furnaces.fuel-efficiency-per-level", 0.10D),
                        0.0D, 2.0D),
                bounded(config.getDouble("furnaces.bonus-yield-chance-per-level", 0.025D),
                        0.0D, 1.0D));
        for (FurnaceUpgrade upgrade : FurnaceUpgrade.values()) {
            List<Integer> costs = new ArrayList<>(config.getIntegerList(
                    "furnaces.upgrades." + upgrade.configKey + "-costs"));
            if (costs.isEmpty()) {
                costs = new ArrayList<>(defaultCosts(upgrade));
            }
            costs.replaceAll(cost -> Math.max(0, cost));
            upgradeCosts.put(upgrade, List.copyOf(costs));
        }
    }

    private void ensureManagedOwner(Block block) {
        Furnace furnace = managedFurnace(block);
        if (furnace != null) {
            ensureSkillsOwner(furnace);
            FurnaceIndex.Entry known = index.get(BlockKey.of(block));
            if (known == null || !known.owner().equals(fixedOwner(furnace)) || known.type() != furnace.getType()) remember(furnace);
        }
    }

    private Furnace managedFurnace(Block block) {
        if (!enabled) {
            return null;
        }
        Furnace furnace = furnace(block);
        return furnace != null && fixedOwner(furnace) != null ? furnace : null;
    }

    private Furnace loadedFurnace(BlockKey key) {
        org.bukkit.World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
            return null;
        }
        return furnace(world.getBlockAt(key.x(), key.y(), key.z()));
    }

    private Furnace furnace(Block block) {
        if (block == null || !isFurnace(block.getType())) {
            return null;
        }
        BlockState state = block.getState();
        return state instanceof Furnace furnace ? furnace : null;
    }

    private UUID fixedOwner(Furnace furnace) {
        String serialized = furnace.getPersistentDataContainer().get(
                fixedOwnerKey, PersistentDataType.STRING);
        if (serialized == null) {
            return null;
        }
        try {
            return UUID.fromString(serialized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void setFixedOwner(Furnace furnace, UUID owner) {
        furnace.getPersistentDataContainer().set(
                fixedOwnerKey, PersistentDataType.STRING, owner.toString());
        furnace.getPersistentDataContainer().set(
                SkillsBridge.OWNER_KEY, PersistentDataType.STRING, owner.toString());
    }

    private void ensureSkillsOwner(Furnace furnace) {
        if (furnace == null) {
            return;
        }
        UUID fixed = fixedOwner(furnace);
        if (fixed == null) {
            return;
        }
        String current = furnace.getPersistentDataContainer().get(
                SkillsBridge.OWNER_KEY, PersistentDataType.STRING);
        if (!fixed.toString().equals(current)) {
            furnace.getPersistentDataContainer().set(
                    SkillsBridge.OWNER_KEY, PersistentDataType.STRING, fixed.toString());
            furnace.update(true, false);
        }
    }

    /** Blueprints saved per player this server session (machine blueprints). */
    private final Map<UUID, EnumMap<FurnaceUpgrade, Integer>> blueprints = new java.util.concurrent.ConcurrentHashMap<>();

    private EnumMap<FurnaceUpgrade, Integer> blueprintLevels(Furnace furnace) {
        EnumMap<FurnaceUpgrade, Integer> levels = new EnumMap<>(FurnaceUpgrade.class);
        for (FurnaceUpgrade upgrade : FurnaceUpgrade.values()) levels.put(upgrade, level(furnace, upgrade));
        return levels;
    }

    /** Raises this furnace to the player's blueprint, one paid upgrade at a time; stops at the first refusal. */
    private void applyBlueprint(Player player, BlockKey key) {
        Lang lang = plugin.lang();
        EnumMap<FurnaceUpgrade, Integer> print = blueprints.get(player.getUniqueId());
        Furnace furnace = loadedFurnace(key);
        if (print == null || furnace == null) { lang.send(player, "furnace.gone"); return; }
        int done = 0; long spent = 0; UpgradeResult last = null;
        for (FurnaceUpgrade step : MachineBlueprints.steps(print, blueprintLevels(furnace))) {
            last = applyUpgrade(player, key, step, -1, 0, false);
            if (!last.done()) break;
            done++; spent += last.cost();
        }
        if (done > 0) player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7F, 1.35F);
        if (last == null || last.done()) lang.send(player, "blueprint.applied", "steps", done, "cost", spent);
        else if (last.outcome() == UpgradeResult.Outcome.NEED_XP) lang.send(player, "blueprint.partial", "steps", done, "cost", spent, "need", last.cost());
        else lang.send(player, "blueprint.stopped", "steps", done, "cost", spent);
        Furnace after = loadedFurnace(key);
        if (after != null) openMenu(player, after);
    }

    private int level(Furnace furnace, FurnaceUpgrade upgrade) {
        Integer stored = furnace.getPersistentDataContainer().get(
                levelKeys.get(upgrade), PersistentDataType.INTEGER);
        return Math.max(0, Math.min(maximumLevel(upgrade), stored == null ? 0 : stored));
    }

    private void setLevel(Furnace furnace, FurnaceUpgrade upgrade, int level) {
        PersistentDataContainer data = furnace.getPersistentDataContainer();
        data.set(levelKeys.get(upgrade), PersistentDataType.INTEGER,
                Math.max(0, Math.min(maximumLevel(upgrade), level)));
        furnace.update(true, false);
    }

    private Map<String, Integer> portableLevels(Furnace furnace) {
        Map<String, Integer> levels = new HashMap<>();
        for (FurnaceUpgrade upgrade : FurnaceUpgrade.values()) {
            levels.put(upgrade.configKey, level(furnace, upgrade));
        }
        return levels;
    }

    private double portableRemainder(Furnace furnace) {
        Double remainder = furnace.getPersistentDataContainer().get(
                experienceRemainderKey, PersistentDataType.DOUBLE);
        return remainder != null && Double.isFinite(remainder)
                ? Math.max(0.0D, remainder) : 0.0D;
    }

    private void restorePortableFurnace(Furnace furnace, PortableBlockData.State portable) {
        PersistentDataContainer data = furnace.getPersistentDataContainer();
        for (FurnaceUpgrade upgrade : FurnaceUpgrade.values()) {
            Integer stored = portable.levels().get(upgrade.configKey);
            data.set(levelKeys.get(upgrade), PersistentDataType.INTEGER,
                    Math.max(0, Math.min(maximumLevel(upgrade), stored == null ? 0 : stored)));
        }
        data.set(experienceRemainderKey, PersistentDataType.DOUBLE, portable.experience());
        if (portable.owner() != null) {
            setFixedOwner(furnace, portable.owner());
            ensureSkillsOwner(furnace);
        }
        if (!furnace.update(false, false)) {
            DebugMessages.mid("[FurnaceManager] Could not restore a moved furnace.");
        } else {
            remember(furnace);
        }
    }

    private boolean canAddBonus(Furnace furnace, ItemStack eventResult) {
        if (eventResult == null || eventResult.getType().isAir()
                || eventResult.getAmount() >= eventResult.getMaxStackSize()) {
            return false;
        }
        ItemStack stored = furnace.getInventory().getResult();
        if (stored == null || stored.getType().isAir()) {
            return eventResult.getAmount() + 1 <= eventResult.getMaxStackSize();
        }
        return stored.isSimilar(eventResult)
                && stored.getAmount() + eventResult.getAmount() + 1
                <= Math.min(stored.getMaxStackSize(), eventResult.getMaxStackSize());
    }

    private int applyMultiSmelt(Furnace furnace, FurnaceSmeltEvent event) {
        int multiSmeltLevel = level(furnace, FurnaceUpgrade.MULTISMELT);
        if (multiSmeltLevel <= 0) {
            return 0;
        }
        CookingRecipe<?> recipe = BukkitFeatures.recipe(event, furnace);
        if (recipe == null) {
            return 0;
        }

        FurnaceInventory inventory = furnace.getInventory();
        ItemStack source = inventory.getSmelting();
        ItemStack eventSource = event.getSource();
        ItemStack eventResult = event.getResult();
        ItemStack recipeResult = recipe.getResult();
        if (source == null || source.getType().isAir() || source.getAmount() <= 1
                || eventSource == null || !source.isSimilar(eventSource)
                || eventResult == null || eventResult.getType().isAir()
                || recipeResult == null || recipeResult.getType().isAir()
                || !eventResult.isSimilar(recipeResult)) {
            return 0;
        }

        ItemStack storedOutput = inventory.getResult();
        if (storedOutput != null && !storedOutput.getType().isAir()
                && !storedOutput.isSimilar(eventResult)) {
            return 0;
        }
        int storedOutputAmount = storedOutput == null || storedOutput.getType().isAir()
                ? 0 : storedOutput.getAmount();
        int maximumStackSize = storedOutput == null || storedOutput.getType().isAir()
                ? eventResult.getMaxStackSize()
                : Math.min(storedOutput.getMaxStackSize(), eventResult.getMaxStackSize());
        FurnaceMultiSmelt.Plan plan = FurnaceMultiSmelt.plan(multiSmeltLevel,
                source.getAmount(), storedOutputAmount, eventResult.getAmount(),
                recipeResult.getAmount(), maximumStackSize);
        if (plan.additionalInputs() <= 0) {
            return 0;
        }

        // Replacing the source stack detaches Purpur's original stack reference.
        // Therefore this handler consumes the complete batch, including the one
        // base item that vanilla would otherwise remove after the event.
        int remainingAmount = source.getAmount() - plan.additionalInputs() - 1;
        if (remainingAmount <= 0) {
            inventory.setSmelting(null);
        } else {
            ItemStack remainingSource = source.clone();
            remainingSource.setAmount(remainingAmount);
            inventory.setSmelting(remainingSource);
        }

        ItemStack combinedResult = eventResult.clone();
        combinedResult.setAmount(eventResult.getAmount() + plan.additionalOutput());
        event.setResult(combinedResult);

        return plan.additionalInputs();
    }

    private void scheduleAdditionalExperience(Furnace furnace, FurnaceSmeltEvent event,
            int additionalInputs) {
        BlockKey key = BlockKey.of(furnace.getBlock());
        UUID owner = fixedOwner(furnace);
        var recipe = BukkitFeatures.recipe(event, furnace);
        if (recipe == null) {
            return;
        }
        int xpBoostLevel = level(furnace, FurnaceUpgrade.XP_BOOST);
        double vanillaExperience = FurnaceExperienceBoost.additionalExperience(
                recipe.getExperience(), additionalInputs,
                xpBoostLevel, xpBoostPerLevel);
        // Vystorm Skills gibt für diese Öfen kein Entnahme-XP; die Grund-XP jedes Items kommt daher von hier.
        Material result = event.getResult().getType();
        String discipline = SkillsBridge.discipline(furnace.getType(), result);
        double baseSkillExperience = SkillsBridge.furnaceXp(furnace.getType(), result);
        double skillExperience = baseSkillExperience * (1 + Math.max(0, additionalInputs))
                * FurnaceExperienceBoost.multiplier(xpBoostLevel, xpBoostPerLevel);
        if (vanillaExperience <= 0.0D && skillExperience <= 0.0D) {
            return;
        }
        tasks.now(() -> {
            if (event.isCancelled()) {
                return;
            }
            Furnace current = loadedFurnace(key);
            if (current == null || owner == null || !owner.equals(fixedOwner(current))) {
                return;
            }
            if (vanillaExperience > 0.0D) {
                PersistentDataContainer data = current.getPersistentDataContainer();
                Double stored = data.get(experienceRemainderKey, PersistentDataType.DOUBLE);
                data.set(experienceRemainderKey, PersistentDataType.DOUBLE,
                        (stored == null ? 0.0D : stored) + vanillaExperience);
                current.update(true, false);
            }
            queueSkillExperience(owner, discipline, skillExperience);
        });
    }

    private void queueSkillExperience(UUID owner, String discipline, double experience) {
        if (owner == null || !Double.isFinite(experience) || experience <= 0.0D) {
            return;
        }
        pendingSkillExperience.merge(pendingKey(owner, discipline), experience, Double::sum);
        schedulePendingSkillSave();
        deliverPendingSkillExperience(owner);
    }

    private static String pendingKey(UUID owner, String discipline) {
        return owner + ":" + discipline;
    }

    /** Owner-wide pending skill XP; viewing this value never delivers or consumes it. */
    double queuedSkillExperience(UUID owner) {
        if (owner == null) return 0.0D;
        String prefix = owner + ":";
        double total = 0.0D;
        for (var entry : pendingSkillExperience.entrySet()) {
            if (entry.getKey().startsWith(prefix) && Double.isFinite(entry.getValue()) && entry.getValue() > 0.0D)
                total = Math.min(Double.MAX_VALUE, total + entry.getValue());
        }
        return total;
    }

    private void deliverPendingSkillExperience(UUID owner) {
        Player player = Bukkit.getPlayer(owner);
        if (player == null || !player.isOnline()) {
            return;
        }
        String prefix = owner + ":";
        for (String key : new ArrayList<>(pendingSkillExperience.keySet())) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            double pending = pendingSkillExperience.getOrDefault(key, 0.0D);
            int payable = FurnaceSkillExperience.payablePoints(pending);
            if (payable <= 0) {
                continue;
            }
            if (!SkillsBridge.award(player, key.substring(prefix.length()), payable)) {
                // Vystorm Skills noch nicht bereit (z. B. Login): später erneut.
                tasks.later(100L, () -> deliverPendingSkillExperience(owner));
                return;
            }
            double remainder = FurnaceSkillExperience.remainder(pending, payable);
            if (remainder <= 0.0D) {
                pendingSkillExperience.remove(key);
            } else {
                pendingSkillExperience.put(key, remainder);
            }
        }
        schedulePendingSkillSave();
    }

    /** {@code pending-furnace-skill-xp.json}, schema 2: XP and an atomic legacy-import marker. */
    record PendingState(Map<String, Double> pending, boolean legacyImported) {
        PendingState(Map<String, Double> pending) { this(pending, false); }
        PendingState {
            pending = pending == null ? Map.of() : Map.copyOf(pending);
        }

        static PendingState empty() {
            return new PendingState(Map.of());
        }
    }

    static final int PENDING_SCHEMA = 2;

    private void loadPendingSkillExperience() {
        pendingSkillExperience.clear();
        PendingState loaded = pendingStore.load();
        if (legacyPendingFile.isFile() && !pendingStore.readOnly()) {
            if (!loaded.legacyImported()) {
                try {
                    Map<String, Double> merged = new HashMap<>(loaded.pending());
                    readLegacyPending(legacyPendingFile).forEach((key, value) -> merged.merge(key, value, Double::sum));
                    PendingState imported = new PendingState(merged, true);
                    // Persist both the balance and marker before retiring its only other durable copy.
                    if (pendingStore.saveChecked(imported)) loaded = imported;
                    else DebugMessages.mid("[BlockIntegrations/FurnaceManager] Legacy furnace XP was not imported; the source file stays available for retry.");
                } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException failure) {
                    DebugMessages.mid("[BlockIntegrations/FurnaceManager] Could not read " + legacyPendingFile.getName()
                            + "; it was left unchanged: " + failure.getMessage());
                }
            }
            if (loaded.legacyImported()
                    && !legacyPendingFile.renameTo(new File(legacyPendingFile.getPath() + ".migrated"))) {
                DebugMessages.mid("[BlockIntegrations/FurnaceManager] Could not archive " + legacyPendingFile.getName()
                        + "; the saved import marker prevents duplicate XP.");
            }
        }
        legacyPendingImported = loaded.legacyImported();
        for (Map.Entry<String, Double> entry : loaded.pending().entrySet()) {
            double experience = entry.getValue() == null ? 0.0D : entry.getValue();
            if (!Double.isFinite(experience) || experience <= 0.0D) continue;
            try {
                UUID.fromString(entry.getKey().substring(0, Math.max(0, entry.getKey().indexOf(':'))));
            } catch (IllegalArgumentException invalid) {
                DebugMessages.mid("[BlockIntegrations/FurnaceManager] Invalid pending XP entry: " + entry.getKey());
                continue;
            }
            pendingSkillExperience.merge(entry.getKey(), experience, Double::sum);
        }
    }

    /** Strictly parses the old file without changing the live or persisted pending balances. */
    private Map<String, Double> readLegacyPending(File file)
            throws java.io.IOException, org.bukkit.configuration.InvalidConfigurationException {
        Map<String, Double> pending = new HashMap<>();
        YamlConfiguration storage = new YamlConfiguration();
        storage.load(file);
        if (storage.contains("pending") && !storage.isConfigurationSection("pending"))
            throw new java.io.IOException("pending must be a section");
        if (storage.getConfigurationSection("pending") == null) {
            return pending;
        }
        for (String key : storage.getConfigurationSection("pending").getKeys(false)) {
            double experience = storage.getDouble("pending." + key, 0.0D);
            if (!Double.isFinite(experience) || experience <= 0.0D) {
                continue;
            }
            try {
                UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                DebugMessages.mid("[BlockIntegrations/FurnaceManager] Invalid entry in " + file.getName() + ": " + key);
                continue;
            }
            pending.merge(key + ":smelting", experience, Double::sum);
        }
        return pending;
    }

    private void schedulePendingSkillSave() {
        if (pendingSaveTask != null) {
            return;
        }
        pendingSaveTask = tasks.later(20L, () -> {
            pendingSaveTask = null;
            savePendingSkillExperience();
        });
    }

    private void savePendingSkillExperience() {
        Map<String, Double> pending = new java.util.TreeMap<>();
        for (Map.Entry<String, Double> entry : pendingSkillExperience.entrySet()) {
            if (Double.isFinite(entry.getValue()) && entry.getValue() > 0.0D) {
                pending.put(entry.getKey(), entry.getValue());
            }
        }
        pendingStore.save(new PendingState(pending, legacyPendingImported));
    }

    private int upgradeCost(FurnaceUpgrade upgrade, int currentLevel) {
        List<Integer> costs = upgradeCosts.get(upgrade);
        return costs.get(Math.min(currentLevel, costs.size() - 1));
    }

    private boolean takeExperience(Player player, int cost) {
        if (cost <= 0 || player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        return plugin.core().player().withdrawExperience(player, cost);
    }

    private List<Integer> defaultCosts(FurnaceUpgrade upgrade) {
        return switch (upgrade) {
            case SPEED -> List.of(100, 175, 275, 400, 550,
                    750, 1000, 1300, 1650, 2050);
            case FUEL -> List.of(75, 125, 200, 300, 425,
                    575, 775, 1000, 1300, 1650);
            case YIELD -> List.of(150, 250, 375, 525, 700,
                    900, 1150, 1450, 1800, 2200);
            case MULTISMELT -> List.of(300, 500, 750, 1100, 1500,
                    2000, 2600, 3300, 4100, 5000);
            case XP_BOOST -> List.of(250, 600, 1100, 1800, 2700);
        };
    }

    private String ownerName(CommandSender viewer, UUID owner) {
        if (owner == null) {
            return plugin.lang().plain(viewer, "furnace.unknown-owner");
        }
        String name = Bukkit.getOfflinePlayer(owner).getName();
        return name == null || name.isBlank() ? owner.toString() : name;
    }

    // ---------------------------------------------------------------------------------------------
    // Furnace index (web page "My structures"; never loads chunks)

    /** Records an owned furnace (owner, type, levels) in the index; forgets it when it has no owner. */
    private void remember(Furnace furnace) {
        if (furnace.getBlock() == null) return;
        BlockKey key = BlockKey.of(furnace.getBlock());
        UUID owner = fixedOwner(furnace);
        if (owner == null) {
            forget(key);
            return;
        }
        FurnaceIndex.Entry previous = index.get(key);
        if (index.put(key, new FurnaceIndex.Entry(owner, furnace.getType(), portableLevels(furnace)))) {
            queueIndexSave();
            if (previous != null && !previous.owner().equals(owner)) plugin.structuresChanged(previous.owner());
        }
        plugin.structuresChanged(owner);
    }

    private void forget(BlockKey key) {
        FurnaceIndex.Entry removed = index.remove(key);
        if (removed != null) {
            queueIndexSave();
            plugin.structuresChanged(removed.owner());
        }
    }

    /** After a break/explosion: drop the entry if the block is no longer an owned furnace. */
    private void recheck(BlockKey key) {
        if (index.get(key) == null) return;
        org.bukkit.World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) return;
        Furnace furnace = furnace(world.getBlockAt(key.x(), key.y(), key.z()));
        if (furnace == null || fixedOwner(furnace) == null) forget(key);
        else remember(furnace);
    }

    /** Stage OBSERVE, registered in {@link #handleBlockEvents}. */
    public void onFurnaceBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (!isFurnace(event.getBlock().getType())) return;
        BlockKey key = BlockKey.of(event.getBlock());
        metrics.remove(key);
        if (index.get(key) != null) tasks.now(() -> recheck(key));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        // Furnace metrics are transient and keyed by world/coordinates; remove the unloaded chunk's entries.
        UUID worldId = event.getWorld().getUID();
        metrics.removeChunk(worldId, chunkX, chunkZ);
    }

    /* Stage OBSERVE, registered in handleSharedEvents */
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        recheckLater(event.blockList());
    }

    /* Stage OBSERVE, registered in handleSharedEvents */
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        recheckLater(event.blockList());
    }

    private void recheckLater(List<Block> blocks) {
        List<BlockKey> keys = new ArrayList<>();
        for (Block block : blocks) {
            if (!isFurnace(block.getType())) continue;
            BlockKey key = BlockKey.of(block);
            metrics.remove(key);
            if (index.get(key) != null) keys.add(key);
        }
        if (!keys.isEmpty()) tasks.now(() -> keys.forEach(this::recheck));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (!enabled) return;
        org.bukkit.Chunk chunk = event.getChunk();
        tasks.now(() -> scanChunk(chunk));
    }

    private void scanLoadedChunks() {
        if (!enabled) return;
        for (org.bukkit.World world : Bukkit.getWorlds())
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) scanChunk(chunk);
    }

    /** Indexes the owned furnaces of a loaded chunk (only furnace block entities are looked at). */
    private void scanChunk(org.bukkit.Chunk chunk) {
        if (!chunk.isLoaded()) return;
        for (BlockState state : BukkitFeatures.furnaceStates(chunk)) {
            if (!(state instanceof Furnace furnace) || fixedOwner(furnace) == null) continue;
            FurnaceIndex.Entry known = index.get(BlockKey.of(furnace.getBlock()));
            if (known == null || !known.owner().equals(fixedOwner(furnace)) || known.type() != furnace.getType()
                    || !known.levels().equals(portableLevels(furnace))) remember(furnace);
        }
    }

    private void loadIndex() {
        index.load(indexStore.load());
    }

    private void queueIndexSave() {
        if (indexSaveQueued || !plugin.isEnabled()) return;
        indexSaveQueued = true;
        tasks.later(100L, () -> {
            indexSaveQueued = false;
            FurnaceIndex.State state = index.save();
            tasks.async(() -> indexStore.save(state));
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Web page (called by BlocksWeb on the server thread)

    List<Map.Entry<BlockKey, FurnaceIndex.Entry>> furnacesOwnedBy(UUID owner) {
        return index.of(owner);
    }

    List<Map.Entry<BlockKey, FurnaceIndex.Entry>> allFurnaces() {
        return index.all();
    }

    FurnaceIndex.Entry indexed(BlockKey key) {
        return index.get(key);
    }

    /** Same rule as the upgrade dialog: the fixed owner, or {@code blockintegrations.furnaces.manage-others}. */
    boolean webAccess(Player player, UUID owner) {
        return player.getUniqueId().equals(owner) || player.hasPermission("blockintegrations.furnaces.manage-others");
    }

    /**
     * The furnace as the dialog shows it: live from the block when its chunk is loaded (the index is corrected on the
     * way), else the last seen type and levels ({@code state = unloaded}). {@code null} when it turned out to be gone.
     */
    com.google.gson.JsonObject webView(BlockKey key, FurnaceIndex.Entry entry, String language) {
        Lang lang = plugin.lang();
        org.bukkit.World world = Bukkit.getWorld(key.worldId());
        boolean loaded = world != null && world.isChunkLoaded(key.chunkX(), key.chunkZ());
        Furnace furnace = loaded ? loadedFurnace(key) : null;
        if (loaded && (furnace == null || fixedOwner(furnace) == null)) {
            forget(key);
            return null;
        }
        Material type = furnace != null ? furnace.getType() : entry.type();
        UUID owner = furnace != null ? fixedOwner(furnace) : entry.owner();
        if (furnace != null && (!owner.equals(entry.owner()) || type != entry.type()
                || !entry.levels().equals(portableLevels(furnace)))) remember(furnace);
        com.google.gson.JsonObject json = WebJson.structure("furnace", key, type.name().toLowerCase(Locale.ROOT),
                lang.plain(language, furnaceNameKey(type)), WebJson.color(type));
        json.addProperty("state", furnace != null ? "active" : "unloaded");
        json.addProperty("owner", ownerName(language, owner));
        json.addProperty("ownerId", owner.toString());
        if (furnace != null) json.addProperty("xp", plugin.number(language, "%.2f", storedExperience(furnace)));
        com.google.gson.JsonArray upgrades = new com.google.gson.JsonArray();
        for (FurnaceUpgrade upgrade : FurnaceUpgrade.values()) {
            int maximum = maximumLevel(upgrade);
            int level = furnace != null ? level(furnace, upgrade)
                    : Math.max(0, Math.min(maximum, entry.levels().getOrDefault(upgrade.configKey, 0)));
            boolean maxed = level >= maximum;
            upgrades.add(WebJson.upgrade(upgrade.configKey, lang.plain(language, upgrade.nameKey()), level, maximum,
                    upgradeValue(language, upgrade, level), maxed ? null : upgradeValue(language, upgrade, level + 1),
                    maxed ? null : upgradeCost(upgrade, level)));
        }
        json.add("upgrades", upgrades);
        return json;
    }

    /** Guide: supported furnaces, what they do and the upgrade tables. */
    com.google.gson.JsonObject webGuide(String language) {
        Lang lang = plugin.lang();
        com.google.gson.JsonObject guide = new com.google.gson.JsonObject();
        com.google.gson.JsonArray blocks = new com.google.gson.JsonArray();
        for (Material type : new Material[] {Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER})
            blocks.add(WebJson.block(type.name().toLowerCase(Locale.ROOT), type, lang.plain(language, furnaceNameKey(type))));
        guide.add("blocks", blocks);
        com.google.gson.JsonArray upgrades = new com.google.gson.JsonArray();
        for (FurnaceUpgrade upgrade : FurnaceUpgrade.values()) {
            int maximum = maximumLevel(upgrade);
            com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
            for (int level = 0; level <= maximum; level++)
                rows.add(WebJson.levelRow(level, upgradeValue(language, upgrade, level), level >= maximum ? null : upgradeCost(upgrade, level)));
            com.google.gson.JsonObject table = WebJson.upgradeTable(upgrade.configKey, lang.plain(language, upgrade.nameKey()), maximum, rows);
            table.addProperty("description", lang.plain(language, "web.guide.furnace." + upgrade.configKey));
            upgrades.add(table);
        }
        guide.add("upgrades", upgrades);
        List<String> notes = new ArrayList<>();
        for (Component line : lang.lines(language, "furnace.guide.intro")) notes.add(WebJson.plain(line));
        for (Component line : lang.lines(language, "furnace.guide.outro")) notes.add(WebJson.plain(line));
        if (!notes.isEmpty()) notes.removeFirst(); // header line ("Upgradable furnaces")
        guide.add("notes", WebJson.strings(notes));
        return guide;
    }

    private String ownerName(String language, UUID owner) {
        if (owner == null) return plugin.lang().plain(language, "furnace.unknown-owner");
        String name = Bukkit.getOfflinePlayer(owner).getName();
        return name == null || name.isBlank() ? owner.toString().substring(0, 8) : name;
    }

    static boolean isFurnace(Material material) {
        return material == Material.FURNACE || material == Material.BLAST_FURNACE
                || material == Material.SMOKER;
    }

    private String furnaceName(CommandSender viewer, Material material) {
        return plugin.lang().plain(viewer, furnaceNameKey(material));
    }

    /** Language key of a furnace block name. */
    static String furnaceNameKey(Material material) {
        return switch (material) {
            case BLAST_FURNACE -> "furnace.name.blast-furnace";
            case SMOKER -> "furnace.name.smoker";
            default -> "furnace.name.furnace";
        };
    }

    private static double bounded(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** One decimal place with the viewer's decimal separator. */
    private String decimal(CommandSender viewer, double value) {
        return plugin.number(viewer, "%.1f", value);
    }

}
