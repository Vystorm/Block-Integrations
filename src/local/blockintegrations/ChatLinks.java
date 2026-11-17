package local.blockintegrations;

import Vystorm.vystorm_core._uiMethods.chat.ChatActions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Clickable chat actions (Vystorm Core {@link ChatActions}) for tower, furnace and combination messages. */
final class ChatLinks {
    /** Blocks within which a chat button may still open a tower, furnace or combination (no remote loot access). */
    static final double REMOTE_RANGE = 64.0D;

    private ChatLinks() {}

    /** Block corner location, or {@code null} if the world is not loaded. */
    static Location location(BlockKey key) {
        World world = Bukkit.getWorld(key.worldId());
        return world == null ? null : new Location(world, key.x(), key.y(), key.z());
    }

    /**
     * Checks the distance when the button is clicked; otherwise sends {@code tooFarKey} with the (for permitted players
     * teleport-clickable) coordinates.
     */
    static boolean near(BlockIntegrations plugin, Player player, BlockKey key, String tooFarKey) {
        Location target = location(key);
        if (target != null && player.getWorld().equals(target.getWorld())
                && player.getLocation().distanceSquared(target.clone().add(0.5D, 0.5D, 0.5D)) <= REMOTE_RANGE * REMOTE_RANGE) {
            return true;
        }
        var message = plugin.lang().component(player, tooFarKey);
        if (target != null) message = message.append(plugin.lang().component(player, "chat.location"))
                .append(plugin.core().message().chat().location(target));
        plugin.core().compat().platform().sendMessage(player, message);
        return false;
    }
}
