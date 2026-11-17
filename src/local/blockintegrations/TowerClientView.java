package local.blockintegrations;

import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.web.ClientElement;
import java.util.Locale;
import org.bukkit.Material;

/**
 * Pure views of the client-mod elements (no Bukkit state): tower markers, the loot-full toast and the tooltip rules of
 * portable blocks. Tooltip rules match the string tag {@link PortableBlockData#KIND_TAG} that portable items carry.
 */
final class TowerClientView {
    /** Path of the portable-kind tag in the item's {@code minecraft:custom_data} (Bukkit PDC lives under PublicBukkitValues). */
    static final String KIND_PATH = "PublicBukkitValues.blockintegrations:" + PortableBlockData.KIND_TAG;

    private TowerClientView() {}

    /** Stable marker id per tower block: {@code tower/<world prefix>/<x>/<y>/<z>}. */
    static String markerId(BlockKey key) {
        return "tower/" + key.worldId().toString().substring(0, 8) + "/" + key.x() + "/" + key.y() + "/" + key.z();
    }

    /** Marker label: tower name, plus the withdrawable XP when there is some. */
    static String label(Lang lang, String language, String name, int storedExperience) {
        return storedExperience > 0 ? lang.plain(language, "client.tower-marker-xp", "name", name, "xp", storedExperience)
                : lang.plain(language, "client.tower-marker", "name", name);
    }

    static ClientElement.Marker marker(BlockKey key, String worldKey, TowerType type, String label) {
        String color = color(type);
        return ClientElement.marker(markerId(key)).at(key.x() + 0.5, key.y() + 1.6, key.z() + 0.5).world(worldKey)
                .label(ClientElement.Text.of(label, color)).color(color).icon(ClientElement.Icon.item(type.core))
                .maxDistance(512).showDistance(true).clampToScreen(false);
    }

    /** Only the label (PATCH when the stored XP changed). */
    static ClientElement.Marker labelPatch(BlockKey key, TowerType type, String label) {
        return ClientElement.marker(markerId(key)).label(ClientElement.Text.of(label, color(type)));
    }

    /** With an action ({@code [Y] Open loot}, client 0.9.0+); the handler re-checks everything like the chat button. */
    static ClientElement.Toast lootFullWithAction(Lang lang, String language, String name) {
        return lootFull(lang, language, name).action(lang.plain(language, "client.loot-open")).duration(8000);
    }

    static ClientElement.Toast lootFull(Lang lang, String language, String name) {
        return ClientElement.toast(ClientElement.Text.of(lang.plain(language, "client.loot-full-title"), "#FFFFFF55").bold())
                .text(lang.plain(language, "client.loot-full-text", "name", name))
                .icon(ClientElement.Icon.item(Material.CHEST)).style(ClientElement.Toast.Style.WARNING).duration(6000);
    }

    /** Tooltip rule id per portable kind/type. */
    static String tooltipId(String kind, String type) {
        return ("portable/" + kind + "/" + type).toLowerCase(Locale.ROOT);
    }

    /** Two appended lines: what the item is and what placing it restores, in the viewer's language. */
    static ClientElement.Tooltip tooltip(Lang lang, String language, String kind, String type, String name) {
        return ClientElement.tooltip(tooltipId(kind, type))
                .matchCustomData(KIND_PATH, PortableBlockData.kindTag(kind, type))
                .line(ClientElement.Text.of(lang.plain(language, "client.tooltip-title", "name", name), "#FFFFAA00"))
                .line(ClientElement.Text.of(lang.plain(language, "client.tooltip-" + kind), "#FFAAAAAA"));
    }

    /** Path of an upgrade's level tag in the item's custom data. */
    static String levelPath(String upgradeKey) {
        return "PublicBukkitValues.blockintegrations:" + PortableBlockData.levelTag(upgradeKey);
    }

    /**
     * Like {@link #tooltip} plus one line per upgrade with the level read from the hovered item ({@code {data:…}},
     * client 0.9.0+). Upgrades the block does not have are skipped by the client (placeholder missing); at most 8 lines.
     */
    static ClientElement.Tooltip tooltipWithLevels(Lang lang, String language, String kind, String type, String name) {
        ClientElement.Tooltip rule = tooltip(lang, language, kind, type, name);
        int lines = 2;
        for (var upgrade : PortableBlockData.upgrades(kind).entrySet()) {
            if (lines++ >= 8) break;
            rule.line(ClientElement.Text.of(lang.plain(language, upgrade.getValue()) + ": ", "#FFAAAAAA")
                    .then(ClientElement.data(levelPath(upgrade.getKey())), "#FFFFFFFF"));
        }
        return rule;
    }

    static String color(TowerType type) {
        return String.format(Locale.ROOT, "#FF%06X", type.color.value());
    }
}
