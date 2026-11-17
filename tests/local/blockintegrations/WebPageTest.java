package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import Vystorm.vystorm_core.API;
import Vystorm.vystorm_core._coreMethods.playerHandler.PlayerHandlerInterface;
import Vystorm.vystorm_core.lang.Lang;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Server side of the optional web page: ids, furnace index, guide data and the shared upgrade/loot checks. */
class WebPageTest {
    private final UUID worldId = UUID.fromString("0f9d6f0e-2a55-4e0c-9c33-5b1f1c2d3e4f");
    private final UUID ownerId = UUID.randomUUID();
    private BlockIntegrations plugin;
    private PlayerHandlerInterface xp;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(BlockIntegrations.class);
        when(plugin.getName()).thenReturn("BlockIntegrations");
        when(plugin.isEnabled()).thenReturn(false);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        Lang lang = Lang.create("BlockIntegrations", Map.of(
                "en", Files.readString(Path.of("lang", "en.yml")), "de", Files.readString(Path.of("lang", "de.yml"))));
        when(plugin.lang()).thenReturn(lang);
        when(plugin.number(anyString(), anyString(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenAnswer(call -> String.format(BlockIntegrations.locale((String) call.getArgument(0)),
                        (String) call.getArgument(1), (double) call.getArgument(2)));
        API core = TestCore.api();
        xp = mock(PlayerHandlerInterface.class);
        when(core.player()).thenReturn(xp);
        when(plugin.core()).thenReturn(core);
        world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");
    }

    @Test
    void structureIdsRoundTripAndRejectAnythingElse() {
        BlockKey key = new BlockKey(worldId, -120, 64, 7);
        assertEquals(worldId + "_-120_64_7", key.webId());
        assertEquals(key, BlockKey.parseWebId(key.webId()));
        for (String bad : new String[] {null, "", "x_1_2_3", worldId + "_1_2", worldId + "_1_2_3_4", worldId + "_a_2_3",
                worldId + ",1,2,3", "../" + worldId + "_1_2_3"})
            assertNull(BlockKey.parseWebId(bad), String.valueOf(bad));
    }

    @Test
    void furnaceIndexKeepsOwnersTypesAndLevelsAcrossSaves() {
        FurnaceIndex index = new FurnaceIndex();
        BlockKey a = new BlockKey(worldId, 5, 64, 5), b = new BlockKey(worldId, -3, 70, 1), c = new BlockKey(worldId, 9, 64, 9);
        UUID other = UUID.randomUUID();
        assertTrue(index.put(a, new FurnaceIndex.Entry(ownerId, Material.BLAST_FURNACE, Map.of("speed", 3, "fuel", 0))));
        assertFalse(index.put(a, new FurnaceIndex.Entry(ownerId, Material.BLAST_FURNACE, Map.of("speed", 3, "fuel", 0))),
                "unchanged entries do not trigger a save");
        index.put(b, new FurnaceIndex.Entry(ownerId, Material.SMOKER, Map.of()));
        index.put(c, new FurnaceIndex.Entry(other, Material.FURNACE, Map.of("xp-boost", 2)));
        FurnaceIndex loaded = new FurnaceIndex();
        loaded.load(new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(index.save()), FurnaceIndex.State.class));
        assertEquals(3, loaded.size());
        assertEquals(java.util.List.of(b, a), loaded.of(ownerId).stream().map(Map.Entry::getKey).toList(), "sorted by position");
        assertEquals(Map.of("speed", 3), loaded.get(a).levels(), "zero levels are not stored");
        assertEquals(Map.of("xp-boost", 2), loaded.get(c).levels());
        assertEquals(Material.SMOKER, loaded.get(b).type());
        FurnaceIndex.State broken = new FurnaceIndex.State(Map.of(
                a.serialize(), new FurnaceIndex.Saved("nope", "FURNACE", Map.of()),
                b.serialize(), new FurnaceIndex.Saved(ownerId.toString(), "STONE", Map.of()),
                c.serialize(), new FurnaceIndex.Saved(ownerId.toString(), "SMOKER", Map.of())));
        loaded.load(broken);
        assertEquals(1, loaded.size(), "invalid owners and non-furnace types are skipped");
    }

    @Test
    void everyComboGuideLayoutIsAValidBuild() {
        for (ComboType type : ComboType.values()) {
            Material[] layout = type.layout();
            assertTrue(type.matches(layout[0], layout.length > 1 ? layout[1] : Material.AIR,
                    layout.length > 2 ? layout[2] : Material.AIR), type.name());
            assertTrue(type.isControl(layout[0]), type.name());
        }
    }

    @Test
    void towerGuideComesFromTheTypeDefinitionsAndCurrentCosts() throws Exception {
        TowerManager towers = towers();
        JsonObject guide = towers.webGuide("en");
        JsonArray types = guide.getAsJsonArray("types");
        assertEquals(TowerType.values().length, types.size());
        JsonObject archer = find(types, "archer");
        assertEquals("Archer Tower", archer.get("name").getAsString());
        assertEquals("any full copper block", legend(archer, "foundation").get("name").getAsString());
        assertEquals("SKELETON_SKULL", legend(archer, "core").get("material").getAsString());
        assertEquals("Fletching Table", legend(archer, "pedestal").get("name").getAsString());
        JsonArray side = archer.getAsJsonArray("views").get(0).getAsJsonObject().getAsJsonArray("rows");
        assertEquals("core", side.get(0).getAsJsonArray().get(1).getAsString());
        assertTrue(side.get(0).getAsJsonArray().get(0).isJsonNull());
        assertEquals("ring", side.get(2).getAsJsonArray().get(0).getAsString());
        JsonObject damage = archer.getAsJsonArray("upgrades").get(0).getAsJsonObject();
        assertEquals("damage", damage.get("id").getAsString());
        assertEquals(11, damage.getAsJsonArray("rows").size(), "levels 0..10");
        assertEquals(100, damage.getAsJsonArray("rows").get(0).getAsJsonObject().get("cost").getAsInt());
        assertEquals("4.0 damage", damage.getAsJsonArray("rows").get(0).getAsJsonObject().get("value").getAsString());
        assertFalse(damage.getAsJsonArray("rows").get(10).getAsJsonObject().has("cost"), "no cost at the maximum");
        assertEquals(0, find(types, "zombie-starter").getAsJsonArray("upgrades").size(), "starter towers have no upgrades");
        assertEquals("Schützenturm", find(towers.webGuide("de").getAsJsonArray("types"), "archer").get("name").getAsString());
    }

    @Test
    void webUpgradesUseTheDialogChecksAndCosts() throws Exception {
        TowerManager towers = towers();
        BlockKey key = new BlockKey(worldId, 10, 70, 10);
        Tower tower = new Tower(key, TowerType.ARCHER, 2, 0, 0, 0, 10, 0, new ItemStack[0]);
        tower.owner(ownerId);
        map(towers, "towers").put(key, tower);
        Block core = archer(key);
        when(world.isChunkLoaded(key.chunkX(), key.chunkZ())).thenReturn(true);
        when(world.getBlockAt(key.x(), key.y(), key.z())).thenReturn(core);
        Player owner = player(ownerId, false);
        Player stranger = player(UUID.randomUUID(), false);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            assertFalse(towers.webAccess(stranger, tower));
            assertTrue(towers.webAccess(owner, tower));
            assertTrue(towers.webAccess(player(UUID.randomUUID(), true), tower), "admins");

            Player noPermission = player(ownerId, false);
            when(noPermission.hasPermission("blockintegrations.towers.upgrade")).thenReturn(false);
            assertEquals(UpgradeResult.Outcome.NO_PERMISSION,
                    towers.applyUpgrade(noPermission, key, TowerUpgrade.DAMAGE, 2, 275, false).outcome());
            assertEquals(UpgradeResult.Outcome.DENIED,
                    towers.applyUpgrade(stranger, key, TowerUpgrade.DAMAGE, 2, 275, false).outcome());
            verify(stranger, never()).sendMessage(any(net.kyori.adventure.text.Component.class));

            // The page showed an old offer: nothing is paid.
            UpgradeResult changed = towers.applyUpgrade(owner, key, TowerUpgrade.DAMAGE, 1, 175, false);
            assertEquals(UpgradeResult.Outcome.CHANGED, changed.outcome());
            assertEquals(275, changed.cost());
            verify(xp, never()).withdrawExperience(any(), anyInt());

            when(xp.withdrawExperience(owner, 275)).thenReturn(false);
            assertEquals(UpgradeResult.Outcome.NEED_XP, towers.applyUpgrade(owner, key, TowerUpgrade.DAMAGE, 2, 275, false).outcome());
            assertEquals(2, tower.level(TowerUpgrade.DAMAGE));

            when(xp.withdrawExperience(owner, 275)).thenReturn(true);
            UpgradeResult done = towers.applyUpgrade(owner, key, TowerUpgrade.DAMAGE, 2, 275, false);
            assertTrue(done.done());
            assertEquals(3, done.level());
            assertEquals(3, tower.level(TowerUpgrade.DAMAGE));
            verify(plugin).structuresChanged(ownerId);

            org.bukkit.OfflinePlayer offline = mock(org.bukkit.OfflinePlayer.class);
            when(offline.getName()).thenReturn("Esoren");
            bukkit.when(() -> Bukkit.getOfflinePlayer(ownerId)).thenReturn(offline);
            JsonObject view = towers.webView(tower, "en");
            assertEquals("Esoren", view.get("owner").getAsString());
            assertEquals("world", view.get("world").getAsString());
            assertEquals("active", view.get("state").getAsString());
            JsonObject damage = view.getAsJsonArray("upgrades").get(0).getAsJsonObject();
            assertEquals(3, damage.get("level").getAsInt());
            assertEquals(400, damage.get("cost").getAsInt(), "the page offers the next dialog price");

            when(world.isChunkLoaded(key.chunkX(), key.chunkZ())).thenReturn(false);
            assertEquals(UpgradeResult.Outcome.GONE, towers.applyUpgrade(owner, key, TowerUpgrade.DAMAGE, 3, 400, false).outcome(),
                    "never loads chunks");
            assertEquals("unloaded", towers.state(tower));
        }
    }

    @Test
    void lootIsTakenOnlyNearTheTowerAndOnlyWhatThePageShowed() throws Exception {
        TowerManager towers = towers();
        BlockKey key = new BlockKey(worldId, 10, 70, 10);
        Tower tower = new Tower(key, TowerType.ARCHER, 0, 0, 0, 0, 10, 0, new ItemStack[0]);
        tower.owner(ownerId);
        map(towers, "towers").put(key, tower);
        Block core = archer(key);
        when(world.isChunkLoaded(key.chunkX(), key.chunkZ())).thenReturn(true);
        when(world.getBlockAt(key.x(), key.y(), key.z())).thenReturn(core);
        Inventory chest = mock(Inventory.class);
        ItemStack bones = stack(Material.BONE, 12);
        when(chest.getSize()).thenReturn(54);
        when(chest.getItem(3)).thenReturn(bones);
        when(chest.getContents()).thenReturn(new ItemStack[54]);
        map(towers, "lootInventories").put(key, chest);
        Player owner = player(ownerId, false);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(owner.getInventory()).thenReturn(inventory);
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(new Location(world, 200, 70, 10));
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            assertEquals("web.loot.too-far", towers.takeLoot(owner, key, 3, "BONE", 12).blocker());
            assertEquals("web.loot.offline", towers.lootBlocker(null, tower));
            assertEquals("web.error.not-yours", towers.takeLoot(player(UUID.randomUUID(), false), key, 3, "BONE", 12).blocker());
            verify(inventory, never()).addItem(any(ItemStack[].class));

            when(owner.getLocation()).thenReturn(new Location(world, 30, 70, 10));
            assertTrue(towers.takeLoot(owner, key, 3, "BONE", 11).changed(), "amount changed meanwhile");
            assertTrue(towers.takeLoot(owner, key, 3, "ROTTEN_FLESH", 12).changed());

            // Only 5 fit: the rest stays in the chest slot.
            ItemStack rest = stack(Material.BONE, 7);
            HashMap<Integer, ItemStack> leftover = new HashMap<>(Map.of(0, rest));
            when(inventory.addItem(any(ItemStack[].class))).thenReturn(leftover);
            TowerManager.LootTake take = towers.takeLoot(owner, key, 3, "BONE", 12);
            assertNull(take.blocker());
            assertEquals(5, take.moved());
            assertTrue(take.inventoryFull());
            verify(chest).setItem(org.mockito.ArgumentMatchers.eq(3), any(ItemStack.class));
            verify(plugin).structuresChanged(ownerId);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Real manager with default settings; sounds are stubbed (Bukkit's sound registry needs a server). */
    private TowerManager towers() throws Exception {
        TowerManager towers = org.mockito.Mockito.spy(new TowerManager(plugin));
        Method load = TowerManager.class.getDeclaredMethod("loadSettings");
        load.setAccessible(true);
        load.invoke(towers);
        org.mockito.Mockito.doNothing().when(towers).upgradeSound(any());
        org.mockito.Mockito.doNothing().when(towers).lootSound(any());
        return towers;
    }

    private Player player(UUID id, boolean admin) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.hasPermission(BlockIntegrations.ADMIN_PERMISSION)).thenReturn(admin);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        return player;
    }

    /** A complete archer tower: skull on fletching table on copper with a stone brick ring. */
    private Block archer(BlockKey key) {
        Block head = block(Material.SKELETON_SKULL);
        when(head.getWorld()).thenReturn(world);
        when(head.getX()).thenReturn(key.x());
        when(head.getY()).thenReturn(key.y());
        when(head.getZ()).thenReturn(key.z());
        Block air = block(Material.AIR), pedestal = block(Material.FLETCHING_TABLE), copper = block(Material.COPPER_BLOCK);
        Block ringBlock = block(Material.STONE_BRICKS);
        when(head.getRelative(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(head.getRelative(0, -1, 0)).thenReturn(pedestal);
        when(head.getRelative(0, -2, 0)).thenReturn(copper);
        for (int[] ring : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            when(head.getRelative(ring[0], -2, ring[1])).thenReturn(ringBlock);
        return head;
    }

    private static Block block(Material type) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        return block;
    }

    private static ItemStack stack(Material type, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        when(stack.clone()).thenReturn(stack);
        return stack;
    }

    private static JsonObject find(JsonArray types, String type) {
        for (var element : types) if (element.getAsJsonObject().get("type").getAsString().equals(type)) return element.getAsJsonObject();
        throw new AssertionError(type);
    }

    private static JsonObject legend(JsonObject type, String key) {
        for (var element : type.getAsJsonArray("blocks")) if (element.getAsJsonObject().get("key").getAsString().equals(key)) return element.getAsJsonObject();
        throw new AssertionError(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockKey, Object> map(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (Map<BlockKey, Object>) field.get(target);
    }
}
