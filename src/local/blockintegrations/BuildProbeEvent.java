package local.blockintegrations;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * A test placement of the block that is already there: protection plugins (city claims, regions, worlds) cancel it
 * when the player may not build at that spot. Nothing is placed. BlockIntegrations' own place listeners ignore it.
 */
final class BuildProbeEvent extends BlockPlaceEvent {
    private BuildProbeEvent(Block block, Player player) {
        super(block, block.getState(), block.getRelative(BlockFace.DOWN),
                new ItemStack(block.getType().isItem() ? block.getType() : Material.STONE), player, true, EquipmentSlot.HAND);
    }

    /** Whether {@code player} could build at {@code block}. Main thread only. */
    static boolean mayBuild(Player player, Block block) {
        BuildProbeEvent probe = new BuildProbeEvent(block, player);
        Bukkit.getPluginManager().callEvent(probe);
        return !probe.isCancelled() && probe.canBuild();
    }
}
