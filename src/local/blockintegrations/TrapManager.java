package local.blockintegrations;

import Vystorm.vystorm_core.schedule.TaskGroup;
import Vystorm.vystorm_core.region.RegionService;
import Vystorm.vystorm_core.compat.dialog.DialogButton;
import Vystorm.vystorm_core.compat.dialog.DialogRequest;
import Vystorm.vystorm_core.compat.dialog.DialogBody;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** One bounded task over registered traps, never a world scan. */
final class TrapManager {
    static final String PERMISSION = "blockintegrations.traps";
    record Entry(String key, UUID owner, BlockFace facing, TrapStructure.Type type, boolean automatic, int cooldown, long readyAt) {
        Entry mode(boolean automatic, int cooldown) { return new Entry(key, owner, facing, type, automatic, cooldown, readyAt); }
        Entry fired(long now) { return new Entry(key, owner, facing, type, automatic, cooldown, now + cooldown * 1000L); }
    }
    record State(List<Entry> traps) {}
    private final BlockIntegrations plugin;
    private final TaskGroup tasks;
    private final StateFile<State> store;
    private final Map<BlockKey, Entry> traps = new LinkedHashMap<>();
    private boolean enabled, saveQueued;
    private boolean defaultAutomatic;
    private int defaultCooldown;
    private double power, damage;
    private double slingPower, slingLift;

    TrapManager(BlockIntegrations plugin) {
        this.plugin = plugin;
        tasks = plugin.core().tasks().of(plugin, "BlockIntegrations/Traps");
        store = new StateFile<>(plugin.getDataFolder().toPath().resolve("traps.json"), "BlockIntegrations/Traps",
                State.class, 1, Map.of(), () -> new State(List.of()), plugin.core().data().files());
    }

    void start() {
        reloadSettings();
        State state = store.load();
        if (state != null && state.traps() != null) for (Entry entry : state.traps()) {
            try {
                if (entry.owner() == null || entry.type() == null || !TrapStructure.FACES.contains(entry.facing())) continue;
                traps.put(BlockKey.parse(entry.key()), entry.mode(entry.automatic(), entry.cooldown() == 10 ? 10 : 15));
            } catch (RuntimeException invalid) { plugin.getLogger().warning("Invalid trap record ignored"); }
        }
        store.registerDiagnostics(plugin.core().admin().diagnostics(), plugin);
        handleSharedEvents(plugin.core().priority().stages());
        tasks.timer(10, 10, this::tick);
    }

    void reloadSettings() {
        enabled = BlockIntegrations.enabled(plugin, Feature.TRAPS);
        defaultAutomatic = plugin.getConfig().getBoolean("traps.default-automatic", true);
        defaultCooldown = plugin.getConfig().getInt("traps.default-cooldown-seconds", 15) == 10 ? 10 : 15;
        power = bounded(plugin.getConfig().getDouble("traps.throw-power", 2.0), .5, 3.0, 2);
        damage = bounded(plugin.getConfig().getDouble("traps.spike-damage", 6.0), 0, 40, 6);
        slingPower = bounded(plugin.getConfig().getDouble("traps.sling-power", 2.0), .5, 3.0, 2);
        slingLift = bounded(plugin.getConfig().getDouble("traps.sling-lift", 1.1), .2, 2.0, 1.1);
    }
    private static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
    void shutdown() { tasks.close(); store.save(new State(List.copyOf(traps.values()))); }
    private void save() {
        if (saveQueued) return;
        saveQueued = true;
        tasks.now(() -> { saveQueued = false; store.save(new State(List.copyOf(traps.values()))); });
    }

    /**
     * The trap's events go through Core's stages: the click on the button is the feature acting on an allowed click
     * (ACT); a trap piston never extends as a piston (a rule of this plugin: ADJUST). Neither sees a cancelled event.
     */
    void handleSharedEvents(Vystorm.vystorm_core._coreMethods.eventRegulator.EventStages stages) {
        stages.handle(plugin, PlayerInteractEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.ACT, this::interact);
        stages.handle(plugin, BlockPistonExtendEvent.class, Vystorm.vystorm_core._coreMethods.eventRegulator.EventStage.ADJUST, this::piston);
    }
    /* Stage ACT, registered in handleSharedEvents */
    public void interact(PlayerInteractEvent event) {
        if (!enabled || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || !event.getPlayer().hasPermission(PERMISSION)) return;
        Block clicked = event.getClickedBlock();
        TrapStructure.Shape shape = TrapStructure.isButton(clicked) ? TrapStructure.fromButton(clicked) : null;
        if (shape == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        BlockKey key = BlockKey.of(shape.core());
        Entry entry = traps.get(key);
        if (entry == null) {
            if (store.readOnly() || traps.size() >= 2048 || traps.values().stream().filter(t -> t.owner().equals(player.getUniqueId())).count() >= 32) {
                plugin.lang().send(player, "trap.limit"); return;
            }
            if (!allowedRegion(plugin.core().area().regions(), shape.core().getLocation(), shape.core().getLocation())) { plugin.lang().send(player, "trap.denied"); return; }
            entry = new Entry(key.serialize(), player.getUniqueId(), shape.facing(), shape.type(), defaultAutomatic, defaultCooldown, 0);
            traps.put(key, entry); save();
            plugin.lang().send(player, "trap.registered", "seconds", defaultCooldown,
                    "mode", plugin.lang().plain(player, defaultAutomatic ? "trap.auto" : "trap.manual"));
        }
        if (!owner(player, entry) || entry.type() != shape.type() || entry.facing() != shape.facing()) {
            plugin.lang().send(player, "trap.denied"); return;
        }
        if (player.isSneaking()) menu(player, key);
        else {
            long now = System.currentTimeMillis();
            if (!ready(entry, now)) plugin.lang().send(player, "trap.cooldown", "seconds", Math.max(1, (entry.readyAt() - now + 999) / 1000));
            else if (!fire(key, entry, shape, now, true)) plugin.lang().send(player, "trap.no-target");
        }
    }

    private boolean owner(Player player, Entry entry) {
        return player.hasPermission(PERMISSION) && (entry.owner().equals(player.getUniqueId()) || player.hasPermission(BlockIntegrations.ADMIN_PERMISSION));
    }

    private void menu(Player player, BlockKey key) {
        Entry entry = traps.get(key);
        if (entry == null || !canManage(player, key)) return;
        var lang = plugin.lang();
        var body = List.<DialogBody>of(DialogBody.plainMessage(lang.component(player, "trap.status", "type",
                lang.plain(player, "trap.type." + entry.type().name().toLowerCase(Locale.ROOT)),
                "mode", lang.plain(player, entry.automatic() ? "trap.auto" : "trap.manual"), "seconds", entry.cooldown())));
        var buttons = List.of(new DialogButton(lang.component(player, "trap.toggle"), (p, r) -> {
            if (!canManage(p, key)) return;
            Entry current = traps.get(key);
            traps.put(key, current.mode(!current.automatic(), current.cooldown())); save(); menu(p, key);
        }), new DialogButton(lang.component(player, "trap.change-cooldown"), (p, r) -> {
            if (!canManage(p, key)) return;
            Entry current = traps.get(key);
            traps.put(key, current.mode(current.automatic(), current.cooldown() == 10 ? 15 : 10)); save(); menu(p, key);
        }), new DialogButton(lang.component(player, "trap.remove"), (p, r) -> {
            if (!canManage(p, key)) return;
            if (!ready(traps.get(key), System.currentTimeMillis())) { plugin.lang().send(p, "trap.wait-remove"); return; }
            traps.remove(key); save();
        }));
        plugin.core().dialogs().open(plugin, player, new DialogRequest(lang.component(player, "trap.title"), body,
                List.of(), buttons, p -> canManage(p, key), () -> {}, BlockIntegrations.CLOSE_LABEL, 1));
    }

    private boolean canManage(Player player, BlockKey key) {
        Entry entry = traps.get(key);
        Block core = loaded(key);
        return enabled && entry != null && owner(player, entry) && player.isOnline() && core != null
                && core.getWorld().equals(player.getWorld()) && core.getLocation().distanceSquared(player.getLocation()) <= 64
                && TrapStructure.detect(core, entry.facing()) != null && allowedRegion(plugin.core().area().regions(), core.getLocation(), core.getLocation());
    }

    void tick() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        for (var pair : List.copyOf(traps.entrySet())) {
            Entry entry = pair.getValue();
            if (!ready(entry, now)) continue;
            Block core = loaded(pair.getKey());
            if (core == null) continue;
            if (TrapStructure.type(core.getType()) != entry.type()) { traps.remove(pair.getKey()); save(); continue; }
            if (!entry.automatic()) continue;
            var shape = TrapStructure.detect(core, entry.facing());
            if (shape == null || shape.type() != entry.type()) continue;
            fire(pair.getKey(), entry, shape, now, false);
        }
    }

    static boolean ready(Entry entry, long now) { return now >= entry.readyAt(); }

    private boolean fire(BlockKey key, Entry entry, TrapStructure.Shape shape, long now, boolean manual) {
        if (!ready(entry, now)) return false;
        Location center = shape.core().getLocation().add(.5, .5, .5);
        if (!allowedRegion(plugin.core().area().regions(), center, center)) return false;
        boolean fired = false;
        for (Entity entity : center.getWorld().getNearbyEntities(center, 4, 4, 4)) {
            if (!(entity instanceof LivingEntity mob) || !hostile(mob)) continue;
            Vector point = mob.getBoundingBox().getCenter();
            if (!TrapStructure.inFront(point.clone().subtract(center.toVector()), shape.facing(), 3)
                    || !allowedRegion(plugin.core().area().regions(), center, mob.getLocation()) || !visible(center, shape.facing(), point)) continue;
            var event = new TrapTriggerEvent(shape.core(), mob, entry.owner());
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled() || !mob.isValid() || mob.isDead()) continue;
            if (!fired) { traps.put(key, entry.fired(now)); save(); fired = true; }
            switch (entry.type()) {
                case THROW -> mob.setVelocity(TrapStructure.launch(shape.facing(), power));
                case SLING -> mob.setVelocity(TrapStructure.sling(shape.facing(),
                        shape.button().getLocation().toVector().subtract(shape.core().getLocation().toVector()), slingPower, slingLift));
                case SPIKES -> {
                    // Only a hand-fired trap counts as the owner's kill; automatic traps must not run AFK loot farms.
                    Player owner = manual ? Bukkit.getPlayer(entry.owner()) : null;
                    if (owner == null) mob.damage(damage); else mob.damage(damage, owner);
                }
                case TAR -> mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
            }
        }
        if (fired) {
            center.getWorld().playSound(center, "minecraft:block.piston.extend", 1, .8f);
            Particle particle = switch (entry.type()) {
                case THROW, SLING -> Particle.POOF;
                case SPIKES -> Particle.CRIT;
                case TAR -> Particle.FALLING_HONEY;
            };
            center.getWorld().spawnParticle(particle, center.clone().add(shape.facing().getDirection().multiply(.7)), 12, .6, .3, .6, .03);
        }
        return fired;
    }

    static boolean hostile(LivingEntity mob) {
        if (!(mob instanceof Enemy) || !mob.isValid() || mob.isDead() || mob.isInvulnerable()) return false;
        if (mob instanceof Tameable pet && pet.isTamed()) return false;
        // Named/custom enemies remain usable, except explicitly protected entities.
        if (mob.getScoreboardTags().contains("no-traps") || mob.getScoreboardTags().contains("npc")) return false;
        if (mob instanceof Enderman enderman && !BukkitFeatures.screaming(enderman) && enderman.getTarget() == null) return false;
        if (mob instanceof PigZombie pig && !pig.isAngry()) return false;
        return !(mob instanceof Piglin piglin) || piglin.getTarget() != null;
    }

    static boolean allowedRegion(RegionService service, Location origin, Location target) {
        for (Location location : List.of(origin, target)) for (var region : service.at(location))
            if (Set.of("arena", "arena-zone", "spawn", "safe", "market").contains(region.kind())) return false;
        return service.top(origin, "city").map(r -> r.id()).equals(service.top(target, "city").map(r -> r.id()));
    }

    private static boolean visible(Location center, BlockFace face, Vector target) {
        Location source = center.clone().add(face.getDirection().multiply(.55));
        Vector direction = target.clone().subtract(source.toVector());
        double length = direction.length();
        return length < .01 || source.getWorld().rayTraceBlocks(source, direction.normalize(), length, FluidCollisionMode.NEVER, true) == null;
    }

    private static Block loaded(BlockKey key) {
        World world = Bukkit.getWorld(key.worldId());
        return world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ()) || key.y() < world.getMinHeight()
                || key.y() >= world.getMaxHeight() ? null : world.getBlockAt(key.x(), key.y(), key.z());
    }

    // The trap actuator stays a block; redstone must not move terrain or reset its cooldown.
    /* Stage ADJUST, registered in handleSharedEvents */
    public void piston(BlockPistonExtendEvent event) {
        if (traps.containsKey(BlockKey.of(event.getBlock()))) event.setCancelled(true);
    }
}
