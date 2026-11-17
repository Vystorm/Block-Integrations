package local.blockintegrations;

import org.bukkit.Material;

final class CopperMaterials {
    private CopperMaterials() {
    }

    static boolean isFullBuildingBlock(Material material) {
        String name = material.name();
        if (name.startsWith("WAXED_")) {
            name = name.substring("WAXED_".length());
        }
        if (name.equals("COPPER_BLOCK") || name.equals("RAW_COPPER_BLOCK")) {
            return true;
        }
        return name.matches("(?:(?:EXPOSED|WEATHERED|OXIDIZED)_)?"
                + "(?:(?:CUT|CHISELED)_)?COPPER")
                || name.matches("(?:(?:EXPOSED|WEATHERED|OXIDIZED)_)?COPPER_BULB");
    }
}
