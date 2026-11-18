package local.blockintegrations;

import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.schedule.TaskGroup;
import Vystorm.vystorm_core.web.ClientServices;
import Vystorm.vystorm_core.web.ClientUi;
import Vystorm.vystorm_core.web.VystormClientReadyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Client-mod features of BlockIntegrations (Core capability {@code web-client-ui}): markers over the player's own towers,
 * a toast when a tower's loot chest is full, and tooltip lines for portable blocks. Only constructed when Core has the
 * capability; server thread only. Without the mod nothing changes (chat messages and item lore stay).
 */
final class TowerClient implements Listener {
    /** Own towers marked at most (nearest first; client limit 64 markers shared with other plugins). */
    static final int MAX_MARKERS = 16;

    private final BlockIntegrations plugin;
    private final ClientUi ui;
    private final TaskGroup tasks;
    /** Marker id → label last sent, per player. */
    private final Map<UUID, Map<String, String>> sent = new HashMap<>();
    /** Owners whose markers refresh within the next second (tower kills change the XP label often: coalesced). */
    private final java.util.Set<UUID> dirty = new HashSet<>();

    TowerClient(BlockIntegrations plugin) {
        this.plugin = plugin;
        this.ui = plugin.core().web().clientUi().of(plugin);
        this.tasks = plugin.core().tasks().of(plugin, "BlockIntegrations/TowerClient");
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tasks.timer(100L, 100L, this::syncAll);
    }

    void shutdown() {
        tasks.close();
        HandlerList.unregisterAll(this);
        for (UUID id : sent.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) ui.clear(player, "tower/");
        }
        sent.clear();
        dirty.clear();
    }

    private boolean markersEnabled() {
        return plugin.getConfig().getBoolean("client.tower-markers", true) && BlockIntegrations.enabled(plugin, Feature.TOWER_MARKERS);
    }

    /** A tower of {@code owner} was built, moved, upgraded or removed: refresh that player's markers next tick. */
    void changed(UUID owner) {
        if (owner == null) return;
        Player player = Bukkit.getPlayer(owner);
        if (player == null || !sent.containsKey(owner) || !dirty.add(owner)) return;
        tasks.later(20L, () -> {
            dirty.remove(owner);
            if (player.isOnline()) sync(player);
        });
    }

    private void syncAll() {
        for (Player player : Bukkit.getOnlinePlayers()) sync(player);
    }

    void sync(Player player) {
        TowerManager towers = plugin.towerManager();
        Map<String, String> before = sent.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        Map<String, Object[]> desired = new LinkedHashMap<>();
        if (towers != null && markersEnabled() && ui.available(player, ClientServices.CAP_MARKERS)
                && player.hasPermission("blockintegrations.towers")) {
            Lang lang = plugin.lang();
            String language = lang.language(player);
            Location at = player.getLocation();
            World world = player.getWorld();
            List<Tower> own = new ArrayList<>(towers.towersOwnedBy(player.getUniqueId()));
            own.removeIf(tower -> !tower.key.worldId().equals(world.getUID()));
            own.sort(Comparator.comparingDouble(tower -> distanceSquared(tower.key, at)));
            for (Tower tower : own.subList(0, Math.min(MAX_MARKERS, own.size()))) {
                String label = TowerClientView.label(lang, language, lang.plain(language, tower.type.nameKey()), tower.storedExperience());
                desired.put(TowerClientView.markerId(tower.key), new Object[]{tower, label});
            }
        }
        for (var entry : desired.entrySet()) {
            Tower tower = (Tower) entry.getValue()[0];
            String label = (String) entry.getValue()[1];
            String shown = before.get(entry.getKey());
            if (label.equals(shown)) continue;
            boolean done = shown != null && ui.patch(player, TowerClientView.labelPatch(tower.key, tower.type, label));
            if (!done) done = ui.put(player, TowerClientView.marker(tower.key, player.getWorld().getKey().toString(), tower.type, label));
            if (done) before.put(entry.getKey(), label);
            else before.remove(entry.getKey());
        }
        for (String id : new HashSet<>(before.keySet())) {
            if (desired.containsKey(id)) continue;
            ui.remove(player, id);
            before.remove(id);
        }
    }

    private static double distanceSquared(BlockKey key, Location at) {
        double dx = key.x() - at.getX(), dy = key.y() - at.getY(), dz = key.z() - at.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Toast for the owner when a tower's loot chest overflows (the chat message with its buttons is sent anyway). */
    /**
     * Toast for the owner; with client 0.9.0 it carries an "Open loot" action ({@code openLoot} re-checks tower, owner
     * and distance like the chat button). Hidden UI drops toasts – the chat message is sent anyway.
     */
    void lootFull(Player owner, TowerType type, java.util.function.Consumer<Player> openLoot) {
        if (!ui.visible(owner) || !ui.available(owner, ClientServices.CAP_TOASTS)) return;
        Lang lang = plugin.lang();
        String language = lang.language(owner);
        String name = lang.plain(language, type.nameKey());
        if (!ui.toast(owner, TowerClientView.lootFullWithAction(lang, language, name), openLoot))
            ui.toast(owner, TowerClientView.lootFull(lang, language, name));
    }

    private void sendTooltips(Player player) {
        if (!plugin.getConfig().getBoolean("client.portable-tooltips", true) || !BlockIntegrations.enabled(plugin, Feature.PORTABLE_TOOLTIPS)) return;
        Lang lang = plugin.lang();
        String language = lang.language(player);
        boolean levels = ui.available(player, ClientServices.CAP_UI_EXTENSIONS);
        for (TowerType type : TowerType.values())
            putTooltip(player, levels && type.upgradeable, lang, language, "tower", type.configKey, lang.plain(language, type.nameKey()));
        for (ComboType type : ComboType.values())
            putTooltip(player, levels, lang, language, "combo", type.configKey, lang.plain(language, type.nameKey()));
        for (Material furnace : List.of(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER))
            putTooltip(player, levels, lang, language, "furnace", furnace.name(), lang.plain(language, FurnaceManager.furnaceNameKey(furnace)));
    }

    /** Rule with level lines for client 0.9.0+, else (or if refused) the plain two-line rule. */
    private void putTooltip(Player player, boolean levels, Lang lang, String language, String kind, String type, String name) {
        if (levels && ui.put(player, TowerClientView.tooltipWithLevels(lang, language, kind, type, name))) return;
        ui.put(player, TowerClientView.tooltip(lang, language, kind, type, name));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReady(VystormClientReadyEvent event) {
        Player player = event.getPlayer();
        sent.remove(player.getUniqueId());
        if (event.has(ClientServices.CAP_TOOLTIPS)) sendTooltips(player);
        sync(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (sent.containsKey(event.getPlayer().getUniqueId())) sync(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sent.remove(event.getPlayer().getUniqueId());
    }
}
