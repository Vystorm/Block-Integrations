package local.blockintegrations;


import Vystorm.vystorm_core.schedule.TaskGroup;
import Vystorm.vystorm_core.compat.dialog.DialogButton;
import Vystorm.vystorm_core.compat.dialog.DialogRequest;
import Vystorm.vystorm_core.compat.dialog.DialogBody;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;


final class AnvilManager implements Listener {
    static final String PERMISSION = "blockintegrations.anvils";
    static final int UPGRADE_SLOT = 18, USE_SLOT = 13;
    record Anvil(UUID identity, UUID owner, Map<String, Integer> levels) {
        Anvil { levels = Map.copyOf(levels); }
        int level(String upgrade) { return Math.clamp(levels.getOrDefault(upgrade, 0), 0, AnvilRules.maximum(upgrade)); }
        Anvil upgraded(String upgrade) { var next = new HashMap<>(levels); next.put(upgrade, level(upgrade) + 1); return new Anvil(identity, owner, next); }
    }
    record State(Map<String, Anvil> anvils) {}
    record Prepared(InventoryView view, BlockKey key, Anvil anvil, ItemStack left, ItemStack right, String rename, ItemStack result, int baseCost, int cost) {}
    private final BlockIntegrations plugin;
    private final TaskGroup tasks;
    private final StateFile<State> store;
    private final Map<BlockKey, Anvil> anvils = new HashMap<>();
    private final Map<UUID, Prepared> prepared = new HashMap<>();
    private record CompletedUse(InventoryClickEvent event,Inventory inventory,ItemStack before) {}
    private record ProtectedUse(Block block,org.bukkit.block.data.BlockData before,Anvil anvil,List<CompletedUse> uses) {}
    private final Map<BlockKey,ProtectedUse> protectedUses = new HashMap<>();
    private boolean enabled;
    private int baseCost, durabilityCost;

    AnvilManager(BlockIntegrations plugin) {
        this.plugin = plugin; tasks = plugin.core().tasks().of(plugin, "BlockIntegrations/Anvils");
        store = new StateFile<>(plugin.getDataFolder().toPath().resolve("anvils.json"), "BlockIntegrations/Anvils",
                State.class, 1, Map.of(), () -> new State(Map.of()), plugin.core().data().files());
    }
    /**
     * The other shared events go through Core's stages too. Opening the anvil menu and taking a managed result are the
     * feature acting on an allowed click (ACT); the durability upgrade and the stored anvils follow the final outcome
     * (OBSERVE). None of the handlers sees a cancelled event. The clicks in the plugin's own menu stay listeners.
     */
    void handleSharedEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, PlayerInteractEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.ACT, this::interact);
        stages.handle(plugin, InventoryClickEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.ACT, this::take);
        stages.handle(plugin, InventoryClickEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::protectUse);
        stages.handle(plugin, org.bukkit.event.entity.EntityChangeBlockEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::falling);
        stages.handle(plugin, BlockExplodeEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::explode);
        stages.handle(plugin, org.bukkit.event.entity.EntityExplodeEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::entityExplode);
    }
    void start() {
        reloadSettings();
        var state = store.load();
        if (state != null && state.anvils() != null) state.anvils().forEach((key, value) -> {
            try { if (value != null && value.owner() != null && value.identity() != null) anvils.put(BlockKey.parse(key), value); }
            catch (RuntimeException invalid) { plugin.getLogger().warning("Invalid anvil record ignored"); }
        });
        store.registerDiagnostics(plugin.core().admin().diagnostics(), plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        handleBlockEvents(plugin.core().priority().stages());
        handleSharedEvents(plugin.core().priority().stages());
        if (plugin.core().compat().server().hasClass("com.destroystokyo.paper.event.block.AnvilDamagedEvent")) {
            try {
                var listener=(Listener)Class.forName("local.blockintegrations.PaperAnvilDamage").getConstructor(AnvilManager.class).newInstance(this);
                plugin.getServer().getPluginManager().registerEvents(listener,plugin);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Paper anvil damage listener unavailable",failure); }
        }
    }
    void reloadSettings() {
        enabled = plugin.getConfig().getBoolean("anvils.enabled", true);
        baseCost = Math.clamp(plugin.getConfig().getInt("anvils.base-xp-cost", 100), 1, 100000);
        durabilityCost = Math.clamp(plugin.getConfig().getInt("anvils.unbreakable-xp-cost", 2500), 1, 1000000);
    }
    private boolean save() {
        Map<String, Anvil> data = new HashMap<>(); anvils.forEach((key, value) -> data.put(key.serialize(), value));
        return store.saveChecked(new State(data));
    }
    void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) if (plugin.core().compat().inventory().top(player.getOpenInventory()).getHolder() instanceof Menu) player.closeInventory();
        tasks.close(); prepared.clear(); protectedUses.clear(); save();
    }
    private Block loaded(BlockKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || key.y() < world.getMinHeight() || key.y() >= world.getMaxHeight() || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) return null;
        Block block = world.getBlockAt(key.x(), key.y(), key.z());
        return AnvilRules.supports(block.getType()) ? block : null;
    }
    private boolean near(Player player, BlockKey key) {
        return player.isOnline() && player.getWorld().getUID().equals(key.worldId())
                && player.getLocation().distanceSquared(new Location(player.getWorld(), key.x() + .5, key.y() + .5, key.z() + .5)) <= 64;
    }
    private boolean manage(Player player, BlockKey key, Anvil expected) {
        return enabled && expected != null && expected.equals(anvils.get(key)) && loaded(key) != null && near(player, key)
                && player.hasPermission(PERMISSION) && (player.getUniqueId().equals(expected.owner()) || player.hasPermission(BlockIntegrations.ADMIN_PERMISSION));
    }

    /* Stage ACT, registered in handleSharedEvents */
    public void interact(PlayerInteractEvent event) {
        if (!enabled || event.useInteractedBlock() == Event.Result.DENY || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || !AnvilRules.supports(event.getClickedBlock().getType())
                || !event.getPlayer().hasPermission(PERMISSION)) return;
        event.setCancelled(true);
        Player player = event.getPlayer(); BlockKey key = BlockKey.of(event.getClickedBlock());
        Menu menu = new Menu(key);
        menu.inventory = plugin.core().compat().platform().createInventory(menu, 27, plugin.lang().component(player, "anvil.title"));
        menu.inventory.setItem(USE_SLOT, icon(Material.ANVIL, player, "anvil.use"));
        menu.inventory.setItem(UPGRADE_SLOT, icon(Material.EXPERIENCE_BOTTLE, player, "anvil.upgrade"));
        player.openInventory(menu.inventory);
    }
    private ItemStack icon(Material material, Player player, String key) {
        ItemStack item = new ItemStack(material); var meta = item.getItemMeta(); plugin.core().compat().platform().displayName(meta, plugin.lang().component(player, key)); item.setItemMeta(meta); return item;
    }
    private static final class Menu implements InventoryHolder {
        final BlockKey key; Inventory inventory; Menu(BlockKey key) { this.key = key; }
        @Override public Inventory getInventory() { return inventory; }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void menuClick(InventoryClickEvent event) {
        if (!(plugin.core().compat().inventory().top(event.getView()).getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !enabled || !near(player, menu.key) || loaded(menu.key) == null || !player.hasPermission(PERMISSION)) return;
        int slot = event.getRawSlot();
        if (slot != USE_SLOT && slot != UPGRADE_SLOT) return;
        tasks.now(() -> {
            Block block = loaded(menu.key);
            if (block == null || !near(player, menu.key) || !enabled || !player.hasPermission(PERMISSION)) return;
            if (slot == USE_SLOT) { BukkitFeatures.openAnvil(player, block.getLocation(), plugin.lang().plain(player,"anvil.title")); return; }
            Anvil anvil = anvils.get(menu.key);
            if (anvil == null) {
                anvil = new Anvil(UUID.randomUUID(), player.getUniqueId(), Map.of()); anvils.put(menu.key, anvil);
                if (!save()) { anvils.remove(menu.key); plugin.lang().send(player, "anvil.unavailable"); return; }
            }
            if (!manage(player, menu.key, anvil)) { plugin.lang().send(player, "anvil.denied"); return; }
            upgrades(player, menu.key, anvil);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) { if (plugin.core().compat().inventory().top(event.getView()).getHolder() instanceof Menu) event.setCancelled(true); }

    private void upgrades(Player player, BlockKey key, Anvil anvil) {
        List<DialogBody> body = new ArrayList<>(); List<DialogButton> buttons = new ArrayList<>();
        body.add(DialogBody.plainMessage(plugin.lang().component(player, "anvil.description")));
        for (String upgrade : AnvilRules.UPGRADES) {
            int level = anvil.level(upgrade), cost = AnvilRules.cost(upgrade, level, baseCost, durabilityCost);
            String name = plugin.lang().plain(player, "anvil.path." + upgrade);
            body.add(DialogBody.plainMessage(plugin.lang().component(player, "workstation.level", "upgrade", name, "level", level, "max", AnvilRules.maximum(upgrade))));
            if (level < AnvilRules.maximum(upgrade)) buttons.add(new DialogButton(plugin.lang().component(player, "upgrade.button", "upgrade", name, "next", level + 1, "cost", cost), (p, r) -> {
                String result = purchase(p, key, anvil, upgrade, cost);
                plugin.lang().send(p, "anvil." + result);
                if (result.equals("upgraded")) upgrades(p, key, anvils.get(key));
            }));
        }
        plugin.core().dialogs().open(plugin, player, new DialogRequest(plugin.lang().component(player, "anvil.upgrade"), body,
                List.of(), buttons, p -> manage(p, key, anvil), () -> {}, BlockIntegrations.CLOSE_LABEL, 1));
    }
    String purchase(Player player, BlockKey key, Anvil expected, String upgrade, int cost) {
        if (!AnvilRules.UPGRADES.contains(upgrade) || !manage(player, key, expected)) return "denied";
        if (expected.level(upgrade) >= AnvilRules.maximum(upgrade) || cost != AnvilRules.cost(upgrade, expected.level(upgrade), baseCost, durabilityCost)) return "changed";
        boolean paid = player.getGameMode() != GameMode.CREATIVE;
        if (paid && !plugin.core().player().withdrawExperience(player, cost)) return "need-xp";
        anvils.put(key, expected.upgraded(upgrade));
        if (save()) return "upgraded";
        anvils.put(key, expected); save();
        if (paid) plugin.core().player().depositExperience(player, cost);
        return "unavailable";
    }

    private BlockKey key(InventoryView view) { Location location = plugin.core().compat().inventory().top(view).getLocation(); return location == null ? null : new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        InventoryView view = event.getView(); if (!(plugin.core().compat().inventory().top(view) instanceof AnvilInventory)) return;
        Anvil anvil = anvils.get(key(view));
        if (enabled && anvil != null && anvil.level("enchantments") > 0) { PlatformAccess.INSTANCE.unlock(view); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareAnvilEvent event) {
        InventoryView view = ((InventoryEvent) event).getView();
        if (!(PlatformAccess.player(view) instanceof Player player)) return;
        BlockKey key = key(view); Anvil anvil = key == null ? null : anvils.get(key);
        if (!enabled || anvil == null || loaded(key) == null || !near(player, key)) { prepared.remove(player.getUniqueId()); return; }
        ItemStack left = plugin.core().compat().inventory().top(view).getItem(0), right = plugin.core().compat().inventory().top(view).getItem(1), result = event.getResult();
        if (left == null || result == null || result.getType().isAir()) { prepared.remove(player.getUniqueId()); return; }
        Prepared previous = prepared.get(player.getUniqueId());
        if (previous != null && previous.view() == view && previous.anvil().equals(anvil)
                && Objects.equals(previous.left(), left) && Objects.equals(previous.right(), right)
                && Objects.equals(previous.rename(), PlatformAccess.INSTANCE.rename(view)) && Objects.equals(previous.result(), result)) {
            PlatformAccess.INSTANCE.cost(view, previous.cost()); return;
        }
        int base = previous != null && previous.view() == view && Objects.equals(previous.left(), left) && Objects.equals(previous.right(), right)
                && Objects.equals(previous.rename(), PlatformAccess.INSTANCE.rename(view)) ? previous.baseCost() : PlatformAccess.INSTANCE.cost(view);
        result = AnvilRules.enchant(left, right, result, anvil.level("enchantments"));
        result = AnvilItemsBridge.prepare(plugin.core().items(), left, right, result, PlatformAccess.INSTANCE.rename(view), player);
        if (result == null) { event.setResult(null); prepared.remove(player.getUniqueId()); return; }
        int cost = AnvilRules.repairCost(base, anvil.level("efficiency"));
        PlatformAccess.INSTANCE.cost(view, cost); event.setResult(result);
        prepared.put(player.getUniqueId(), new Prepared(view, key, anvil, left.clone(), right == null ? null : right.clone(), PlatformAccess.INSTANCE.rename(view), result.clone(), base, cost));
    }
    /* Stage ACT, registered in handleSharedEvents */
    public void take(InventoryClickEvent event) {
        InventoryView view = event.getView();
        if (!(plugin.core().compat().inventory().top(view) instanceof AnvilInventory) || event.getRawSlot() != 2 || !(event.getWhoClicked() instanceof Player player)) return;
        Prepared quote = prepared.get(player.getUniqueId());
        if (quote == null) return;
        if (!enabled || quote.view() != view || !quote.anvil().equals(anvils.get(quote.key())) || loaded(quote.key()) == null || !near(player, quote.key())
                || !Objects.equals(quote.left(), plugin.core().compat().inventory().top(view).getItem(0)) || !Objects.equals(quote.right(), plugin.core().compat().inventory().top(view).getItem(1))
                || !Objects.equals(quote.result(), event.getCurrentItem()) || PlatformAccess.INSTANCE.cost(view) != quote.cost()) { event.setCancelled(true); return; }
        if (!AnvilItemsBridge.managed(plugin.core().items(), quote.left())) return;
        boolean cursorEmpty = event.getCursor() == null || event.getCursor().getType().isAir();
        boolean allowed = event.getAction() == InventoryAction.PICKUP_ALL && cursorEmpty
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && player.getInventory().firstEmpty() >= 0;
        if (!allowed) { event.setCancelled(true); return; }
        tasks.now(() -> {
            if (event.isCancelled() || Objects.equals(quote.left(), plugin.core().compat().inventory().top(view).getItem(0))) return;
            if (!AnvilItemsBridge.commit(plugin.core().items(), quote.result(), player)) plugin.getLogger().warning("Managed anvil result could not be committed for " + player.getUniqueId());
        });
    }
    @EventHandler public void close(InventoryCloseEvent event) { prepared.remove(event.getPlayer().getUniqueId()); }
    boolean protectsDamage(InventoryView view) {
        if (!enabled || !(plugin.core().compat().inventory().top(view) instanceof AnvilInventory)) return false;
        Anvil anvil = anvils.get(key(view));
        return anvil != null && anvil.level("durability") > 0;
    }
    /** Bukkit has no anvil-damage event: restore only the vanilla use transition after a confirmed take. */
    /* Stage OBSERVE, registered in handleSharedEvents */
    public void protectUse(InventoryClickEvent event) {
        if (plugin.core().compat().server().hasClass("com.destroystokyo.paper.event.block.AnvilDamagedEvent")) return;
        protectBukkitUse(event);
    }
    void protectBukkitUse(InventoryClickEvent event) {
        if(event.getRawSlot()!=2 || event.getAction()==InventoryAction.NOTHING || !protectsDamage(event.getView()))return;
        Inventory inventory=plugin.core().compat().inventory().top(event.getView());ItemStack before=inventory.getItem(0);
        if(plugin.core().compat().platform().isEmpty(before))return;
        BlockKey key=key(event.getView());Block block=loaded(key);if(block==null)return;
        ProtectedUse pending=protectedUses.get(key);
        if(pending==null) {
            pending=new ProtectedUse(block,block.getBlockData().clone(),anvils.get(key),new ArrayList<>());
            protectedUses.put(key,pending);ProtectedUse expected=pending;
            tasks.now(()->finishProtectedUse(key,expected));
        }
        pending.uses().add(new CompletedUse(event,inventory,before.clone()));
    }
    private void finishProtectedUse(BlockKey key,ProtectedUse pending) {
        if(!protectedUses.remove(key,pending) || !pending.anvil().equals(anvils.get(key)))return;
        boolean taken=pending.uses().stream().anyMatch(use->!use.event().isCancelled() && !Objects.equals(use.before(),use.inventory().getItem(0)));
        Material current=pending.block().getType(),old=pending.before().getMaterial();
        if(taken && vanillaDamageTransition(old,current))pending.block().setBlockData(pending.before(),false);
    }
    static boolean vanillaDamageTransition(Material before,Material after) {
        return before==Material.ANVIL&&after==Material.CHIPPED_ANVIL
                || before==Material.CHIPPED_ANVIL&&after==Material.DAMAGED_ANVIL
                || before==Material.DAMAGED_ANVIL&&after==Material.AIR;
    }

    /**
     * Block break and place go through Core's stages, so Core decides when they run among all Vystorm plugins.
     * Both only keep the stored anvils in step with the world: OBSERVE, the final outcome, never a cancelled event.
     */
    void handleBlockEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, BlockPlaceEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::place);
        stages.handle(plugin, BlockBreakEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.OBSERVE, this::breakBlock);
    }

    /** Stage OBSERVE, registered in {@link #handleBlockEvents}. */
    public void place(BlockPlaceEvent event) {
        if (event instanceof BuildProbeEvent) return; // test placement, nothing was placed
        if (!AnvilRules.supports(event.getBlockPlaced().getType())) return;
        BlockKey key = BlockKey.of(event.getBlockPlaced());
        protectedUses.remove(key);
        var portable = plugin.portableBlocks().read(event.getItemInHand());
        Anvil value = portable != null && portable.kind().equals("anvil") && portable.owner() != null
                ? new Anvil(UUID.randomUUID(), portable.owner(), portable.levels()) : null;
        tasks.now(() -> { if (event.isCancelled() || !event.canBuild()) return; anvils.remove(key); if (value != null && loaded(key) != null) anvils.put(key, value); save(); });
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void drop(BlockDropItemEvent event) {
        Anvil anvil = anvils.get(BlockKey.of(event.getBlock()));
        if (anvil == null || !AnvilRules.supports(event.getBlockState().getType())) return;
        for (var entity : event.getItems()) {
            ItemStack stack = entity.getItemStack();
            if (AnvilRules.supports(stack.getType()) && stack.getAmount() == 1) {
                entity.setItemStack(plugin.portableBlocks().write(stack, event.getPlayer(), "anvil", stack.getType().name(), anvil.levels(), anvil.owner(), 0)); break;
            }
        }
    }
    /** Stage OBSERVE, registered in {@link #handleBlockEvents}. */
    public void breakBlock(BlockBreakEvent event) {
        BlockKey key = BlockKey.of(event.getBlock());
        protectedUses.remove(key);
        if (!anvils.containsKey(key)) return;
        tasks.now(() -> { if (!event.isCancelled() && loaded(key) == null) { anvils.remove(key); save(); } });
    }

    private NamespacedKey fallingKey() { return new NamespacedKey(plugin, "anvil_falling"); }
    /* Stage OBSERVE, registered in handleSharedEvents */
    public void falling(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.FallingBlock falling)) return;
        BlockKey key = BlockKey.of(event.getBlock());
        if (event.getTo().isAir()) {
            protectedUses.remove(key);
            Anvil value = anvils.get(key);
            if (value == null || !AnvilRules.supports(event.getBlock().getType())) return;
            var portable = new PortableBlockData.State(event.getBlock().getType(), "anvil", event.getBlock().getType().name(), value.levels(), value.owner(), 0);
            falling.getPersistentDataContainer().set(fallingKey(), org.bukkit.persistence.PersistentDataType.BYTE_ARRAY, PortableBlockData.encode(portable));
            tasks.now(() -> { if (!event.isCancelled()) { anvils.remove(key, value); save(); } });
        } else if (AnvilRules.supports(event.getTo())) {
            byte[] bytes = falling.getPersistentDataContainer().get(fallingKey(), org.bukkit.persistence.PersistentDataType.BYTE_ARRAY);
            if (bytes == null) return;
            try {
                var portable = PortableBlockData.decode(bytes);
                if (!portable.kind().equals("anvil")) return;
                tasks.now(() -> {
                    if (event.isCancelled() || loaded(key) == null) return;
                    anvils.put(key, new Anvil(UUID.randomUUID(), portable.owner(), portable.levels())); save();
                });
            } catch (java.io.IOException | IllegalArgumentException invalid) { plugin.getLogger().warning("Invalid falling anvil state"); }
        }
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fallingDrop(org.bukkit.event.entity.EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.FallingBlock)) return;
        byte[] bytes = event.getEntity().getPersistentDataContainer().get(fallingKey(), org.bukkit.persistence.PersistentDataType.BYTE_ARRAY);
        if (bytes == null || !AnvilRules.supports(event.getItemDrop().getItemStack().getType())) return;
        try {
            var portable = PortableBlockData.decode(bytes);
            if (!portable.kind().equals("anvil")) return;
            var stack = event.getItemDrop().getItemStack();
            event.getItemDrop().setItemStack(plugin.portableBlocks().write(stack, null, "anvil", stack.getType().name(), portable.levels(), portable.owner(), 0));
        } catch (java.io.IOException | IllegalArgumentException invalid) { plugin.getLogger().warning("Invalid falling anvil drop state"); }
    }
    private void exploded(List<Block> blocks) {
        blocks.forEach(block->protectedUses.remove(BlockKey.of(block)));
        var keys = blocks.stream().map(BlockKey::of).filter(anvils::containsKey).toList();
        if (!keys.isEmpty()) tasks.now(() -> { for (BlockKey key : keys) if (loaded(key) == null) anvils.remove(key); save(); });
    }
    /* Stage OBSERVE, registered in handleSharedEvents */
    public void explode(BlockExplodeEvent event) { exploded(event.blockList()); }
    /* Stage OBSERVE, registered in handleSharedEvents */
    public void entityExplode(org.bukkit.event.entity.EntityExplodeEvent event) { exploded(event.blockList()); }
}
