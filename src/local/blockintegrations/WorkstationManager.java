package local.blockintegrations;


import Vystorm.vystorm_core.compat.dialog.DialogButton;
import Vystorm.vystorm_core.compat.dialog.DialogRequest;

import Vystorm.vystorm_core.compat.dialog.DialogBody;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Event-driven upgrades for brewing stands and both campfires. No chunk loads or inventory rewrites. */
final class WorkstationManager implements Listener {
    static final int BREW_USE_SLOT = 13, BREW_UPGRADE_SLOT = 18;
    private final Vystorm.vystorm_core.schedule.TaskGroup tasks;
    private final BlockIntegrations plugin;
    private final NamespacedKey ownerKey, identityKey;
    private final Map<String, NamespacedKey> levels = new HashMap<>();
    private boolean brewing = true, campfires = true;
    private int maxLevel = 10, baseCost = 100;
    private final Map<String, Integer> potionCosts = new HashMap<>();

    WorkstationManager(BlockIntegrations plugin) {
        this.plugin = plugin;
        tasks = plugin.core().tasks().of(plugin, "BlockIntegrations/BrewingMenu");
        ownerKey = new NamespacedKey(plugin, "workstation_owner");
        identityKey = new NamespacedKey(plugin, "workstation_identity");
        for (String key : List.of("speed", "fuel", "yield", "extra", "strength", "duration")) levels.put(key, new NamespacedKey(plugin, "workstation_" + key));
    }

    /** The click on a workstation goes through Core's stages: the feature acts on an allowed click (ACT). */
    void handleSharedEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, PlayerInteractEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.ACT, this::interact);
    }
    void start() {
        reloadSettings();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        handleBlockEvents(plugin.core().priority().stages());
        handleSharedEvents(plugin.core().priority().stages());
    }

    void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers())
            if (plugin.core().compat().inventory().menuHolder(plugin.core().compat().inventory().top(player.getOpenInventory())) instanceof BrewingMenu) player.closeInventory();
        tasks.close();
    }

    private static final class BrewingMenu implements org.bukkit.inventory.InventoryHolder {
        final BlockKey key; final String identity;
        org.bukkit.inventory.Inventory inventory;
        BrewingMenu(BlockKey key, String identity) { this.key = key; this.identity = identity; }
        @Override public org.bukkit.inventory.Inventory getInventory() { return inventory; }
    }

    private void brewingMenu(Player player, TileState tile) {
        var menu = new BrewingMenu(BlockKey.of(tile.getBlock()), identity(tile));
        menu.inventory = plugin.core().compat().platform().createInventory(menu, 27, plugin.lang().component(player, "workstation.name.brewing_stand"));
        menu.inventory.setItem(BREW_USE_SLOT, menuIcon(player, Material.BREWING_STAND, "workstation.brew-use"));
        menu.inventory.setItem(BREW_UPGRADE_SLOT, menuIcon(player, Material.EXPERIENCE_BOTTLE, "workstation.brew-upgrade"));
        player.openInventory(menu.inventory);
    }

    private ItemStack menuIcon(Player player, Material material, String label) {
        var item = new ItemStack(material); var meta = item.getItemMeta();
        plugin.core().compat().platform().displayName(meta, plugin.lang().component(player, label)); item.setItemMeta(meta); return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void menuClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(plugin.core().compat().inventory().menuHolder(plugin.core().compat().inventory().top(event.getView())) instanceof BrewingMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot != BREW_USE_SLOT && slot != BREW_UPGRADE_SLOT) return;
        tasks.now(() -> {
            if (plugin.core().compat().inventory().menuHolder(plugin.core().compat().inventory().top(player.getOpenInventory())) == menu)
                selectBrewing(player, menu.key, menu.identity, slot);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void menuDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (plugin.core().compat().inventory().menuHolder(plugin.core().compat().inventory().top(event.getView())) instanceof BrewingMenu) event.setCancelled(true);
    }

    void selectBrewing(Player player, BlockKey key, String expectedIdentity, int slot) {
        if (!near(player, key) || !player.hasPermission("blockintegrations.workstations")) return;
        TileState tile = loaded(key);
        if (!(tile instanceof org.bukkit.block.BrewingStand stand) || !enabled(tile.getType())
                || !Objects.equals(expectedIdentity, identity(tile))) return;
        if (slot == BREW_USE_SLOT) { player.openInventory(stand.getInventory()); return; }
        if (slot != BREW_UPGRADE_SLOT) return;
        if (!player.hasPermission("blockintegrations.workstations.upgrade")) {
            plugin.lang().send(player, "workstation.denied"); return;
        }
        manageMenu(player, tile);
    }

    void reloadSettings() {
        for (String path : List.of("extra", "strength", "duration"))
            potionCosts.put(path, Math.clamp(plugin.getConfig().getInt("workstations." + path + "-base-xp-cost",
                    path.equals("strength") ? 10000 : path.equals("extra") ? 5000 : 1000), 1, 100000));
        brewing = BlockIntegrations.enabled(plugin, Feature.BREWING);
        campfires = BlockIntegrations.enabled(plugin, Feature.CAMPFIRES);
        maxLevel = Math.clamp(plugin.getConfig().getInt("workstations.maximum-level", 10), 1, 10);
        baseCost = Math.clamp(plugin.getConfig().getInt("workstations.base-xp-cost", 100), 1, 100_000);
    }

    boolean enabled(Material type) {
        return type == Material.BREWING_STAND ? brewing
                : (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE) && campfires;
    }

    private TileState state(Block block) {
        if (block == null || !WorkstationStats.supports(block.getType())
                || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return null;
        return block.getState() instanceof TileState tile ? tile : null;
    }

    private TileState loaded(BlockKey key) {
        World world = Bukkit.getWorld(key.worldId());
        return world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ()) ? null
                : state(world.getBlockAt(key.x(), key.y(), key.z()));
    }

    UUID owner(TileState tile) {
        String value = tile.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private String identity(TileState tile) {
        return tile.getPersistentDataContainer().get(identityKey, PersistentDataType.STRING);
    }

    int level(TileState tile, String upgrade) {
        Integer value = tile.getPersistentDataContainer().get(levels.get(upgrade), PersistentDataType.INTEGER);
        return Math.min(maximum(upgrade), WorkstationStats.level(value == null ? 0 : value));
    }

    private int maximum(String upgrade) { return Math.min(maxLevel, WorkstationStats.maximum(upgrade)); }
    private int price(String upgrade, int current) { return WorkstationStats.cost(current, potionCosts.getOrDefault(upgrade, baseCost)); }

    private int activeLevel(Block block, String upgrade) {
        if (!enabled(block.getType())) return 0;
        TileState tile = state(block);
        return tile == null || owner(tile) == null ? 0 : level(tile, upgrade);
    }

    private void initialize(TileState tile, UUID owner, Map<String, Integer> restored) {
        var data = tile.getPersistentDataContainer();
        data.set(ownerKey, PersistentDataType.STRING, owner.toString());
        data.set(identityKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        for (String upgrade : WorkstationStats.upgrades(tile.getType()))
            data.set(levels.get(upgrade), PersistentDataType.INTEGER, Math.min(WorkstationStats.maximum(upgrade), WorkstationStats.level(restored.getOrDefault(upgrade, 0))));
    }

    /**
     * Block break and place go through Core's stages, so Core decides when they run among all Vystorm plugins.
     * A placed workstation gets its owner once the placement is final: OBSERVE, never a cancelled event.
     */
    void handleBlockEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, BlockPlaceEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::place);
    }

    /** Stage OBSERVE, registered in {@link #handleBlockEvents}. */
    public void place(BlockPlaceEvent event) {
        if (event instanceof BuildProbeEvent) return; // test placement, nothing was placed
        if (event.isCancelled() || !event.canBuild()) return;
        TileState tile = state(event.getBlockPlaced());
        if (tile == null) return;
        PortableBlockData.State saved = plugin.portableBlocks().read(event.getItemInHand());
        if (saved != null && saved.kind().equals("workstation") && saved.material() == tile.getType()
                && saved.owner() != null) {
            initialize(tile, saved.owner(), saved.levels());
        } else {
            if (!enabled(tile.getType()) || !event.getPlayer().hasPermission("blockintegrations.workstations")) return;
            initialize(tile, event.getPlayer().getUniqueId(), Map.of());
        }
        tile.update(false, false);
    }

    /* Stage ACT, registered in handleSharedEvents */
    public void interact(PlayerInteractEvent event) {
        if (event.isCancelled() || event.useInteractedBlock() == Event.Result.DENY
                || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || !enabled(event.getClickedBlock().getType())) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() && event.getClickedBlock().getType() != Material.BREWING_STAND) return;
        if (!player.hasPermission("blockintegrations.workstations")) return;
        TileState tile = state(event.getClickedBlock());
        if (tile == null) return;
        event.setCancelled(true);
        if (tile.getType() == Material.BREWING_STAND && !player.isSneaking()) {
            brewingMenu(player, tile); return;
        }
        manageMenu(player, tile);
    }

    private void manageMenu(Player player, TileState tile) {
        if (owner(tile) == null) {
            initialize(tile, player.getUniqueId(), Map.of());
            if (!tile.update(false, false)) { plugin.lang().send(player, "workstation.unavailable"); return; }
        }
        if (!mayManage(player, tile)) { plugin.lang().send(player, "workstation.not-owner"); return; }
        open(player, tile);
    }

    private boolean mayManage(Player player, TileState tile) {
        return player.hasPermission("blockintegrations.workstations") && (player.getUniqueId().equals(owner(tile))
                || player.hasPermission("blockintegrations.workstations.manage-others"));
    }

    private boolean near(Player player, BlockKey key) {
        if (!player.isOnline() || !player.getWorld().getUID().equals(key.worldId())) return false;
        var at = player.getLocation();
        return Math.pow(at.getX() - key.x() - .5, 2) + Math.pow(at.getY() - key.y() - .5, 2)
                + Math.pow(at.getZ() - key.z() - .5, 2) <= 64;
    }

    private void open(Player player, TileState tile) {
        var lang = plugin.lang();
        BlockKey key = BlockKey.of(tile.getBlock());
        String id = identity(tile);
        List<DialogBody> body = new ArrayList<>();
        List<DialogButton> buttons = new ArrayList<>();
        body.add(DialogBody.plainMessage(lang.component(player, "workstation.description")));
        if (tile.getType() == Material.BREWING_STAND) body.add(DialogBody.plainMessage(lang.component(player, "workstation.potion-description")));
        for (String upgrade : WorkstationStats.upgrades(tile.getType())) {
            int shown = level(tile, upgrade), cost = price(upgrade, shown);
            String label = lang.plain(player, "workstation.upgrade." + upgrade);
            body.add(DialogBody.plainMessage(lang.component(player, "workstation.level", "upgrade", label,
                    "level", shown, "max", maximum(upgrade))));
            if (shown < maximum(upgrade)) buttons.add(new DialogButton(lang.component(player, "upgrade.button",
                    "upgrade", label, "next", shown + 1, "cost", cost), (p, response) -> {
                String outcome = purchase(p, key, id, upgrade, shown, cost);
                lang.send(p, "workstation." + outcome);
                TileState current = loaded(key);
                if (current != null && near(p, key) && mayManage(p, current) && enabled(current.getType())) open(p, current);
            }));
        }
        buttons.add(PortableMenuButtons.back(plugin.core(), plugin::openBlockGuideMenu));
        plugin.core().dialogs().open(plugin, player, new DialogRequest(lang.component(player, nameKey(tile.getType())),
                body, List.of(), buttons, p -> {
                    TileState current = loaded(key);
                    return current != null && enabled(current.getType()) && near(p, key)
                            && mayManage(p, current) && Objects.equals(id, identity(current));
                }, () -> {}, BlockIntegrations.CLOSE_LABEL, 2));
    }

    String purchase(Player player, BlockKey key, String expectedId, String upgrade, int expectedLevel, int expectedCost) {
        if (!player.hasPermission("blockintegrations.workstations.upgrade")) return "denied";
        TileState tile = loaded(key);
        if (tile == null || !enabled(tile.getType()) || !near(player, key)) return "unavailable";
        if (!mayManage(player, tile)) return "not-owner";
        if (expectedId == null || !expectedId.equals(identity(tile)) || !WorkstationStats.upgrades(tile.getType()).contains(upgrade)) return "changed";
        int current = level(tile, upgrade), cost = price(upgrade, current);
        if (current >= maximum(upgrade) || current != expectedLevel || cost != expectedCost) return "changed";
        boolean paid = player.getGameMode() != GameMode.CREATIVE;
        if (paid && !plugin.core().player().withdrawExperience(player, cost)) return "need-xp";
        try {
            tile.getPersistentDataContainer().set(levels.get(upgrade), PersistentDataType.INTEGER, current + 1);
            if (tile.update(false, false)) return "upgraded";
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Workstation upgrade write failed: " + failure.getClass().getSimpleName());
        }
        if (paid) plugin.core().player().depositExperience(player, cost);
        return "unavailable";
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void drop(BlockDropItemEvent event) {
        if (event.isCancelled() || !(event.getBlockState() instanceof TileState tile)
                || !WorkstationStats.supports(tile.getType()) || owner(tile) == null) return;
        Map<String, Integer> saved = new HashMap<>();
        for (String upgrade : WorkstationStats.upgrades(tile.getType())) {
            Integer stored = tile.getPersistentDataContainer().get(levels.get(upgrade), PersistentDataType.INTEGER);
            saved.put(upgrade, WorkstationStats.level(stored == null ? 0 : stored));
        }
        for (var entity : event.getItems()) {
            ItemStack item = entity.getItemStack();
            if (item.getType() == tile.getType() && item.getAmount() == 1) {
                entity.setItemStack(plugin.portableBlocks().write(item, event.getPlayer(), "workstation",
                        tile.getType().name(), saved, owner(tile), 0));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void brew(BrewingStartEvent event) {
        int level = activeLevel(event.getBlock(), "speed");
        if (level > 0) PlatformAccess.brewing().brew(event, level);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fuel(BrewingStandFuelEvent event) {
        if (event.isCancelled()) return;
        int level = activeLevel(event.getBlock(), "fuel");
        if (level > 0) event.setFuelPower(WorkstationStats.fuel(event.getFuelPower(), level));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void brewFinished(org.bukkit.event.inventory.BrewEvent event) {
        if (event.isCancelled()) return;
        int extra = activeLevel(event.getBlock(), "extra"), strength = activeLevel(event.getBlock(), "strength"),
                duration = activeLevel(event.getBlock(), "duration");
        if (extra + strength + duration == 0) return;
        var results = event.getResults();
        for (int slot = 0; slot < Math.min(3, results.size()); slot++) {
            ItemStack result = results.get(slot), input = event.getContents().getItem(slot);
            if (result == null || result.getType().isAir() || Objects.equals(input, result)
                    || plugin.core().items().managed(result)
                    || input != null && plugin.core().items().managed(input)) continue;
            results.set(slot, BrewingBonuses.apply(input, result, extra, strength, duration, ThreadLocalRandom.current()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void cookStart(CampfireStartEvent event) {
        int level = activeLevel(event.getBlock(), "speed");
        if (level > 0) event.setTotalCookTime(WorkstationStats.ticks(event.getTotalCookTime(), level));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void cooked(BlockCookEvent event) {
        if (event.isCancelled() || (event.getBlock().getType() != Material.CAMPFIRE
                && event.getBlock().getType() != Material.SOUL_CAMPFIRE)) return;
        int level = activeLevel(event.getBlock(), "yield");
        ItemStack result = event.getResult();
        if (level <= 0 || result.getAmount() <= 0 || result.getAmount() >= result.getMaxStackSize()
                || ThreadLocalRandom.current().nextDouble() >= WorkstationStats.yieldChance(level)) return;
        ItemStack bonus = result.clone();
        bonus.setAmount(result.getAmount() + 1);
        event.setResult(bonus);
    }

    static String nameKey(Material type) { return "workstation.name." + type.name().toLowerCase(Locale.ROOT); }
}
