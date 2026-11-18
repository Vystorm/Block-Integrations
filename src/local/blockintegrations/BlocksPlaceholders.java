package local.blockintegrations;

import Vystorm.vystorm_core._advancedMethods.placeholderHandler.PlaceholderInterface;
import Vystorm.vystorm_core._advancedMethods.placeholderHandler.PlaceholderNamespace;
import java.util.function.UnaryOperator;
import org.bukkit.plugin.Plugin;

/**
 * The values of BlockIntegrations in Core's placeholder registry, namespace {@code blocks}. The managers keep plain
 * maps, so every value is answered on the server thread only. A value of a switched-off feature is empty.
 *
 * <pre>
 * %vystorm_blocks_towers_owned%    {blocks:towers_owned}    attack towers of the player
 * %vystorm_blocks_warp_pads_max%   {blocks:warp_pads_max}   warp pad limit, -1 = none
 * combos_owned, warp_pads_owned, warp_pads_shared, furnaces_owned, combos_total, towers_total, furnaces_total
 * </pre>
 */
final class BlocksPlaceholders {
    static final String NAMESPACE = "blocks";

    /** What the values read, at call time; a manager is null before the start. */
    interface Source {
        boolean on(Feature feature);
        ComboManager combos();
        TowerManager towers();
        FurnaceManager furnaces();
    }

    private BlocksPlaceholders() {}

    static PlaceholderNamespace register(PlaceholderInterface registry, Plugin owner, Source source, UnaryOperator<String> describe) {
        PlaceholderNamespace ns = registry.register(owner, NAMESPACE);
        ns.value("combos_owned", describe.apply("placeholder.combos_owned"), viewer -> {
            ComboManager combos = source.on(Feature.BLOCKS) ? source.combos() : null;
            return combos == null ? "" : combos.combosOwnedBy(viewer.getUniqueId()).size();
        });
        ns.value("warp_pads_owned", describe.apply("placeholder.warp_pads_owned"), viewer -> {
            ComboManager combos = source.on(Feature.WARP_PADS) ? source.combos() : null;
            return combos == null ? "" : combos.warpPadsOwnedBy(viewer.getUniqueId()).size();
        });
        ns.player("warp_pads_max", describe.apply("placeholder.warp_pads_max"), player -> {
            ComboManager combos = source.on(Feature.WARP_PADS) ? source.combos() : null;
            return combos == null ? "" : combos.warpPadLimit(player);
        });
        ns.value("warp_pads_shared", describe.apply("placeholder.warp_pads_shared"), viewer -> {
            ComboManager combos = source.on(Feature.WARP_PADS) ? source.combos() : null;
            return combos == null ? "" : combos.warpGuests(viewer.getUniqueId()).size();
        });
        ns.value("towers_owned", describe.apply("placeholder.towers_owned"), viewer -> {
            TowerManager towers = source.on(Feature.TOWERS) ? source.towers() : null;
            return towers == null ? "" : towers.towersOwnedBy(viewer.getUniqueId()).size();
        });
        ns.value("furnaces_owned", describe.apply("placeholder.furnaces_owned"), viewer -> {
            FurnaceManager furnaces = source.on(Feature.FURNACES) ? source.furnaces() : null;
            return furnaces == null ? "" : furnaces.furnacesOwnedBy(viewer.getUniqueId()).size();
        });
        ns.global("combos_total", describe.apply("placeholder.combos_total"), () -> {
            ComboManager combos = source.on(Feature.BLOCKS) ? source.combos() : null;
            return combos == null ? "" : combos.comboCount();
        });
        ns.global("towers_total", describe.apply("placeholder.towers_total"), () -> {
            TowerManager towers = source.on(Feature.TOWERS) ? source.towers() : null;
            return towers == null ? "" : towers.towerCount();
        });
        ns.global("furnaces_total", describe.apply("placeholder.furnaces_total"), () -> {
            FurnaceManager furnaces = source.on(Feature.FURNACES) ? source.furnaces() : null;
            return furnaces == null ? "" : furnaces.furnaceCount();
        });
        return ns;
    }
}
