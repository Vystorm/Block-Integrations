package local.blockintegrations;

import Vystorm.vystorm_core.playermenu.PlayerMenu;
import Vystorm.vystorm_core.playermenu.PlayerMenuInterface;
import Vystorm.vystorm_core.playermenu.PlayerMenuEntry;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * "Warp pads" entry in Core's player menu (sneak + right-click another player, Core 0.43.0): one click shares all of
 * the viewer's warp pads with that player or takes the access back.
 */
final class WarpPadMenuEntry {
    private WarpPadMenuEntry() {
    }

    static void register(PlayerMenuInterface menu, BlockIntegrations plugin, ComboManager combos) {
        menu.register(plugin, PlayerMenuEntry.builder("warp-pad-access")
                .priority(150)
                .visibleWhen((viewer, target) -> combos.warpPadsEnabled() && viewer.hasPermission(ComboManager.WARP_PERMISSION)
                        && !viewer.getUniqueId().equals(target.getUniqueId())
                        && !combos.warpPadsOwnedBy(viewer.getUniqueId()).isEmpty())
                .icon((viewer, target) -> {
                    boolean shared = combos.warpGuests(viewer.getUniqueId()).contains(target.getUniqueId());
                    ItemStack item = plugin.lang().name(new ItemStack(shared ? Material.LODESTONE : Material.CRYING_OBSIDIAN), viewer,
                            shared ? "combo.warp-pad.menu.shared" : "combo.warp-pad.menu.not-shared", "player", target.getName());
                    return plugin.lang().lore(item, viewer, shared ? "combo.warp-pad.menu.lore-shared" : "combo.warp-pad.menu.lore-not-shared",
                            "player", target.getName(), "pads", combos.warpPadsOwnedBy(viewer.getUniqueId()).size());
                })
                .onClick((viewer, target, click) -> {
                    boolean shared = combos.warpGuests(viewer.getUniqueId()).contains(target.getUniqueId());
                    combos.sendShareResult(viewer, target.getName(),
                            combos.shareWarpPads(viewer.getUniqueId(), target.getUniqueId(), !shared));
                    return PlayerMenu.Result.REFRESH;
                })
                .build());
    }
}
