package local.blockintegrations;

import Vystorm.vystorm_core.compat.PlatformCompatInterface;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * JSON pieces for the optional web page (Gson only, no Vystorm Core web types, so the managers can build their views
 * without the web API being present). All methods run on the server thread.
 */
final class WebJson {
    static final int MAX_LORE_LINES = 8;
    private static final String FALLBACK_COLOR = "#8a8a8a";

    private WebJson() {}

    /** Common fields of a structure entry: id, kind, type, name, colour and location. */
    static JsonObject structure(String kind, BlockKey key, String type, String name, String color) {
        JsonObject json = new JsonObject();
        json.addProperty("id", key.webId());
        json.addProperty("kind", kind);
        json.addProperty("type", type);
        json.addProperty("name", name);
        json.addProperty("color", color);
        json.addProperty("world", worldName(key.worldId()));
        json.addProperty("x", key.x());
        json.addProperty("y", key.y());
        json.addProperty("z", key.z());
        return json;
    }

    /** One row of an upgrade path as the dialogs show it: level, maximum, current and next value and the next cost. */
    static JsonObject upgrade(String id, String name, int level, int max, String value, String next, Integer cost) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("level", level);
        json.addProperty("max", max);
        json.addProperty("value", value);
        if (next != null) json.addProperty("next", next);
        if (cost != null) json.addProperty("cost", cost);
        return json;
    }

    /** One level row of a guide upgrade table: the value at that level and the XP cost of the next level. */
    static JsonObject levelRow(int level, String value, Integer nextCost) {
        JsonObject json = new JsonObject();
        json.addProperty("level", level);
        json.addProperty("value", value);
        if (nextCost != null) json.addProperty("cost", nextCost);
        return json;
    }

    static JsonObject upgradeTable(String id, String name, int max, JsonArray rows) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("max", max);
        json.add("rows", rows);
        return json;
    }

    /** Legend entry of a building diagram. */
    static JsonObject block(String key, Material material, String name) {
        JsonObject json = new JsonObject();
        json.addProperty("key", key);
        json.addProperty("material", material.name());
        json.addProperty("name", name);
        json.addProperty("color", color(material));
        return json;
    }

    /** A diagram view: label and rows of legend keys ({@code null} = empty/any block). */
    static JsonObject view(String label, String[][] rows) {
        JsonObject json = new JsonObject();
        json.addProperty("label", label);
        JsonArray grid = new JsonArray();
        for (String[] row : rows) {
            JsonArray cells = new JsonArray();
            for (String cell : row) {
                if (cell == null) cells.add(com.google.gson.JsonNull.INSTANCE);
                else cells.add(cell);
            }
            grid.add(cells);
        }
        json.add("rows", grid);
        return json;
    }

    static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    /** Map colour of a block as {@code #rrggbb} (grey when the server has none, e.g. in unit tests or for heads). */
    static String color(Material material) {
        try {
            int rgb = material.createBlockData().getMapColor().asRGB() & 0xFFFFFF;
            return rgb == 0 ? FALLBACK_COLOR : String.format(Locale.ROOT, "#%06x", rgb);
        } catch (RuntimeException | LinkageError noServer) {
            return FALLBACK_COLOR;
        }
    }

    static String hex(net.kyori.adventure.text.format.TextColor color) {
        return color == null ? FALLBACK_COLOR : color.asHexString();
    }

    static String worldName(UUID worldId) {
        World world = Bukkit.getWorld(worldId);
        return world == null ? worldId.toString().substring(0, 8) : world.getName();
    }

    /** An item of the tower loot chest: material, amount, display name, enchantments and lore (plain text). */
    static JsonObject item(PlatformCompatInterface platform, int slot, ItemStack item) {
        JsonObject json = new JsonObject();
        json.addProperty("slot", slot);
        json.addProperty("material", item.getType().name());
        json.addProperty("amount", item.getAmount());
        json.addProperty("max", item.getMaxStackSize());
        String name = null;
        List<String> lore = new ArrayList<>();
        JsonArray enchants = new JsonArray();
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) name = plain(platform.displayName(meta));
            List<Component> lines = platform.lore(meta);
            if (lines != null) for (Component line : lines) {
                if (lore.size() >= MAX_LORE_LINES) break;
                lore.add(plain(line));
            }
            for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet())
                enchants.add(friendly(entry.getKey().getKey().getKey()) + " " + entry.getValue());
        }
        json.addProperty("name", name == null || name.isBlank() ? TowerType.friendly(item.getType()) : name);
        json.addProperty("custom", name != null && !name.isBlank());
        json.add("enchants", enchants);
        json.add("lore", strings(lore));
        return json;
    }

    static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** {@code fire_protection} → {@code Fire Protection}. */
    static String friendly(String key) {
        StringBuilder result = new StringBuilder();
        for (String word : key.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
