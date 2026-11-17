package local.blockintegrations;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;

enum ComboType {
    SORTER("sorter", NamedTextColor.GOLD,
            Material.LIGHT_WEIGHTED_PRESSURE_PLATE, 2,
            EnumSet.of(ComboUpgrade.RANGE, ComboUpgrade.COOLDOWN)),
    VACUUM("vacuum", NamedTextColor.GRAY,
            Material.HEAVY_WEIGHTED_PRESSURE_PLATE, 2,
            EnumSet.of(ComboUpgrade.RANGE, ComboUpgrade.COOLDOWN)),
    LAUNCHER("launcher", NamedTextColor.GREEN,
            Material.SLIME_BLOCK, 2,
            EnumSet.of(ComboUpgrade.POWER, ComboUpgrade.COOLDOWN)),
    BOOSTER("booster", NamedTextColor.AQUA,
            Material.BLUE_ICE, 2,
            EnumSet.of(ComboUpgrade.POWER, ComboUpgrade.COOLDOWN)),
    FLOAT_LAUNCHER("float-launcher", NamedTextColor.WHITE,
            Material.WHITE_TERRACOTTA, 3,
            EnumSet.of(ComboUpgrade.POWER, ComboUpgrade.DURATION, ComboUpgrade.COOLDOWN)),
    HEAL("heal", NamedTextColor.LIGHT_PURPLE,
            Material.HONEY_BLOCK, 2,
            EnumSet.of(ComboUpgrade.RANGE, ComboUpgrade.POWER, ComboUpgrade.COOLDOWN)),
    HARVESTER("harvester", NamedTextColor.YELLOW,
            Material.COMPOSTER, 2,
            EnumSet.of(ComboUpgrade.RANGE, ComboUpgrade.COOLDOWN)),
    WARP_PAD("warp-pad", NamedTextColor.DARK_PURPLE,
            Material.LODESTONE, 2,
            EnumSet.of(ComboUpgrade.RANGE, ComboUpgrade.COOLDOWN)),
    XP_COLLECTOR("xp-collector", NamedTextColor.GREEN,
            Material.ENCHANTING_TABLE, 2,
            EnumSet.of(ComboUpgrade.RANGE, ComboUpgrade.POWER));

    final String configKey;
    final NamedTextColor color;
    final Material icon;
    final int height;
    private final Set<ComboUpgrade> upgrades;

    ComboType(String configKey, NamedTextColor color,
            Material icon, int height, Set<ComboUpgrade> upgrades) {
        this.configKey = configKey;
        this.color = color;
        this.icon = icon;
        this.height = height;
        this.upgrades = Set.copyOf(upgrades);
    }

    boolean supports(ComboUpgrade upgrade) {
        return upgrades.contains(upgrade);
    }

    boolean isControl(Material material) {
        return this == BOOSTER ? isBoosterIce(material) : material == icon;
    }

    boolean matches(Block control) {
        return matches(control.getType(), control.getRelative(0, -1, 0).getType(),
                control.getRelative(0, -2, 0).getType());
    }

    boolean matches(Material control, Material oneBelow, Material twoBelow) {
        return switch (this) {
            case SORTER -> control == Material.LIGHT_WEIGHTED_PRESSURE_PLATE
                    && CopperMaterials.isFullBuildingBlock(oneBelow);
            case VACUUM -> control == Material.HEAVY_WEIGHTED_PRESSURE_PLATE
                    && CopperMaterials.isFullBuildingBlock(oneBelow);
            case LAUNCHER -> control == Material.SLIME_BLOCK
                    && oneBelow == Material.REDSTONE_BLOCK;
            case BOOSTER -> isBoosterIce(control) && oneBelow == Material.REDSTONE_BLOCK;
            case FLOAT_LAUNCHER -> FloatLauncherStructure.matches(control, oneBelow, twoBelow);
            case HEAL -> control == Material.HONEY_BLOCK
                    && oneBelow == Material.REDSTONE_BLOCK;
            case HARVESTER -> control == Material.COMPOSTER && oneBelow == Material.HAY_BLOCK;
            case WARP_PAD -> control == Material.LODESTONE && oneBelow == Material.CRYING_OBSIDIAN;
            case XP_COLLECTOR -> control == Material.ENCHANTING_TABLE && oneBelow == Material.LAPIS_BLOCK;
        };
    }

    /**
     * One valid build from top to bottom for the web guide (the control block first). Copper and ice stand for "any
     * full copper block" / "any ice"; {@code ComboTypeTest} checks that every layout is accepted by {@link #matches}.
     */
    Material[] layout() {
        return switch (this) {
            case SORTER -> new Material[] {Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.COPPER_BLOCK};
            case VACUUM -> new Material[] {Material.HEAVY_WEIGHTED_PRESSURE_PLATE, Material.COPPER_BLOCK};
            case LAUNCHER -> new Material[] {Material.SLIME_BLOCK, Material.REDSTONE_BLOCK};
            case BOOSTER -> new Material[] {Material.BLUE_ICE, Material.REDSTONE_BLOCK};
            case FLOAT_LAUNCHER -> new Material[] {Material.WHITE_TERRACOTTA, Material.COPPER_BLOCK, Material.REDSTONE_BLOCK};
            case HEAL -> new Material[] {Material.HONEY_BLOCK, Material.REDSTONE_BLOCK};
            case HARVESTER -> new Material[] {Material.COMPOSTER, Material.HAY_BLOCK};
            case WARP_PAD -> new Material[] {Material.LODESTONE, Material.CRYING_OBSIDIAN};
            case XP_COLLECTOR -> new Material[] {Material.ENCHANTING_TABLE, Material.LAPIS_BLOCK};
        };
    }

    /** Whether any ice block works as control block (booster). */
    static boolean anyIce(Material material) {
        return isBoosterIce(material);
    }

    /** Language key of the display name ({@code combo.<key>.name}); the structure text is {@code combo.<key>.structure}. */
    String nameKey() {
        return "combo." + configKey + ".name";
    }

    /** Language key of an upgrade path here: the warp pad calls RANGE "distance", the XP collector POWER "capacity". */
    String upgradeNameKey(ComboUpgrade upgrade) {
        if (this == WARP_PAD && upgrade == ComboUpgrade.RANGE) return "combo-upgrade.distance";
        if (this == XP_COLLECTOR && upgrade == ComboUpgrade.POWER) return "combo-upgrade.capacity";
        return upgrade.nameKey();
    }

    static ComboType detect(Block control) {
        return Arrays.stream(values()).filter(type -> type.matches(control))
                .findFirst().orElse(null);
    }

    static ComboType byConfigKey(String key) {
        return Arrays.stream(values()).filter(type -> type.configKey.equalsIgnoreCase(key))
                .findFirst().orElse(null);
    }

    private static boolean isBoosterIce(Material material) {
        return material == Material.ICE || material == Material.PACKED_ICE
                || material == Material.BLUE_ICE;
    }
}
