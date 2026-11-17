package local.blockintegrations;

import Vystorm.vystorm_core.API;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Composter on a hay bale: harvests ripe crops around it, replants them with a seed from the harvest (or from its
 * container) and puts the yield into a container touching the composter or hay bale. Stays inside its own city.
 */
final class Harvester {
    static final int MAX_PER_RUN = 64;
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN};

    private Harvester() {
    }

    /** Seed or crop item that replants a ripe crop; null for blocks that are not replanted crops. */
    static Material seed(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            default -> null;
        };
    }

    /** A replantable crop at its final growth stage. */
    static boolean ripe(BlockData data) {
        return seed(data.getMaterial()) != null && data instanceof Ageable age && age.getAge() >= age.getMaximumAge();
    }

    /** Removes one {@code seed} from the drops; true when one was found. */
    static boolean takeSeed(List<ItemStack> drops, Material seed) {
        for (ItemStack drop : drops) {
            if (drop != null && drop.getType() == seed && drop.getAmount() > 0) {
                drop.setAmount(drop.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    /** Melon or pumpkin grown by an attached stem pointing at it; a placed decoration block stays. */
    static boolean grownFruit(Block block) {
        Material stem = switch (block.getType()) {
            case MELON -> Material.ATTACHED_MELON_STEM;
            case PUMPKIN -> Material.ATTACHED_PUMPKIN_STEM;
            default -> null;
        };
        if (stem == null) return false;
        for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block neighbour = block.getRelative(face);
            if (neighbour.getType() == stem && neighbour.getBlockData() instanceof Directional directional
                    && directional.getFacing() == face.getOppositeFace()) return true;
        }
        return false;
    }

    /** Sugar cane or bamboo segment standing on the same plant; the bottom segment keeps growing. */
    static boolean upperStalk(Block block) {
        Material type = block.getType();
        return (type == Material.SUGAR_CANE || type == Material.BAMBOO) && block.getRelative(BlockFace.DOWN).getType() == type;
    }

    /** No empty storage slot left; partly filled stacks do not count as room. */
    static boolean noFreeSlot(ItemStack[] contents) {
        if (contents == null || contents.length == 0) return false;
        for (ItemStack item : contents) {
            Material type = item == null ? Material.AIR : item.getType();
            if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR || item.getAmount() <= 0) return false;
        }
        return true;
    }

    /**
     * One harvesting pass; returns the number of harvested blocks, or -1 when paused because the touching container
     * has no free slot ({@code pauseWhenFull}), so the ripe crops wait instead of piling up as dropped items.
     * Main thread only.
     */
    static int run(API core, Block composter, int radius, boolean particles, boolean pauseWhenFull, HarvestProfile profile, Particles effects) {
        if (profile == HarvestProfile.PAUSED) return 0;
        World world = composter.getWorld();
        Inventory output = container(core, composter);
        if (pauseWhenFull && output != null && noFreeSlot(output.getStorageContents())) return -1;
        List<ItemStack> yield = new ArrayList<>();
        List<Unseeded> unseeded = new ArrayList<>();
        int harvested = 0;
        Location origin = composter.getLocation();
        var city = core.area().regions().top(origin, "city").map(region -> region.id());
        // Highest first, so upper sugar cane segments go before the ones below them.
        for (int dy = 1; dy >= -2 && harvested < MAX_PER_RUN; dy--) {
            for (int dx = -radius; dx <= radius && harvested < MAX_PER_RUN; dx++) {
                for (int dz = -radius; dz <= radius && harvested < MAX_PER_RUN; dz++) {
                    int x = composter.getX() + dx, y = composter.getY() + dy, z = composter.getZ() + dz;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (!profile.accepts(block.getType())) continue;
                    BlockData data = block.getBlockData();
                    boolean crop = ripe(data);
                    if (!crop && !grownFruit(block) && !upperStalk(block)) continue;
                    if (!core.area().regions().top(block.getLocation(), "city").map(region -> region.id()).equals(city)) continue;
                    List<ItemStack> drops = new ArrayList<>(block.getDrops());
                    if (crop && takeSeed(drops, seed(data.getMaterial()))) replant(block, data);
                    else if (crop) { block.setType(Material.AIR, true); unseeded.add(new Unseeded(block, data)); }
                    else block.setType(Material.AIR, true);
                    for (ItemStack drop : drops) if (drop != null && drop.getAmount() > 0) yield.add(drop);
                    if (particles && harvested < 12) effects.spawn(block.getLocation().add(.5, .5, .5));
                    harvested++;
                }
            }
        }
        if (harvested == 0) return 0;
        // A crop whose own drops had no seed (wheat sometimes) takes one from this pass's yield or the container.
        for (Unseeded open : unseeded) {
            Material seed = seed(open.ripe().getMaterial());
            if (seed != null && (takeSeed(yield, seed) || output != null && output.containsAtLeast(new ItemStack(seed), 1)
                    && output.removeItem(new ItemStack(seed)).isEmpty())) {
                // Replant from the old block data so a cocoa pod keeps facing its log.
                if (open.block().getType().isAir()) replant(open.block(), open.ripe());
            }
        }
        Location drop = composter.getLocation().add(.5, 1.1, .5);
        for (ItemStack item : yield) {
            if (item.getAmount() <= 0) continue;
            var leftover = output == null ? java.util.Map.of(0, item) : output.addItem(item);
            for (ItemStack rest : leftover.values()) world.dropItem(drop, rest);
        }
        world.playSound(drop, Sound.BLOCK_COMPOSTER_FILL_SUCCESS, .7f, 1.1f);
        return harvested;
    }

    private record Unseeded(Block block, BlockData ripe) {
    }

    /** Same crop again at age 0. */
    private static void replant(Block block, BlockData ripe) {
        Ageable young = (Ageable) ripe.clone();
        young.setAge(0);
        block.setBlockData(young, true);
    }

    /** First container touching the composter (sides) or its hay bale (sides and below). */
    static Inventory container(API core, Block composter) {
        Block hay = composter.getRelative(BlockFace.DOWN);
        for (Block base : new Block[] {composter, hay}) {
            for (BlockFace face : SIDES) {
                if (base == composter && face == BlockFace.DOWN) continue;
                if (core.compat().platform().getState(base.getRelative(face), false) instanceof Container container) return container.getInventory();
            }
        }
        return null;
    }

    /** Particle hook so the pass can run without Core in tests. */
    interface Particles {
        void spawn(Location at);

        static Particles core(BlockIntegrations plugin) {
            return at -> plugin.core().particle().spawn(Particle.HAPPY_VILLAGER, at, 3, .3, .3, .3, 0, null);
        }
    }
}
