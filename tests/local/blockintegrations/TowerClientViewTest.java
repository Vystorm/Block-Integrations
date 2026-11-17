package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.panel.PanelTesting;
import Vystorm.vystorm_core.web.ClientElement;
import Vystorm.vystorm_core.web.ClientUiTesting;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TowerClientViewTest {
    private static final BlockKey KEY = new BlockKey(UUID.fromString("6f1c2d3e-0000-4000-8000-abcdefabcdef"), -120, 64, 300);

    static Lang lang() {
        try {
            return Lang.create("blockintegrations", Map.of("de", Files.readString(Path.of("lang", "de.yml")),
                    "en", Files.readString(Path.of("lang", "en.yml"))));
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Validated with the client's own schema (public Core test API). */
    static JsonObject valid(ClientElement.Element<?> element) {
        assertEquals(List.of(), ClientUiTesting.validate(element));
        return element.toJson();
    }

    @Test
    void givenTower_whenMarker_thenValidAndKeyedByBlock() {
        JsonObject marker = valid(TowerClientView.marker(KEY, "minecraft:overworld", TowerType.ARCHER, "Archer · 40 XP"));
        assertEquals("tower/6f1c2d3e/-120/64/300", marker.get("id").getAsString());
        assertEquals(-119.5, marker.get("x").getAsDouble());
        assertEquals("minecraft:overworld", marker.get("world").getAsString());
        assertEquals("Archer · 40 XP", PanelTesting.plain(marker.get("label")));
        assertEquals("#FF55FF55", TowerClientView.color(TowerType.ARCHER));
    }

    @Test
    void givenStoredExperience_whenLabel_thenShownOnlyWhenThereIsSome() {
        assertEquals("Archer · 40 XP", TowerClientView.label(lang(), "en", "Archer", 40));
        assertEquals("Archer", TowerClientView.label(lang(), "en", "Archer", 0));
    }

    @Test
    void givenLabelChange_whenPatch_thenOnlyTheLabel() {
        JsonObject patch = TowerClientView.labelPatch(KEY, TowerType.CANNON, "Cannon · 5 XP").toJson();
        assertTrue(patch.has("label"));
        assertFalse(patch.has("x") || patch.has("icon") || patch.has("world"), patch.toString());
    }

    @Test
    void givenPortableKinds_whenTooltip_thenValidRuleMatchingTheStringTag() {
        var rule = TowerClientView.tooltip(lang(), "de", "tower", "zombie-starter", "Zombie-Wachturm");
        JsonObject json = valid(rule);
        assertEquals("portable/tower/zombie-starter", json.get("id").getAsString());
        assertTrue(json.toString().contains("PublicBukkitValues.blockintegrations:portable_kind"), json.toString());
        assertTrue(json.toString().contains("\"tower:zombie-starter\""), json.toString());
        assertTrue(json.toString().contains("Tragbar: Zombie-Wachturm"), json.toString());
        assertFalse(ClientUiTesting.needsExtensions(rule), "the plain rule works on every client");
        assertEquals("tower:zombie-starter", PortableBlockData.kindTag("tower", "zombie-starter"));
        for (ComboType type : ComboType.values()) valid(TowerClientView.tooltip(lang(), "en", "combo", type.configKey, "x"));
        valid(TowerClientView.tooltip(lang(), "en", "furnace", "BLAST_FURNACE", "Blast furnace"));
    }

    @Test
    void givenUpgradeableBlocks_whenTooltipWithLevels_thenOneDataLinePerUpgradeWithinLimits() {
        var tower = TowerClientView.tooltipWithLevels(lang(), "en", "tower", "archer", "Archer tower");
        JsonObject json = valid(tower);
        assertTrue(ClientUiTesting.needsExtensions(tower), "placeholders need client 0.9.0+");
        assertEquals(2 + TowerUpgrade.values().length, json.getAsJsonArray("lines").size());
        assertTrue(json.toString().contains("PublicBukkitValues.blockintegrations:lvl_damage"), json.toString());
        assertEquals("lvl_xp-boost", PortableBlockData.levelTag("xp-boost"));
        JsonObject furnace = valid(TowerClientView.tooltipWithLevels(lang(), "de", "furnace", "FURNACE", "Ofen"));
        assertTrue(furnace.getAsJsonArray("lines").size() <= 8);
        for (ComboType type : ComboType.values()) valid(TowerClientView.tooltipWithLevels(lang(), "en", "combo", type.configKey, "x"));
    }

    @Test
    void givenLootFull_whenToast_thenValidWarningAndActionVariant() {
        var toast = TowerClientView.lootFull(lang(), "en", "Cannon tower");
        assertEquals(List.of(), ClientUiTesting.validate(toast));
        assertEquals("Tower loot chest full", PanelTesting.plain(toast.toJson().get("title")));
        assertEquals("Cannon tower: attacks pause; projectiles in flight can still overflow",
                PanelTesting.plain(toast.toJson().get("text")));
        var action = TowerClientView.lootFullWithAction(lang(), "de", "Kanonenturm");
        assertEquals(List.of(), ClientUiTesting.validate(action));
        assertTrue(action.toJson().toString().contains("Beute öffnen"), action.toJson().toString());
    }
}
