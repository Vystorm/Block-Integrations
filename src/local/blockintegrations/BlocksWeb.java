package local.blockintegrations;

import Vystorm.vystorm_core._parsersDebuggerEtc.debugger.DebugMessages;
import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.schedule.TaskGroup;
import Vystorm.vystorm_core.web.WebException;
import Vystorm.vystorm_core.web.WebModule;
import Vystorm.vystorm_core.web.WebRequest;
import Vystorm.vystorm_core.web.WebResponse;
import Vystorm.vystorm_core.web.WebRoute;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Web module {@value #MODULE} (Vystorm Core 0.24.0+): "My structures" (towers, furnaces, block combos with state and
 * live refresh), upgrades, tower loot, building guides and an admin list. The only class that uses Core's web API; it
 * is created by {@link BlockIntegrations} only when Core has it.
 *
 * <p>Every route reads and changes state on the server thread ({@code request.sync}). Upgrades go through the same
 * check-and-apply methods as the in-game dialogs ({@code applyUpgrade}: permissions, ownership, complete structure,
 * max level, expected offer, XP cost); remote access additionally needs the owner or an admin (furnaces: owner or
 * {@code manage-others}, as in the dialog). Loot can only be taken within {@link ChatLinks#REMOTE_RANGE} blocks of the
 * tower, like the in-game loot chat button. Chunks are never loaded: structures in unloaded areas are shown with their
 * last known values and cannot be changed.
 */
final class BlocksWeb implements StructureWeb {
    static final String MODULE = "blocks";
    /** Upper bound per list in one answer (the page says when a list was cut). */
    static final int LIST_LIMIT = 300;
    private static final long FLUSH_TICKS = 40L;

    private final BlockIntegrations plugin;
    private final Lang lang;
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final AtomicLong revision = new AtomicLong(1);
    /** Core task group of the live-refresh flush; closed on shutdown. */
    private final TaskGroup tasks;

    private BlocksWeb(BlockIntegrations plugin) {
        this.plugin = plugin;
        this.lang = plugin.lang();
        this.tasks = plugin.core().tasks().of(plugin, "BlockIntegrations/Web");
    }

    static BlocksWeb register(BlockIntegrations plugin) {
        BlocksWeb web = new BlocksWeb(plugin);
        plugin.core().web().register(plugin, web.module());
        web.tasks.timer(FLUSH_TICKS, FLUSH_TICKS, web::flush);
        return web;
    }

    private WebModule module() {
        return WebModule.builder(MODULE, lang.ref("web.module.title"))
                .description(lang.ref("web.module.description"))
                .snapshot(ctx -> Map.of("changed", changedEvent()))
                .route(WebRoute.get("/structures", this::structures).rateLimit(90).build())
                .route(WebRoute.get("/guide", this::guide).rateLimit(30).build())
                .route(WebRoute.get("/towers/{id}/loot", this::loot).rateLimit(60).build())
                .route(WebRoute.post("/towers/{id}/upgrade", req -> upgrade(req, "tower"))
                        .requiresOnline().rateLimit(30).maxBody(256).build())
                .route(WebRoute.post("/furnaces/{id}/upgrade", req -> upgrade(req, "furnace"))
                        .requiresOnline().rateLimit(30).maxBody(256).build())
                .route(WebRoute.post("/combos/{id}/upgrade", req -> upgrade(req, "combo"))
                        .requiresOnline().rateLimit(30).maxBody(256).build())
                .route(WebRoute.post("/towers/{id}/loot/take", this::takeLoot)
                        .requiresOnline().rateLimit(60).maxBody(256).build())
                .route(WebRoute.get("/admin/structures", this::adminStructures)
                        .permission(BlockIntegrations.ADMIN_PERMISSION).rateLimit(30).build())
                .build();
    }

    // ------------------------------------------------------------------ StructureWeb

    @Override
    public void changed(UUID owner) {
        dirty.add(owner);
    }

    @Override
    public boolean available() {
        return plugin.core().web().available();
    }

    @Override
    public void open(Player player, String subPath) {
        plugin.core().web().openOrLink(player, MODULE, subPath == null ? "" : subPath, () -> lang.send(player, "web.unavailable"));
    }

    @Override
    public void shutdown() {
        tasks.close();
        dirty.clear();
        try {
            plugin.core().web().unregister(plugin, MODULE);
        } catch (RuntimeException ignored) {
            // Core cleans up on disable anyway.
        }
    }

    /** Coalesced live signal ({@code blocks/changed}); pages reload their list. A no-op without an open page. */
    private void flush() {
        if (dirty.isEmpty()) return;
        List<UUID> owners = List.copyOf(dirty);
        dirty.removeAll(owners);
        JsonObject event = changedEvent(revision.incrementAndGet());
        for (UUID owner : owners) {
            try {
                plugin.core().web().push(owner, MODULE, "changed", event);
            } catch (RuntimeException failure) {
                DebugMessages.low("[BlockIntegrations/Web] Web push failed: " + failure);
            }
        }
    }

    private JsonObject changedEvent() {
        return changedEvent(revision.get());
    }

    private static JsonObject changedEvent(long rev) {
        JsonObject json = new JsonObject();
        json.addProperty("rev", rev);
        return json;
    }

    // ------------------------------------------------------------------ Routes

    private WebResponse structures(WebRequest req) {
        String language = language(req);
        return WebResponse.json(req.sync(() -> {
            UUID id = req.playerId();
            Player player = Bukkit.getPlayer(id);
            JsonObject json = new JsonObject();
            json.addProperty("online", player != null);
            if (player != null) {
                json.addProperty("admin", player.hasPermission(BlockIntegrations.ADMIN_PERMISSION));
                json.addProperty("manageOthers", player.hasPermission("blockintegrations.furnaces.manage-others"));
            }
            json.addProperty("remoteRange", (int) ChatLinks.REMOTE_RANGE);
            json.addProperty("rev", revision.get());
            TowerManager towers = plugin.towerManager();
            List<Tower> ownTowers = towers.towersOwnedBy(id);
            JsonObject limit = new JsonObject();
            limit.addProperty("owned", ownTowers.size());
            limit.addProperty("max", towers.maximumTowersPerPlayer());
            json.add("towerLimit", limit);
            json.add("towers", towerList(ownTowers, language, json, "towersTruncated"));
            json.add("furnaces", furnaceList(plugin.furnaceManager().furnacesOwnedBy(id), language, json, "furnacesTruncated"));
            json.add("combos", comboList(plugin.comboManager().combosOwnedBy(id), language, json, "combosTruncated"));
            return json;
        }));
    }

    private WebResponse guide(WebRequest req) {
        String language = language(req);
        return WebResponse.json(req.sync(() -> {
            JsonObject json = new JsonObject();
            json.add("towers", plugin.towerManager().webGuide(language));
            json.add("combos", plugin.comboManager().webGuide(language));
            json.add("furnaces", plugin.furnaceManager().webGuide(language));
            return json;
        }));
    }

    private WebResponse adminStructures(WebRequest req) {
        String language = language(req);
        String kind = req.query("kind").orElse("").toLowerCase(Locale.ROOT);
        String filter = req.query("q").orElse("").trim().toLowerCase(Locale.ROOT);
        if (filter.length() > 64) throw WebException.badRequest(lang.plain(language, "web.error.bad-request"));
        return WebResponse.json(req.sync(() -> {
            Player player = Bukkit.getPlayer(req.playerId());
            // Core checked the admin permission (also while offline, with the last known permissions).
            JsonObject json = new JsonObject();
            json.addProperty("online", player != null);
            json.addProperty("limit", LIST_LIMIT);
            if (kind.isEmpty() || kind.equals("tower"))
                json.add("towers", filtered(towerList(plugin.towerManager().allTowers(), language, null, null), filter, json, "towers"));
            if (kind.isEmpty() || kind.equals("furnace"))
                json.add("furnaces", filtered(furnaceList(plugin.furnaceManager().allFurnaces(), language, null, null), filter, json, "furnaces"));
            if (kind.isEmpty() || kind.equals("combo"))
                json.add("combos", filtered(comboList(plugin.comboManager().allCombos(), language, null, null), filter, json, "combos"));
            return json;
        }));
    }

    private WebResponse loot(WebRequest req) {
        String language = language(req);
        BlockKey key = key(req, language);
        return WebResponse.json(req.sync(() -> {
            Player player = Bukkit.getPlayer(req.playerId());
            Tower tower = plugin.towerManager().tower(key);
            if (tower == null) throw WebException.notFound(lang.plain(language, "web.error.unknown"));
            boolean owner = req.playerId().equals(tower.owner());
            if (!owner && (player == null || !plugin.towerManager().webAccess(player, tower)))
                throw WebException.forbidden(lang.plain(language, "web.error.not-yours"));
            return lootJson(tower, player, language);
        }));
    }

    private WebResponse takeLoot(WebRequest req) {
        String language = language(req);
        BlockKey key = key(req, language);
        TakeBody body = req.body(TakeBody.class);
        boolean all = body != null && Boolean.TRUE.equals(body.all());
        if (body == null || (!all && (body.slot() == null || body.slot() < 0 || body.slot() >= 54 || body.material() == null
                || body.material().length() > 64 || body.amount() == null || body.amount() < 1 || body.amount() > 127)))
            throw WebException.badRequest(lang.plain(language, "web.error.bad-request"));
        return WebResponse.json(req.sync(() -> {
            Player player = online(req, language);
            Tower tower = plugin.towerManager().tower(key);
            if (tower == null) throw WebException.notFound(lang.plain(language, "web.error.unknown"));
            TowerManager.LootTake result = plugin.towerManager().takeLoot(player, key, all ? -1 : body.slot(),
                    all ? null : body.material(), all ? 0 : body.amount());
            if (result.blocker() != null) {
                String text = lang.plain(language, result.blocker(), "range", (int) ChatLinks.REMOTE_RANGE);
                throw result.blocker().equals("web.error.not-yours") ? WebException.forbidden(text) : WebException.conflict(text);
            }
            if (result.changed()) throw WebException.conflict(lang.plain(language, "web.error.loot-changed"));
            if (result.moved() <= 0)
                throw WebException.conflict(lang.plain(language, result.inventoryFull() ? "web.error.inventory-full" : "web.error.loot-empty"));
            JsonObject json = new JsonObject();
            json.addProperty("moved", result.moved());
            json.addProperty("full", result.inventoryFull());
            json.addProperty("message", lang.plain(language, result.inventoryFull() ? "web.loot.taken-partly" : "web.loot.taken",
                    "moved", result.moved()));
            json.add("loot", lootJson(tower, player, language));
            return json;
        }));
    }

    private WebResponse upgrade(WebRequest req, String kind) {
        String language = language(req);
        BlockKey key = key(req, language);
        UpgradeBody body = req.body(UpgradeBody.class);
        if (body == null || body.upgrade() == null || body.upgrade().length() > 32 || body.level() == null || body.cost() == null
                || body.level() < 0 || body.level() > 100 || body.cost() < 0)
            throw WebException.badRequest(lang.plain(language, "web.error.bad-request"));
        return WebResponse.json(req.sync(() -> {
            Player player = online(req, language);
            UpgradeResult result;
            String upgradeKey;
            switch (kind) {
                case "tower" -> {
                    TowerManager towers = plugin.towerManager();
                    Tower tower = towers.tower(key);
                    if (tower == null) throw WebException.notFound(lang.plain(language, "web.error.unknown"));
                    if (!towers.webAccess(player, tower)) throw WebException.forbidden(lang.plain(language, "web.error.not-yours"));
                    TowerUpgrade upgrade = enumByKey(TowerUpgrade.values(), body.upgrade(), u -> u.configKey, language);
                    upgradeKey = upgrade.nameKey();
                    result = towers.applyUpgrade(player, key, upgrade, body.level(), body.cost(), false);
                    if (result.outcome() == UpgradeResult.Outcome.GONE && plugin.towerManager().tower(key) != null
                            && "unloaded".equals(towers.state(towers.tower(key))))
                        throw WebException.conflict(lang.plain(language, "web.error.not-loaded"));
                    if (result.outcome() == UpgradeResult.Outcome.GONE)
                        throw WebException.conflict(lang.plain(language, "tower.not-complete"));
                    if (result.outcome() == UpgradeResult.Outcome.NO_PERMISSION)
                        throw WebException.forbidden(lang.plain(language, "tower.no-upgrade-permission"));
                    if (result.outcome() == UpgradeResult.Outcome.NOT_UPGRADEABLE)
                        throw WebException.conflict(lang.plain(language, "tower.starter-no-upgrades"));
                }
                case "furnace" -> {
                    FurnaceManager furnaces = plugin.furnaceManager();
                    FurnaceIndex.Entry entry = furnaces.indexed(key);
                    if (entry == null) throw WebException.notFound(lang.plain(language, "web.error.unknown"));
                    if (!furnaces.webAccess(player, entry.owner())) throw WebException.forbidden(lang.plain(language, "web.error.not-yours"));
                    FurnaceUpgrade upgrade = enumByKey(FurnaceUpgrade.values(), body.upgrade(), u -> u.configKey, language);
                    upgradeKey = upgrade.nameKey();
                    result = furnaces.applyUpgrade(player, key, upgrade, body.level(), body.cost());
                    if (result.outcome() == UpgradeResult.Outcome.GONE)
                        throw WebException.conflict(lang.plain(language, loaded(key) ? "furnace.gone" : "web.error.not-loaded"));
                    if (result.outcome() == UpgradeResult.Outcome.NO_PERMISSION)
                        throw WebException.forbidden(lang.plain(language, "furnace.no-upgrade-permission"));
                    if (result.outcome() == UpgradeResult.Outcome.MAXED)
                        throw WebException.conflict(lang.plain(language, "furnace.maxed"));
                }
                default -> {
                    ComboManager combos = plugin.comboManager();
                    Combo combo = combos.combo(key);
                    if (combo == null) throw WebException.notFound(lang.plain(language, "web.error.unknown"));
                    if (!combos.webAccess(player, combo)) throw WebException.forbidden(lang.plain(language, "web.error.not-yours"));
                    ComboUpgrade upgrade = enumByKey(ComboUpgrade.values(), body.upgrade(), u -> u.configKey, language);
                    upgradeKey = upgrade.nameKey();
                    result = combos.applyUpgrade(player, key, upgrade, body.level(), body.cost());
                    if (result.outcome() == UpgradeResult.Outcome.GONE)
                        throw WebException.conflict(lang.plain(language, loaded(key) ? "combo.incomplete" : "web.error.not-loaded"));
                    if (result.outcome() == UpgradeResult.Outcome.NO_PERMISSION)
                        throw WebException.forbidden(lang.plain(language, "combo.no-upgrade"));
                }
            }
            switch (result.outcome()) {
                case DENIED -> throw WebException.forbidden(lang.plain(language, "web.error.not-yours"));
                case UNSUPPORTED -> throw WebException.badRequest(lang.plain(language, "web.error.bad-request"));
                case MAXED -> throw WebException.conflict(lang.plain(language, "upgrade.maxed"));
                case CHANGED -> throw WebException.conflict(lang.plain(language, "upgrade.offer-changed"));
                case NEED_XP -> throw WebException.conflict(lang.plain(language, "upgrade.need-xp", "cost", result.cost()));
                default -> { }
            }
            if (!result.done()) throw WebException.conflict(lang.plain(language, "web.error.bad-request"));
            JsonObject json = new JsonObject();
            json.addProperty("message", lang.plain(language, "upgrade.done", "upgrade", lang.plain(language, upgradeKey),
                    "level", result.level(), "cost", result.cost()));
            JsonElement view = view(kind, key, language);
            if (view != null) json.add("structure", view);
            return json;
        }));
    }

    // ------------------------------------------------------------------ Helpers

    record UpgradeBody(String upgrade, Integer level, Integer cost) {}

    record TakeBody(Integer slot, String material, Integer amount, Boolean all) {}

    private String language(WebRequest req) {
        return lang.resolveLanguage(req.language());
    }

    private BlockKey key(WebRequest req, String language) {
        BlockKey key = BlockKey.parseWebId(req.pathParam("id"));
        if (key == null) throw WebException.badRequest(lang.plain(language, "web.error.bad-request"));
        return key;
    }

    private Player online(WebRequest req, String language) {
        Player player = Bukkit.getPlayer(req.playerId());
        if (player == null) throw WebException.conflict(lang.plain(language, "web.error.offline"));
        return player;
    }

    private static boolean loaded(BlockKey key) {
        var world = Bukkit.getWorld(key.worldId());
        return world != null && world.isChunkLoaded(key.chunkX(), key.chunkZ());
    }

    private <E> E enumByKey(E[] values, String key, java.util.function.Function<E, String> configKey, String language) {
        for (E value : values) if (configKey.apply(value).equals(key)) return value;
        throw WebException.badRequest(lang.plain(language, "web.error.bad-request"));
    }

    private JsonElement view(String kind, BlockKey key, String language) {
        return switch (kind) {
            case "tower" -> {
                Tower tower = plugin.towerManager().tower(key);
                yield tower == null ? null : plugin.towerManager().webView(tower, language);
            }
            case "furnace" -> {
                FurnaceIndex.Entry entry = plugin.furnaceManager().indexed(key);
                yield entry == null ? null : plugin.furnaceManager().webView(key, entry, language);
            }
            default -> {
                Combo combo = plugin.comboManager().combo(key);
                yield combo == null ? null : plugin.comboManager().webView(combo, language);
            }
        };
    }

    private JsonObject lootJson(Tower tower, Player player, String language) {
        JsonObject json = new JsonObject();
        json.addProperty("id", tower.key.webId());
        json.addProperty("name", lang.plain(language, tower.type.nameKey()));
        JsonArray items = plugin.towerManager().lootItems(tower);
        json.add("items", items);
        String blocker = plugin.towerManager().lootBlocker(player, tower);
        json.addProperty("canTake", blocker == null);
        if (blocker != null) json.addProperty("hint", lang.plain(language, blocker, "range", (int) ChatLinks.REMOTE_RANGE));
        return json;
    }

    private JsonArray towerList(List<Tower> towers, String language, JsonObject parent, String truncatedFlag) {
        JsonArray array = new JsonArray();
        for (Tower tower : towers) {
            if (parent != null && array.size() >= LIST_LIMIT) {
                parent.addProperty(truncatedFlag, true);
                break;
            }
            array.add(plugin.towerManager().webView(tower, language));
        }
        return array;
    }

    private JsonArray furnaceList(List<Map.Entry<BlockKey, FurnaceIndex.Entry>> furnaces, String language, JsonObject parent,
            String truncatedFlag) {
        JsonArray array = new JsonArray();
        for (Map.Entry<BlockKey, FurnaceIndex.Entry> entry : furnaces) {
            if (parent != null && array.size() >= LIST_LIMIT) {
                parent.addProperty(truncatedFlag, true);
                break;
            }
            JsonObject view = plugin.furnaceManager().webView(entry.getKey(), entry.getValue(), language);
            if (view != null) array.add(view);
        }
        return array;
    }

    private JsonArray comboList(List<Combo> combos, String language, JsonObject parent, String truncatedFlag) {
        JsonArray array = new JsonArray();
        for (Combo combo : combos) {
            if (parent != null && array.size() >= LIST_LIMIT) {
                parent.addProperty(truncatedFlag, true);
                break;
            }
            array.add(plugin.comboManager().webView(combo, language));
        }
        return array;
    }

    /** Admin lists: text filter on owner, name, type and world, then at most {@link #LIST_LIMIT} entries. */
    private static JsonArray filtered(JsonArray all, String filter, JsonObject parent, String name) {
        JsonArray out = new JsonArray();
        int matching = 0;
        for (JsonElement element : all) {
            JsonObject entry = element.getAsJsonObject();
            if (!filter.isEmpty() && !matches(entry, filter)) continue;
            matching++;
            if (out.size() < LIST_LIMIT) out.add(entry);
        }
        JsonObject counts = parent.has("totals") ? parent.getAsJsonObject("totals") : new JsonObject();
        counts.addProperty(name, matching);
        parent.add("totals", counts);
        return out;
    }

    static boolean matches(JsonObject entry, String filter) {
        for (String field : new String[] {"owner", "name", "type", "world"}) {
            if (entry.has(field) && entry.get(field).getAsString().toLowerCase(Locale.ROOT).contains(filter)) return true;
        }
        return false;
    }
}
