package local.blockintegrations;

import Vystorm.vystorm_core.compat.dialog.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;

/** Read-only live dashboard over existing machine registries; never loads chunks. */
final class MachineDashboard {
    record Machine(String kind, BlockKey key, String nameKey) {
        String id() { return kind + ":" + key.serialize(); }
    }
    record Observation(String state, long notified) {}
    static final int MAX_WATCHED = 32;
    private final BlockIntegrations plugin;
    private final NamespacedKey watches, groupsKey, pausedKey;
    private final Map<UUID, Map<String, Observation>> previous = new HashMap<>();
    private BukkitTask task;
    MachineDashboard(BlockIntegrations plugin) {
        this.plugin = plugin; watches = new NamespacedKey(plugin, "machine-watches");
        groupsKey = new NamespacedKey(plugin, "machine-groups"); pausedKey = new NamespacedKey(plugin, "machine-group-paused");
    }
    void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::poll, 200, 200); }
    void stop() { if (task != null) task.cancel(); previous.clear(); }
    List<Machine> machines(Player p) {
        List<Machine> result = new ArrayList<>();
        if (p.hasPermission(BlockCommands.COMBOS)) for (Combo c : plugin.comboManager().combosOwnedBy(p.getUniqueId()))
            result.add(new Machine("combo", c.key, c.type.nameKey()));
        if (p.hasPermission(BlockCommands.TOWERS)) for (Tower t : plugin.towerManager().towersOwnedBy(p.getUniqueId()))
            result.add(new Machine("tower", t.key, t.type.nameKey()));
        if (p.hasPermission(BlockCommands.FURNACES)) for (var f : plugin.furnaceManager().furnacesOwnedBy(p.getUniqueId()))
            result.add(new Machine("furnace", f.getKey(), "dashboard.furnace"));
        return result;
    }
    String state(Machine machine) {
        BlockKey key = machine.key(); World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) return "unloaded";
        var block = world.getBlockAt(key.x(), key.y(), key.z());
        if (machine.kind().equals("furnace")) return block.getState() instanceof Furnace f ? furnaceState(f) : "incomplete";
        if (machine.kind().equals("combo")) {
            Combo combo = plugin.comboManager().combo(key);
            if (combo == null) return "incomplete";
            String structure = plugin.comboManager().state(combo);
            if (!structure.equals("active")) return structure;
            if (combo.type == ComboType.HARVESTER) {
                if (combo.harvestProfile() == HarvestProfile.PAUSED) return "paused";
                for (int[] offset : new int[][] {{1,0},{-1,0},{0,1},{0,-1}})
                    if (!world.isChunkLoaded((key.x() + offset[0]) >> 4, (key.z() + offset[1]) >> 4)) return "unloaded";
                var output = Harvester.container(plugin.core(), block);
                if (plugin.harvestPauseWhenFull() && output != null && Harvester.noFreeSlot(output.getStorageContents())) return "output";
            }
            return "ready";
        }
        Tower tower = plugin.towerManager().tower(key);
        if (tower == null) return "incomplete";
        var structure = MachineDiagnostics.tower(tower.type, block);
        if (structure.state() != MachineDiagnostics.State.COMPLETE) return structure.state().name().toLowerCase(Locale.ROOT);
        if (tower.targetProfile == TowerControls.TargetProfile.NONE || !tower.redstoneMode.active(block.isBlockPowered() || block.isBlockIndirectlyPowered())) return "paused";
        return "ready";
    }
    static String furnaceState(Furnace furnace) {
        var inventory = furnace.getInventory(); ItemStack input = inventory.getSmelting(), output = inventory.getResult();
        if (empty(input)) return "idle";
        if (!empty(output) && output.getAmount() >= Math.min(output.getMaxStackSize(), inventory.getMaxStackSize())) return "output";
        if (furnace.getBurnTime() > 0 && furnace.getCookTime() > 0) return "running";
        ItemStack fuel = inventory.getFuel();
        if (furnace.getBurnTime() <= 0 && empty(fuel)) return "fuel";
        return "waiting";
    }
    private static boolean empty(ItemStack item) {
        return item == null || item.getAmount() <= 0 || item.getType() == Material.AIR
                || item.getType() == Material.CAVE_AIR || item.getType() == Material.VOID_AIR;
    }
    void open(Player player, int requested) {
        if (!player.hasPermission(BlockCommands.USE)) return;
        var lang = plugin.lang(); List<Machine> list = machines(player);
        int page = Math.clamp(requested, 0, Math.max(0, (list.size() - 1) / 12));
        List<DialogButton> buttons = new ArrayList<>();
        for (Machine machine : list.subList(page * 12, Math.min(list.size(), page * 12 + 12)))
            buttons.add(new DialogButton(lang.component(player, "dashboard.row", "name", lang.component(player, machine.nameKey()),
                    "x", machine.key().x(), "y", machine.key().y(), "z", machine.key().z(), "state", lang.component(player, "dashboard.state-" + state(machine))),
                    (p, r) -> detail(p, machine)));
        if (page > 0) buttons.add(new DialogButton(lang.component(player, "dashboard.previous"), (p, r) -> open(p, page - 1)));
        if (page * 12 + 12 < list.size()) buttons.add(new DialogButton(lang.component(player, "dashboard.next"), (p, r) -> open(p, page + 1)));
        buttons.add(new DialogButton(lang.component(player, "groups.button", "count", groups(player).size()), (p, r) -> openGroups(p, null)));
        buttons.add(new DialogButton(lang.component(player, "dashboard.refresh"), (p, r) -> open(p, page)));
        plugin.core().dialogs().open(plugin, player, new DialogRequest(lang.component(player, "dashboard.title"),
                List.of(DialogBody.plainMessage(lang.component(player, "dashboard.intro", "count", list.size(), "page", page + 1))),
                List.of(), buttons, p -> p.isOnline() && p.hasPermission(BlockCommands.USE), () -> {}));
    }
    private void detail(Player p, Machine machine) {
        if (!p.hasPermission(BlockCommands.USE) || !machines(p).contains(machine)) return;
        var lang = plugin.lang(); String status = state(machine);
        plugin.core().dialogs().open(plugin, p, new DialogRequest(lang.component(p, "dashboard.title"),
                List.of(DialogBody.plainMessage(lang.component(p, "dashboard.detail", "name", lang.component(p, machine.nameKey()),
                        "world", Optional.ofNullable(Bukkit.getWorld(machine.key().worldId())).map(World::getName).orElse(machine.key().worldId().toString()),
                        "x", machine.key().x(), "y", machine.key().y(), "z", machine.key().z(), "state", lang.component(p, "dashboard.state-" + status))),
                        DialogBody.plainMessage(lang.component(p, "dashboard.hint-" + status))), List.of(),
                detailButtons(p, machine),
                viewer -> viewer.isOnline() && viewer.hasPermission(BlockCommands.USE), () -> {}));
    }
    private List<DialogButton> detailButtons(Player p, Machine machine) {
        var lang = plugin.lang();
        List<DialogButton> buttons = new ArrayList<>();
        buttons.add(new DialogButton(lang.component(p, watched(p).contains(machine.id()) ? "dashboard.unwatch" : "dashboard.watch"), (viewer, r) -> {
            toggle(viewer, machine); detail(viewer, machine);
        }));
        MachineGroups groups = groups(p);
        for (String name : groups.names()) {
            boolean member = groups.members(name).contains(machine.id());
            buttons.add(new DialogButton(lang.component(p, member ? "groups.remove-from" : "groups.add-to", "group", name), (viewer, r) -> {
                MachineGroups g = groups(viewer);
                if (member) g.remove(name, machine.id());
                else { String result = g.add(name, machine.id()); if (!result.equals("ok")) lang.send(viewer, "groups.error-" + result, "max", MachineGroups.MAX_MEMBERS); }
                saveGroups(viewer, g); detail(viewer, machine);
            }));
        }
        buttons.add(new DialogButton(lang.component(p, "dashboard.back"), (viewer, r) -> open(viewer, 0)));
        return buttons;
    }

    // ---- machine groups ----

    MachineGroups groups(Player p) { return MachineGroups.decode(p.getPersistentDataContainer().get(groupsKey, PersistentDataType.STRING)); }

    private void saveGroups(Player p, MachineGroups groups) {
        String text = groups.encode();
        if (text.isEmpty()) p.getPersistentDataContainer().remove(groupsKey);
        else p.getPersistentDataContainer().set(groupsKey, PersistentDataType.STRING, text);
    }

    /** Members of a group the player still owns (others are dropped from the stored group). */
    private List<Machine> members(Player p, String name) {
        MachineGroups groups = groups(p);
        Map<String, Machine> owned = new LinkedHashMap<>();
        for (Machine m : machines(p)) owned.put(m.id(), m);
        if (groups.retain(owned.keySet())) saveGroups(p, groups);
        List<Machine> out = new ArrayList<>();
        for (String id : groups.members(name)) if (owned.containsKey(id)) out.add(owned.get(id));
        return out;
    }

    private Component summary(Player p, List<Machine> members) {
        var lang = plugin.lang();
        if (members.isEmpty()) return lang.component(p, "groups.empty-group");
        List<String> states = new ArrayList<>();
        for (Machine m : members) states.add(state(m));
        Component out = Component.empty();
        boolean first = true;
        for (var entry : MachineGroups.summary(states)) {
            if (!first) out = out.append(Component.text(", "));
            out = out.append(lang.component(p, "groups.count", "count", entry.getValue(), "state", lang.component(p, "dashboard.state-" + entry.getKey())));
            first = false;
        }
        return out;
    }

    void openGroups(Player player, Component notice) {
        if (!player.hasPermission(BlockCommands.USE)) return;
        var lang = plugin.lang();
        MachineGroups groups = groups(player);
        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(DialogBody.plainMessage(notice));
        body.add(DialogBody.plainMessage(lang.component(player, groups.size() == 0 ? "groups.intro-empty" : "groups.intro", "max", MachineGroups.MAX_GROUPS)));
        List<DialogButton> buttons = new ArrayList<>();
        for (String name : groups.names()) {
            List<Machine> members = members(player, name);
            buttons.add(new DialogButton(lang.component(player, "groups.row", "group", name, "count", members.size()).append(Component.text(" · ")).append(summary(player, members)),
                    (p, r) -> openGroup(p, name, null)));
        }
        if (groups.size() < MachineGroups.MAX_GROUPS) buttons.add(new DialogButton(lang.component(player, "groups.new"), (p, r) -> askName(p, null)));
        buttons.add(new DialogButton(lang.component(player, "dashboard.back"), (p, r) -> open(p, 0)));
        plugin.core().dialogs().open(plugin, player, new DialogRequest(lang.component(player, "groups.title"), body, List.of(), buttons,
                p -> p.isOnline() && p.hasPermission(BlockCommands.USE), () -> {}));
    }

    /** Name dialog: new group ({@code old} null) or rename. */
    private void askName(Player player, String old) {
        var lang = plugin.lang();
        plugin.core().dialogs().open(plugin, player, new DialogRequest(lang.component(player, old == null ? "groups.new-title" : "groups.rename-title"),
                List.of(DialogBody.plainMessage(lang.component(player, "groups.name-hint", "max", MachineGroups.MAX_NAME))),
                List.of(DialogInput.text("name", lang.component(player, "groups.name")).initial(old == null ? "" : old).maxLength(MachineGroups.MAX_NAME).build()),
                List.of(new DialogButton(lang.component(player, "groups.save"), (p, r) -> {
                    MachineGroups g = groups(p);
                    String name = MachineGroups.clean(String.valueOf(r.getText("name")));
                    String result = old == null ? g.create(name) : g.rename(old, name);
                    if (!result.equals("ok")) { openGroups(p, lang.component(p, "groups.error-" + result, "max", MachineGroups.MAX_GROUPS)); return; }
                    saveGroups(p, g);
                    openGroup(p, name, null);
                }), new DialogButton(lang.component(player, "dashboard.back"), (p, r) -> openGroups(p, null))),
                p -> p.isOnline() && p.hasPermission(BlockCommands.USE), () -> {}));
    }

    void openGroup(Player player, String name, Component notice) {
        var lang = plugin.lang();
        if (groups(player).find(name) == null) { openGroups(player, lang.component(player, "groups.error-unknown")); return; }
        List<Machine> members = members(player, name);
        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(DialogBody.plainMessage(notice));
        body.add(DialogBody.plainMessage(lang.component(player, "groups.detail", "group", name, "count", members.size()).append(Component.newline()).append(summary(player, members))));
        if (members.isEmpty()) body.add(DialogBody.plainMessage(lang.component(player, "groups.how-to-add")));
        List<DialogButton> buttons = new ArrayList<>();
        for (Machine m : members) buttons.add(new DialogButton(lang.component(player, "dashboard.row", "name", lang.component(player, m.nameKey()),
                "x", m.key().x(), "y", m.key().y(), "z", m.key().z(), "state", lang.component(player, "dashboard.state-" + state(m))), (p, r) -> detail(p, m)));
        boolean harvesters = members.stream().anyMatch(m -> m.kind().equals("combo") && isHarvester(m));
        if (harvesters) {
            buttons.add(new DialogButton(lang.component(player, "groups.pause"), (p, r) -> openGroup(p, name, lang.component(p, "groups.paused", "count", pause(p, name, true)))));
            buttons.add(new DialogButton(lang.component(player, "groups.resume"), (p, r) -> openGroup(p, name, lang.component(p, "groups.resumed", "count", pause(p, name, false)))));
        }
        if (!members.isEmpty()) {
            buttons.add(new DialogButton(lang.component(player, "groups.watch-all"), (p, r) -> openGroup(p, name, lang.component(p, "groups.watched", "count", watchAll(p, name, true)))));
            buttons.add(new DialogButton(lang.component(player, "groups.unwatch-all"), (p, r) -> openGroup(p, name, lang.component(p, "groups.unwatched", "count", watchAll(p, name, false)))));
        }
        buttons.add(new DialogButton(lang.component(player, "groups.rename"), (p, r) -> askName(p, name)));
        buttons.add(new DialogButton(lang.component(player, "groups.delete"), (p, r) -> {
            MachineGroups g = groups(p); g.delete(name); saveGroups(p, g);
            openGroups(p, lang.component(p, "groups.deleted", "group", name));
        }));
        buttons.add(new DialogButton(lang.component(player, "dashboard.back"), (p, r) -> openGroups(p, null)));
        plugin.core().dialogs().open(plugin, player, new DialogRequest(lang.component(player, "groups.title"), body, List.of(), buttons,
                p -> p.isOnline() && p.hasPermission(BlockCommands.USE), () -> {}));
    }

    private boolean isHarvester(Machine m) {
        Combo combo = plugin.comboManager().combo(m.key());
        return combo != null && combo.type == ComboType.HARVESTER;
    }

    /** Pauses or resumes every harvester of the group; a resumed one gets back the profile it had before. Returns how many changed. */
    int pause(Player p, String name, boolean pause) {
        Map<String, String> remembered = decodePaused(p.getPersistentDataContainer().get(pausedKey, PersistentDataType.STRING));
        int changed = 0;
        for (Machine m : members(p, name)) {
            Combo combo = plugin.comboManager().combo(m.key());
            if (combo == null || combo.type != ComboType.HARVESTER) continue;
            HarvestProfile current = combo.harvestProfile();
            if (pause) {
                if (current == HarvestProfile.PAUSED) continue;
                if (plugin.comboManager().setHarvestProfileRemote(p, combo, HarvestProfile.PAUSED)) { remembered.put(m.id(), current.key); changed++; }
            } else {
                if (current != HarvestProfile.PAUSED) continue;
                HarvestProfile back = HarvestProfile.parse(remembered.getOrDefault(m.id(), HarvestProfile.ALL.key));
                if (back == HarvestProfile.PAUSED) back = HarvestProfile.ALL;
                if (plugin.comboManager().setHarvestProfileRemote(p, combo, back)) { remembered.remove(m.id()); changed++; }
            }
        }
        String text = encodePaused(remembered);
        if (text.isEmpty()) p.getPersistentDataContainer().remove(pausedKey);
        else p.getPersistentDataContainer().set(pausedKey, PersistentDataType.STRING, text);
        return changed;
    }

    static Map<String, String> decodePaused(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        if (text != null && text.length() <= 8192) for (String line : text.split("\n")) {
            int eq = line.lastIndexOf('=');
            if (eq > 0 && MachineGroups.valid(line.substring(0, eq)) && out.size() < 256) out.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return out;
    }

    static String encodePaused(Map<String, String> map) {
        StringBuilder out = new StringBuilder();
        map.forEach((id, profile) -> { if (!out.isEmpty()) out.append('\n'); out.append(id).append('=').append(profile); });
        return out.toString();
    }

    /** Turns alerts on or off for every member; returns how many changed (the watch limit applies). */
    int watchAll(Player p, String name, boolean on) {
        int changed = 0;
        for (Machine m : members(p, name)) {
            boolean watching = watched(p).contains(m.id());
            if (watching == on) continue;
            if (on && watched(p).size() >= MAX_WATCHED) { plugin.lang().send(p, "dashboard.limit", "count", MAX_WATCHED); break; }
            toggle(p, m);
            changed++;
        }
        return changed;
    }

    Set<String> watched(Player p) { return decode(p.getPersistentDataContainer().get(watches, PersistentDataType.STRING)); }
    static Set<String> decode(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text != null && text.length() <= 4096) for (String id : text.split("\\n")) {
            String[] parts = id.split(":", 2);
            if (parts.length != 2 || !Set.of("furnace", "tower", "combo").contains(parts[0])) continue;
            try { BlockKey.parse(parts[1]); if (result.size() < MAX_WATCHED) result.add(id); } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
    void toggle(Player p, Machine machine) {
        if (!p.hasPermission(BlockCommands.USE) || !machines(p).contains(machine)) return;
        Set<String> selected = watched(p);
        if (!selected.remove(machine.id())) {
            if (selected.size() >= MAX_WATCHED) { plugin.lang().send(p, "dashboard.limit", "count", MAX_WATCHED); return; }
            selected.add(machine.id());
        }
        p.getPersistentDataContainer().set(watches, PersistentDataType.STRING, String.join("\n", selected));
        previous.computeIfAbsent(p.getUniqueId(), id -> new HashMap<>()).remove(machine.id());
    }
    void poll() {
        if (!BlockIntegrations.enabled(plugin, Feature.DASHBOARD)) return; // dashboard.enabled off: no alerts
        Set<UUID> online = new HashSet<>(); long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p.getUniqueId());
            if (!p.hasPermission(BlockCommands.USE)) { previous.remove(p.getUniqueId()); continue; }
            Set<String> selected = watched(p); if (selected.isEmpty()) { previous.remove(p.getUniqueId()); continue; }
            Map<String, Observation> known = previous.computeIfAbsent(p.getUniqueId(), id -> new HashMap<>());
            known.keySet().retainAll(selected);
            for (Machine machine : machines(p)) if (selected.contains(machine.id())) {
                String state = state(machine); Observation old = known.get(machine.id());
                boolean notify = shouldNotify(old, state, now);
                if (notify) plugin.lang().send(p, "dashboard.alert", "name", plugin.lang().component(p, machine.nameKey()),
                        "x", machine.key().x(), "y", machine.key().y(), "z", machine.key().z(), "state", plugin.lang().component(p, "dashboard.state-" + state));
                known.put(machine.id(), new Observation(state, notify ? now : old == null ? 0 : old.notified()));
            }
        }
        previous.keySet().retainAll(online);
    }
    static boolean shouldNotify(Observation old, String state, long now) {
        return old != null && !old.state().equals(state) && now - old.notified() >= 120_000;
    }
}
