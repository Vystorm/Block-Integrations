package local.blockintegrations;

import java.util.Arrays;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;

enum TowerType {
    ZOMBIE_STARTER("zombie-starter", NamedTextColor.DARK_GREEN,
            Material.ZOMBIE_HEAD, Material.CRAFTING_TABLE, Material.COBBLESTONE,
            Material.OAK_PLANKS, AttackStyle.PRECISION, false,
            2.0D, 8.0D, 30),
    PLAYER_STARTER("player-starter", NamedTextColor.AQUA,
            Material.PLAYER_HEAD, Material.BARREL, Material.STONE,
            Material.COBBLESTONE, AttackStyle.PRECISION, false,
            3.0D, 9.0D, 26),
    ARCHER("archer", NamedTextColor.GREEN,
            Material.SKELETON_SKULL, Material.FLETCHING_TABLE, Material.COPPER_BLOCK,
            Material.STONE_BRICKS, AttackStyle.PRECISION, true,
            4.0D, 14.0D, 20),
    CANNON("cannon", NamedTextColor.GOLD,
            Material.CREEPER_HEAD, Material.DISPENSER, Material.BLAST_FURNACE,
            Material.OBSIDIAN, AttackStyle.AREA, true,
            8.0D, 11.0D, 40),
    FLAME("flame", NamedTextColor.RED,
            Material.WITHER_SKELETON_SKULL, Material.MAGMA_BLOCK, Material.NETHER_BRICKS,
            Material.POLISHED_BLACKSTONE_BRICKS, AttackStyle.FIRE, true,
            5.0D, 10.0D, 24),
    PIGLIN("piglin", NamedTextColor.YELLOW,
            Material.PIGLIN_HEAD, Material.SMITHING_TABLE, Material.GOLD_BLOCK,
            Material.POLISHED_BLACKSTONE_BRICKS, AttackStyle.PRECISION, true,
            6.0D, 12.0D, 16),
    DRAGON("dragon", NamedTextColor.LIGHT_PURPLE,
            Material.DRAGON_HEAD, Material.RESPAWN_ANCHOR, Material.PURPUR_BLOCK,
            Material.END_STONE_BRICKS, AttackStyle.AREA, true,
            12.0D, 16.0D, 50);

    final String configKey;
    final NamedTextColor color;
    final Material core;
    final Material pedestal;
    final Material foundation;
    final Material ring;
    final AttackStyle attackStyle;
    final boolean upgradeable;
    final double defaultDamage;
    final double defaultRange;
    final int defaultCooldownTicks;

    TowerType(String configKey, NamedTextColor color,
            Material core, Material pedestal, Material foundation, Material ring,
            AttackStyle attackStyle, boolean upgradeable,
            double defaultDamage, double defaultRange,
            int defaultCooldownTicks) {
        this.configKey = configKey;
        this.color = color;
        this.core = core;
        this.pedestal = pedestal;
        this.foundation = foundation;
        this.ring = ring;
        this.attackStyle = attackStyle;
        this.upgradeable = upgradeable;
        this.defaultDamage = defaultDamage;
        this.defaultRange = defaultRange;
        this.defaultCooldownTicks = defaultCooldownTicks;
    }

    boolean matches(Block coreBlock) {
        if (coreBlock.getType() != core
                || coreBlock.getRelative(0, -1, 0).getType() != pedestal
                || !acceptsFoundation(coreBlock.getRelative(0, -2, 0).getType())) {
            return false;
        }
        return coreBlock.getRelative(1, -2, 0).getType() == ring
                && coreBlock.getRelative(-1, -2, 0).getType() == ring
                && coreBlock.getRelative(0, -2, 1).getType() == ring
                && coreBlock.getRelative(0, -2, -1).getType() == ring;
    }

    /** Language key of the display name ({@code tower.<key>.name}). */
    String nameKey() {
        return "tower." + configKey + ".name";
    }

    boolean acceptsFoundation(Material material) {
        if (this != ARCHER) {
            return material == foundation;
        }
        return CopperMaterials.isFullBuildingBlock(material);
    }

    static TowerType detect(Block coreBlock) {
        return Arrays.stream(values()).filter(type -> type.matches(coreBlock)).findFirst().orElse(null);
    }

    static boolean isCore(Material material) {
        return Arrays.stream(values()).anyMatch(type -> type.core == material);
    }

    static TowerType byCore(Material material) {
        return Arrays.stream(values()).filter(type -> type.core == material)
                .findFirst().orElse(null);
    }

    static TowerType byConfigKey(String key) {
        return Arrays.stream(values()).filter(type -> type.configKey.equalsIgnoreCase(key))
                .findFirst().orElse(null);
    }

    static String friendly(Material material) {
        String[] words = material.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    enum AttackStyle {
        PRECISION,
        AREA,
        FIRE
    }
}
