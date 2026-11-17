package local.blockintegrations;

import java.util.Arrays;
import org.bukkit.inventory.ItemStack;

final class Tower {
    final BlockKey key;
    final TowerType type;
    private int damageLevel;
    private int rangeLevel;
    private int speedLevel;
    private int lootingLevel;
    private int storedExperience;
    private ItemStack[] lootContents;
    long nextAttackTick;
    TowerControls.TargetProfile targetProfile = TowerControls.TargetProfile.ALL;
    TowerControls.RedstoneMode redstoneMode = TowerControls.RedstoneMode.ALWAYS;
    final TowerControls controls = new TowerControls();
    /** Erbauer; {@code null} bei alten Türmen – der erste berechtigte Nutzer übernimmt ihn. */
    private java.util.UUID owner;

    Tower(BlockKey key, TowerType type, int damageLevel, int rangeLevel, int speedLevel,
            int maximumLevel) {
        this(key, type, damageLevel, rangeLevel, speedLevel, 0, maximumLevel, 0,
                new ItemStack[0]);
    }

    Tower(BlockKey key, TowerType type, int damageLevel, int rangeLevel, int speedLevel,
            int maximumLevel, int storedExperience, ItemStack[] lootContents) {
        this(key, type, damageLevel, rangeLevel, speedLevel, 0, maximumLevel,
                storedExperience, lootContents);
    }

    Tower(BlockKey key, TowerType type, int damageLevel, int rangeLevel, int speedLevel,
            int lootingLevel, int maximumLevel, int storedExperience,
            ItemStack[] lootContents) {
        this.key = key;
        this.type = type;
        this.damageLevel = type.upgradeable ? clamp(damageLevel, maximumLevel) : 0;
        this.rangeLevel = type.upgradeable ? clamp(rangeLevel, maximumLevel) : 0;
        this.speedLevel = type.upgradeable ? clamp(speedLevel, maximumLevel) : 0;
        this.lootingLevel = type.upgradeable
                ? clamp(lootingLevel, Math.min(maximumLevel, TowerUpgrade.LOOTING.maximumLevel))
                : 0;
        this.storedExperience = Math.max(0, storedExperience);
        setLootContents(lootContents);
    }

    java.util.UUID owner() {
        return owner;
    }

    void owner(java.util.UUID owner) {
        this.owner = owner;
    }

    int level(TowerUpgrade upgrade) {
        return switch (upgrade) {
            case DAMAGE -> damageLevel;
            case RANGE -> rangeLevel;
            case SPEED -> speedLevel;
            case LOOTING -> lootingLevel;
        };
    }

    void upgrade(TowerUpgrade upgrade) {
        if (!type.upgradeable) {
            return;
        }
        switch (upgrade) {
            case DAMAGE -> damageLevel++;
            case RANGE -> rangeLevel++;
            case SPEED -> speedLevel++;
            case LOOTING -> {
                if (lootingLevel < TowerUpgrade.LOOTING.maximumLevel) {
                    lootingLevel++;
                }
            }
        }
    }

    int storedExperience() {
        return storedExperience;
    }

    void addStoredExperience(int amount) {
        if (amount <= 0) {
            return;
        }
        storedExperience = (int) Math.min(Integer.MAX_VALUE,
                (long) storedExperience + amount);
    }

    int withdrawStoredExperience() {
        int payout = storedExperience;
        storedExperience = 0;
        return payout;
    }

    ItemStack[] lootContents(int size) {
        return Arrays.copyOf(lootContents, Math.max(0, size));
    }

    void setLootContents(ItemStack[] contents) {
        if (contents == null) {
            lootContents = new ItemStack[0];
            return;
        }
        lootContents = Arrays.copyOf(contents, contents.length);
    }

    private static int clamp(int value, int maximumLevel) {
        return Math.max(0, Math.min(maximumLevel, value));
    }
}
