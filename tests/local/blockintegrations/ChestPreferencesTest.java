package local.blockintegrations;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChestPreferencesTest {
    private final UUID world = UUID.randomUUID();

    @Test
    void pdcRoundTripsOrderedFavoritesAndOverflow() {
        NamespacedKey key = new NamespacedKey("blockintegrations", "chest-preferences");
        ChestPreferences manager = new ChestPreferences(key);
        Map<NamespacedKey, String> values = new HashMap<>();
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(eq(key), eq(PersistentDataType.STRING))).thenAnswer(call -> values.get(key));
        doAnswer(call -> { values.put(key, call.getArgument(2)); return null; })
                .when(pdc).set(eq(key), eq(PersistentDataType.STRING), any(String.class));
        doAnswer(call -> { values.remove(key); return null; }).when(pdc).remove(key);

        var a = new ChestPreferences.Position(world, 1, 2, 3);
        var b = new ChestPreferences.Position(world, 4, 5, 6);
        var prefs = ChestPreferences.setOverflow(ChestPreferences.add(
                ChestPreferences.Preferences.empty(), a), b);
        manager.write(pdc, prefs);

        assertEquals(prefs, manager.read(pdc));
        assertEquals(0, ChestPreferences.rank(a.key(), prefs));
        assertEquals(ChestPreferences.MAX_PREFERRED + 1, ChestPreferences.rank(b.key(), prefs));
        assertEquals(ChestPreferences.MAX_PREFERRED,
                ChestPreferences.rank(new ChestPreferences.Position(world, 7, 8, 9).key(), prefs));
        var ordinary = new ChestPreferences.Position(world, 7, 8, 9);
        var order = java.util.stream.Stream.of(b, ordinary, a).sorted(java.util.Comparator.comparingInt(pos -> ChestPreferences.rank(pos.key(), prefs))).toList();
        assertEquals(java.util.List.of(a, ordinary, b), order, "overflow is attempted only after ordinary matching chests");
        assertEquals(0, ChestPreferences.rank(a.key() + "|" + b.key(), prefs));
    }

    @Test
    void strictParserRejectsMalformedOversizedAndOverLimitValues() {
        assertEquals(ChestPreferences.Preferences.empty(), ChestPreferences.decode("v2;P:bad"));
        assertEquals(ChestPreferences.Preferences.empty(), ChestPreferences.decode("v1;P:not-a-position"));
        assertEquals(ChestPreferences.Preferences.empty(), ChestPreferences.decode("x".repeat(4097)));
        UUID id = UUID.randomUUID();
        StringBuilder overLimit = new StringBuilder("v1");
        for (int i = 0; i < 9; i++) overLimit.append(";P:").append(id).append(':').append(i).append(":0:0");
        assertEquals(ChestPreferences.Preferences.empty(), ChestPreferences.decode(overLimit.toString()));
    }

    @Test
    void enforcesEightPreferredPositionsAndSupportsRemovingOverflow() {
        var prefs = ChestPreferences.Preferences.empty();
        for (int i = 0; i < ChestPreferences.MAX_PREFERRED; i++) {
            prefs = ChestPreferences.add(prefs, new ChestPreferences.Position(world, i, 0, 0));
        }
        var full = prefs;
        assertThrows(IllegalArgumentException.class, () -> ChestPreferences.add(full,
                new ChestPreferences.Position(world, 99, 0, 0)));
        var overflow = new ChestPreferences.Position(world, 100, 0, 0);
        prefs = ChestPreferences.setOverflow(prefs, overflow);
        assertNull(ChestPreferences.remove(prefs, overflow).overflow());
    }
}
