package local.blockintegrations;

import Vystorm.vystorm_core._coreMethods.dataStorage.AtomicFilesInterface;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StateFileTest {
    @TempDir Path folder;

    private StateFile<ComboManager.CombosState> combos(Path file) {
        return new StateFile<>(file, "BlockIntegrations/Test", ComboManager.CombosState.class, ComboManager.STATE_SCHEMA,
                Map.of(), ComboManager.CombosState::empty, AtomicFilesInterface.DEFAULT);
    }

    @Test
    void roundTripWritesAVersionedEnvelope() throws Exception {
        Path file = folder.resolve("combos.json");
        var state = new ComboManager.CombosState(Map.of("w:1:2:3",
                new ComboManager.ComboRecord("sorter", "00000000-0000-0000-0000-000000000001", Map.of("range", 2))));
        combos(file).save(state);
        var envelope = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(ComboManager.STATE_SCHEMA, envelope.get("schema").getAsInt());
        assertEquals(state, combos(file).load());
    }

    @Test
    void warpPadNamesCollectorXpAndAccessRoundTrip() {
        Path file = folder.resolve("combos.json");
        String owner = "00000000-0000-0000-0000-000000000001", guest = "00000000-0000-0000-0000-000000000002";
        var state = new ComboManager.CombosState(Map.of(
                "w:1:2:3", new ComboManager.ComboRecord("warp-pad", owner, Map.of("range", 1, "cooldown", 0), "Home", null),
                "w:4:5:6", new ComboManager.ComboRecord("xp-collector", owner, Map.of("range", 0, "power", 2), null, 1234)),
                Map.of(owner, java.util.List.of(guest)));
        combos(file).save(state);
        var loaded = combos(file).load();
        assertEquals(state, loaded);
        assertEquals("Home", loaded.combos().get("w:1:2:3").name());
        assertEquals(1234, loaded.combos().get("w:4:5:6").xp());
        assertEquals(java.util.List.of(guest), loaded.warpAccess().get(owner));
    }

    @Test
    void filesFromBeforeWarpPadsStillLoad() throws Exception {
        Path file = folder.resolve("combos.json");
        Files.writeString(file, "{\"schema\":1,\"data\":{\"combos\":{\"w:1:2:3\":{\"type\":\"sorter\",\"owner\":null,"
                + "\"levels\":{\"range\":2}}}}}", StandardCharsets.UTF_8);
        var loaded = combos(file).load();
        var sorter = loaded.combos().get("w:1:2:3");
        assertEquals("sorter", sorter.type());
        assertEquals(2, sorter.levels().get("range"));
        assertNull(sorter.name());
        assertNull(sorter.xp());
        assertTrue(loaded.warpAccess().isEmpty());
    }

    @Test
    void missingFileStartsEmpty() {
        assertTrue(combos(folder.resolve("none.json")).load().combos().isEmpty());
    }

    @Test
    void unreadableFileIsBackedUpAndNeverOverwritten() throws Exception {
        Path file = folder.resolve("combos.json");
        Files.writeString(file, "{ broken", StandardCharsets.UTF_8);
        var store = combos(file);
        assertTrue(store.load().combos().isEmpty());
        assertTrue(store.readOnly());
        store.save(ComboManager.CombosState.empty());
        assertEquals("{ broken", Files.readString(file), "the unreadable file stays untouched");
        try (var backups = Files.list(folder)) {
            assertTrue(backups.anyMatch(p -> p.getFileName().toString().startsWith("combos.json.corrupt-")));
        }
    }

    @Test
    void towerAndPendingStatesRoundTrip() {
        var towers = new StateFile<>(folder.resolve("towers.json"), "BlockIntegrations/Test", TowerManager.TowersState.class,
                TowerManager.STATE_SCHEMA, Map.of(), TowerManager.TowersState::empty, AtomicFilesInterface.DEFAULT);
        var tower = new TowerManager.TowerRecord("arrow", 1, 2, 3, 0, 40, null, Map.of(4, "encoded-item"));
        towers.save(new TowerManager.TowersState(Map.of("w:0:64:0", tower)));
        assertEquals(tower, towers.load().towers().get("w:0:64:0"));
        var pending = new StateFile<>(folder.resolve("pending.json"), "BlockIntegrations/Test", FurnaceManager.PendingState.class,
                FurnaceManager.PENDING_SCHEMA, Map.of(), FurnaceManager.PendingState::empty, AtomicFilesInterface.DEFAULT);
        pending.save(new FurnaceManager.PendingState(Map.of("00000000-0000-0000-0000-000000000001:smelting", 12.5)));
        assertEquals(12.5, pending.load().pending().get("00000000-0000-0000-0000-000000000001:smelting"));
    }
}
