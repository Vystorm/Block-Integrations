package local.blockintegrations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MachineGroupsTest {
    private static String id(String kind, int x) { return kind + ":" + new BlockKey(UUID.fromString("00000000-0000-0000-0000-000000000001"), x, 64, 0).serialize(); }

    @Test
    void givenGroups_whenEncodedAndDecoded_thenNamesAndMembersSurvive() {
        MachineGroups g = new MachineGroups();
        assertEquals("ok", g.create("Farm"));
        assertEquals("ok", g.create("Smeltery"));
        assertEquals("ok", g.add("farm", id("combo", 1)));
        assertEquals("ok", g.add("Farm", id("combo", 2)));
        assertEquals("ok", g.add("Smeltery", id("furnace", 3)));
        MachineGroups back = MachineGroups.decode(g.encode());
        assertEquals(List.of("Farm", "Smeltery"), back.names());
        assertEquals(Set.of(id("combo", 1), id("combo", 2)), back.members("FARM"));
        assertEquals(List.of("Smeltery"), back.groupsOf(id("furnace", 3)));
    }

    @Test
    void givenNames_whenCreatingAndRenaming_thenRulesApply() {
        MachineGroups g = new MachineGroups();
        assertEquals("name", g.create("  \t "));
        assertEquals("ok", g.create("Base, defence\tline"));
        assertEquals(List.of("Base defence line"), g.names());
        assertEquals("exists", g.create("base DEFENCE line"));
        assertEquals("ok", g.create("Farm"));
        assertEquals("exists", g.rename("Farm", "Base defence line"));
        assertEquals("ok", g.rename("Farm", "Wheat"));
        assertEquals(List.of("Base defence line", "Wheat"), g.names());
        assertEquals("unknown", g.rename("Nope", "X"));
        for (int i = 0; i < 10; i++) g.create("G" + i);
        assertEquals(MachineGroups.MAX_GROUPS, g.size());
        assertEquals("limit", g.create("One more"));
        assertEquals(MachineGroups.MAX_NAME, MachineGroups.clean("x".repeat(60)).length());
    }

    @Test
    void givenMembers_whenLimitsAndInvalidIds_thenRejected() {
        MachineGroups g = new MachineGroups();
        g.create("A");
        assertEquals("unknown", g.add("A", "pickaxe:1"));
        assertEquals("unknown", g.add("B", id("combo", 1)));
        for (int i = 0; i < MachineGroups.MAX_MEMBERS; i++) assertEquals("ok", g.add("A", id("tower", i)));
        assertEquals("full", g.add("A", id("tower", 999)));
        assertEquals("ok", g.add("A", id("tower", 0)), "adding a member twice is fine");
        assertTrue(g.retain(Set.of(id("tower", 0))));
        assertEquals(Set.of(id("tower", 0)), g.members("A"));
        assertTrue(g.remove("A", id("tower", 0)));
        assertTrue(g.delete("a"));
        assertEquals(0, g.size());
    }

    @Test
    void givenBrokenText_whenDecoding_thenBadLinesAreSkipped() {
        MachineGroups g = MachineGroups.decode("Farm\t" + id("combo", 1) + "|garbage|\n\t" + id("combo", 2) + "\nfarm\t" + id("combo", 3) + "\nSolo");
        assertEquals(List.of("Farm", "Solo"), g.names());
        assertEquals(Set.of(id("combo", 1)), g.members("Farm"));
        assertTrue(g.members("Solo").isEmpty());
        assertEquals(0, MachineGroups.decode(null).size());
    }

    @Test
    void givenStates_whenSummarised_thenMostCommonFirst() {
        var summary = MachineGroups.summary(List.of("ready", "fuel", "ready", "output", "ready", "fuel"));
        assertEquals(List.of(Map.entry("ready", 3), Map.entry("fuel", 2), Map.entry("output", 1)), summary);
    }

    @Test
    void givenRememberedProfiles_whenEncoded_thenDecodedBack() {
        var map = new java.util.LinkedHashMap<String, String>();
        map.put(id("combo", 1), "fruit");
        map.put(id("combo", 2), "all");
        assertEquals(map, MachineDashboard.decodePaused(MachineDashboard.encodePaused(map)));
        assertTrue(MachineDashboard.decodePaused("nonsense=1\n").isEmpty());
    }
}
