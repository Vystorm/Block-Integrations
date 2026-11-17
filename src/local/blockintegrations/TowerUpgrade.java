package local.blockintegrations;

enum TowerUpgrade {
    DAMAGE("damage", 10),
    RANGE("range", 10),
    SPEED("speed", 10),
    LOOTING("looting", 3);

    final String configKey;
    final int maximumLevel;

    TowerUpgrade(String configKey, int maximumLevel) {
        this.configKey = configKey;
        this.maximumLevel = maximumLevel;
    }

    /** Language key of the display name. */
    String nameKey() {
        return "tower-upgrade." + configKey;
    }
}
