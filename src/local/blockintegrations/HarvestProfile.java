package local.blockintegrations;

import org.bukkit.Material;

/** Per-machine selection; maturity and protection checks still belong to the harvesting pass. */
enum HarvestProfile {
    ALL("all"), REPLANTED("replanted"), FRUIT("fruit"), STALKS("stalks"), PAUSED("paused");

    final String key;

    HarvestProfile(String key) {
        this.key = key;
    }

    String nameKey() {
        return "harvest-profile.names." + key;
    }

    boolean accepts(Material material) {
        boolean replanted = Harvester.seed(material) != null;
        boolean fruit = material == Material.MELON || material == Material.PUMPKIN;
        boolean stalk = material == Material.SUGAR_CANE || material == Material.BAMBOO;
        return switch (this) {
            case ALL -> replanted || fruit || stalk;
            case REPLANTED -> replanted;
            case FRUIT -> fruit;
            case STALKS -> stalk;
            case PAUSED -> false;
        };
    }

    static HarvestProfile parse(String key) {
        for (HarvestProfile profile : values()) if (profile.key.equals(key)) return profile;
        return ALL;
    }
}
