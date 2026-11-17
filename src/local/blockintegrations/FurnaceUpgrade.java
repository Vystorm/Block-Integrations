package local.blockintegrations;

enum FurnaceUpgrade {
    SPEED("speed", 10),
    FUEL("fuel", 10),
    YIELD("yield", 10),
    MULTISMELT("multismelt", 10),
    XP_BOOST("xp-boost", 5);

    final String configKey;
    final int maximumLevel;

    FurnaceUpgrade(String configKey, int maximumLevel) {
        this.configKey = configKey;
        this.maximumLevel = maximumLevel;
    }

    /** Language key of the display name. */
    String nameKey() {
        return "furnace-upgrade." + configKey;
    }
}
