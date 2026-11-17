package local.blockintegrations;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class TowerLootHolder implements InventoryHolder {
    final BlockKey key;
    private Inventory inventory;

    TowerLootHolder(BlockKey key) {
        this.key = key;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
