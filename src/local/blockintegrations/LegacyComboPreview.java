package local.blockintegrations;

import Vystorm.vystorm_core.storage.MigrationPreviews;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Detached legacy YAML conversion for comparison only. Never writes a state file. */
final class LegacyComboPreview {
    private LegacyComboPreview() {}
    static MigrationPreviews.Report convert(byte[] source) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(new String(source, StandardCharsets.UTF_8));
        ConfigurationSection combos = yaml.getConfigurationSection("combos");
        if (combos == null) return new MigrationPreviews.Report(0, 0, Map.of(), Map.of(), List.of("missing combos mapping"));
        Map<String, ComboManager.ComboRecord> converted = new LinkedHashMap<>();
        Map<String, BigDecimal> before = new TreeMap<>(), after = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (String key : combos.getKeys(false)) {
            try {
                BlockKey.parse(key);
                ConfigurationSection row = combos.getConfigurationSection(key);
                if (row == null) throw new IllegalArgumentException("not a mapping");
                String type = row.getString("type"), owner = row.getString("owner");
                if (ComboType.byConfigKey(type == null ? "" : type) == null) throw new IllegalArgumentException("unknown type");
                if (owner != null && !owner.isBlank()) UUID.fromString(owner);
                Map<String, Integer> levels = new LinkedHashMap<>();
                for (ComboUpgrade upgrade : ComboUpgrade.values()) {
                    String oldKey = upgrade.configKey + "-level";
                    Object raw = row.get(oldKey, 0);
                    if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() != n.intValue() || n.intValue() < 0)
                        throw new IllegalArgumentException("invalid " + oldKey);
                    levels.put(upgrade.configKey, n.intValue());
                    before.merge(upgrade.configKey, BigDecimal.valueOf(n.intValue()), BigDecimal::add);
                }
                converted.put(key, new ComboManager.ComboRecord(type, owner, levels));
            } catch (RuntimeException failure) {
                if (problems.size() < 100) problems.add(key + ": " + failure.getMessage());
            }
        }
        for (var row : converted.values()) row.levels().forEach((key, value) -> after.merge(key, BigDecimal.valueOf(value), BigDecimal::add));
        return new MigrationPreviews.Report(combos.getKeys(false).size(), converted.size(), before, after, problems);
    }
}
