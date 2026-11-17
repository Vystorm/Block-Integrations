package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class WarpPadsTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void lodestoneOnCryingObsidianIsAWarpPad() {
        assertTrue(ComboType.WARP_PAD.matches(Material.LODESTONE, Material.CRYING_OBSIDIAN, Material.AIR));
        assertFalse(ComboType.WARP_PAD.matches(Material.LODESTONE, Material.OBSIDIAN, Material.AIR));
        assertEquals(ComboType.WARP_PAD, ComboType.byConfigKey("warp-pad"));
        assertTrue(ComboType.WARP_PAD.supports(ComboUpgrade.RANGE));
        assertTrue(ComboType.WARP_PAD.supports(ComboUpgrade.COOLDOWN));
        assertFalse(ComboType.WARP_PAD.supports(ComboUpgrade.POWER));
        assertEquals("combo-upgrade.distance", ComboType.WARP_PAD.upgradeNameKey(ComboUpgrade.RANGE));
        assertEquals("combo-upgrade.cooldown", ComboType.WARP_PAD.upgradeNameKey(ComboUpgrade.COOLDOWN));
    }

    @Test
    void namesAreCleanedAndCut() {
        assertEquals("Home Base", WarpPads.cleanName("  Home   Base "));
        assertEquals("redHome", WarpPads.cleanName("<red>Home"), "tags never survive");
        assertEquals("Mühle #2", WarpPads.cleanName("Mühle #2"));
        assertEquals(WarpPads.MAX_NAME_LENGTH, WarpPads.cleanName("x".repeat(100)).length());
        assertNull(WarpPads.cleanName("   "));
        assertNull(WarpPads.cleanName("<>{}"));
        assertNull(WarpPads.cleanName(null));
    }

    @Test
    void reachGrowsPerLevel() {
        assertEquals(256, WarpPads.reach(256, 128, 0));
        assertEquals(1536, WarpPads.reach(256, 128, 10));
        assertEquals(256, WarpPads.reach(256, 128, -3));
    }

    @Test
    void onlyOwnerAndGuestsMayTravel() {
        UUID owner = UUID.randomUUID(), guest = UUID.randomUUID(), stranger = UUID.randomUUID();
        assertTrue(WarpPads.mayUse(owner, owner, null));
        assertTrue(WarpPads.mayUse(guest, owner, Set.of(guest)));
        assertFalse(WarpPads.mayUse(stranger, owner, Set.of(guest)));
        assertFalse(WarpPads.mayUse(owner, null, Set.of(owner)), "pads without owner are closed");
    }

    @Test
    void sharingAddsRemovesAndIsCapped() {
        UUID owner = UUID.randomUUID(), guest = UUID.randomUUID();
        Set<UUID> guests = new java.util.LinkedHashSet<>();
        assertEquals(WarpPads.ShareResult.SELF, WarpPads.share(guests, owner, owner, true));
        assertEquals(WarpPads.ShareResult.ADDED, WarpPads.share(guests, owner, guest, true));
        assertEquals(WarpPads.ShareResult.UNCHANGED, WarpPads.share(guests, owner, guest, true));
        assertEquals(WarpPads.ShareResult.REMOVED, WarpPads.share(guests, owner, guest, false));
        assertEquals(WarpPads.ShareResult.UNCHANGED, WarpPads.share(guests, owner, guest, false));
        for (int i = 0; i < WarpPads.MAX_GUESTS; i++) WarpPads.share(guests, owner, UUID.randomUUID(), true);
        assertEquals(WarpPads.ShareResult.FULL, WarpPads.share(guests, owner, guest, true));
        assertEquals(WarpPads.MAX_GUESTS, guests.size());
    }

    @Test
    void destinationsAreNearestFirstInTheSameWorldAndWithinReach() {
        BlockKey source = new BlockKey(WORLD, 0, 64, 0);
        BlockKey near = new BlockKey(WORLD, 10, 64, 0);
        BlockKey far = new BlockKey(WORLD, 200, 64, 0);
        BlockKey outOfReach = new BlockKey(WORLD, 400, 64, 0);
        BlockKey otherWorld = new BlockKey(NETHER, 5, 64, 0);
        BlockKey forbidden = new BlockKey(WORLD, 20, 64, 0);
        List<BlockKey> pads = List.of(far, source, outOfReach, otherWorld, near, forbidden);
        assertEquals(List.of(near, far), WarpPads.destinations(source, pads, key -> key, key -> !key.equals(forbidden), 256));

        List<BlockKey> many = new ArrayList<>();
        for (int i = 1; i <= 40; i++) many.add(new BlockKey(WORLD, i, 64, 0));
        List<BlockKey> shown = WarpPads.destinations(source, many, key -> key, key -> true, 256);
        assertEquals(WarpPads.MAX_DESTINATIONS, shown.size());
        assertEquals(1, shown.get(0).x());
    }

    @Test
    void standingOnNeedsTheSameColumnRightOnTop() {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(WORLD);
        BlockKey pad = new BlockKey(WORLD, 3, 64, -5);
        assertTrue(WarpPads.standingOn(new Location(world, 3.5, 65.0, -4.5), pad));
        assertTrue(WarpPads.standingOn(new Location(world, 3.1, 65.5, -4.9), pad));
        assertFalse(WarpPads.standingOn(new Location(world, 4.5, 65.0, -4.5), pad), "next block");
        assertFalse(WarpPads.standingOn(new Location(world, 3.5, 67.0, -4.5), pad), "jumping high above");
        World other = mock(World.class);
        when(other.getUID()).thenReturn(NETHER);
        assertFalse(WarpPads.standingOn(new Location(other, 3.5, 65.0, -4.5), pad));
    }

    @Test
    void arrivalNeedsHarmlessSpace() {
        assertTrue(WarpPads.passable(Material.AIR, false));
        assertTrue(WarpPads.passable(Material.CAVE_AIR, false));
        assertTrue(WarpPads.passable(Material.SHORT_GRASS, false));
        assertTrue(WarpPads.passable(Material.WATER, false));
        assertFalse(WarpPads.passable(Material.STONE, true));
        assertFalse(WarpPads.passable(Material.LAVA, false));
        assertFalse(WarpPads.passable(Material.FIRE, false));
        assertFalse(WarpPads.passable(Material.COBWEB, false));
    }
}
