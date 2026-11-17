package local.blockintegrations;

import org.bukkit.Material;
import org.bukkit.block.Block;

final class FloatLauncherStructure {
    private FloatLauncherStructure() {
    }

    static boolean matches(Block topBlock) {
        return matches(topBlock.getType(),
                topBlock.getRelative(0, -1, 0).getType(),
                topBlock.getRelative(0, -2, 0).getType());
    }

    static boolean matches(Material top, Material middle, Material bottom) {
        return top == Material.WHITE_TERRACOTTA
                && CopperMaterials.isFullBuildingBlock(middle)
                && bottom == Material.REDSTONE_BLOCK;
    }
}
