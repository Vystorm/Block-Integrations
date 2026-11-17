package local.blockintegrations;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;

/** Main-thread, read-only container checks for both halves of a target. No synthetic interaction events. */
final class ChestAccess {
    private ChestAccess() {}

    static boolean allowed(Player player, Inventory inventory) {
        List<Location> locations;
        if (inventory instanceof DoubleChestInventory pair) {
            Location left = pair.getLeftSide().getLocation(), right = pair.getRightSide().getLocation();
            if (left == null || right == null) return false;
            locations = List.of(left, right);
        } else {
            Location location = inventory.getLocation();
            if (location == null) return false;
            locations = List.of(location);
        }
        for (Location at : locations) {
            if (at.getWorld() == null || at.getWorld() != player.getWorld()
                    || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return false;
            if (!(at.getBlock().getState() instanceof Chest chest) || chest.isLocked()) return false;
            if (!cityAllows(player.getUniqueId(), at)) return false;
        }
        return true;
    }

    /** Optional CityClaim public service; unavailable or failed provider denies rather than bypassing its claims. */
    static boolean cityAllows(UUID actor, Location at) {
        var cityPlugin = Bukkit.getPluginManager().getPlugin("VystormCityClaim");
        if (cityPlugin == null) return true;
        if (!cityPlugin.isEnabled()) return false;
        try {
            for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
                if (!service.getName().equals("at.esoren.stadtclaim.VystormCityApi")) continue;
                Object provider = Bukkit.getServicesManager().load(service);
                if (provider == null) return false;
                Method allowed = service.getMethod("allowed", UUID.class, Location.class, String.class);
                return Boolean.TRUE.equals(allowed.invoke(provider, actor, at, "CONTAINER"));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return false; }
        return false;
    }
}
