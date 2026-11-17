package local.blockintegrations;

enum ComboUpgrade {
    RANGE("range"),
    POWER("power"),
    DURATION("duration"),
    COOLDOWN("cooldown");

    final String configKey;

    ComboUpgrade(String configKey) {
        this.configKey = configKey;
    }

    /** Language key of the display name. */
    String nameKey() {
        return "combo-upgrade." + configKey;
    }
}
