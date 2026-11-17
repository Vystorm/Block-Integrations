package local.blockintegrations;

import Vystorm.vystorm_core.commands.Arg;
import Vystorm.vystorm_core.commands.CommandSpec;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * {@code /blockintegrations} ({@code /bi}) as a Vystorm Core command tree (Core 0.45.0+). Names, root aliases, usage
 * and help come from {@code commands.blockintegrations.*} in the language files ({@code /bi tuerme liste},
 * {@code /bi 塔 列表}); the English names always work. The handlers receive the same English argument arrays the old
 * plugin.yml executor saw, so the managers' command methods are unchanged.
 */
final class BlockCommands {
    static final String USE = "blockintegrations.use";
    static final String COMBOS = "blockintegrations.combos";
    static final String TOWERS = "blockintegrations.towers";
    static final String FURNACES = "blockintegrations.furnaces";
    static final String WORKSTATIONS = "blockintegrations.workstations";
    static final String ADMIN = "blockintegrations.admin";

    private BlockCommands() {
    }

    /** The tree; {@code p} may be {@code null} (or a mock) in tests that only inspect names, help and routing. */
    static CommandSpec spec(BlockIntegrations p) {
        return CommandSpec.command("blockintegrations")
                .permission(USE)
                .aliases("blockint", "bi")
                .executes(ctx -> p.rootCommand(ctx.sender()))
                .sub("menu", s -> s.playerOnly().executes(ctx -> p.menuCommand(ctx.player())))
                .sub("dashboard", s -> s.playerOnly().executes(ctx -> p.dashboardCommand(ctx.player())))
                .sub("web", s -> s.playerOnly()
                        .arg(Arg.word("page").optional().suggests((ctx, prefix) -> List.of(ctx.valueName("guide"))))
                        .executes(ctx -> p.webCommand(ctx.player(), ctx.keyword(ctx.get("page"), "guide") != null)))
                .sub("status", s -> s.permission(ADMIN).executes(ctx -> p.statusCommand(ctx.sender())))
                .sub("combos", s -> s.permission(COMBOS).executes(ctx -> p.combosCommand(ctx.sender()))
                        .sub("mine", t -> t.playerOnly().executes(ctx -> p.myCombosCommand(ctx.player()))))
                .sub("traps", s -> s.permission(TrapManager.PERMISSION).executes(ctx -> p.trapsCommand(ctx.sender())))
                .sub("towers", s -> s.permission(TOWERS)
                        .executes(ctx -> p.towersCommand(ctx.sender(), args("towers")))
                        .sub("list", t -> t.arg(Arg.playerName("player").optional().suggests((ctx, prefix) ->
                                        ctx.sender().hasPermission(ADMIN) ? onlinePlayers() : List.of()))
                                .executes(ctx -> p.towersCommand(ctx.sender(), args("towers", "list", ctx.get("player")))))
                        .sub("near", t -> t.playerOnly().executes(ctx -> p.towersCommand(ctx.sender(), args("towers", "near"))))
                        .sub("remove", t -> t.permission(ADMIN).playerOnly()
                                .executes(ctx -> p.towersCommand(ctx.sender(), args("towers", "remove")))))
                .sub("furnaces", s -> s.permission(FURNACES).executes(ctx -> p.furnacesCommand(ctx.sender())))
                .sub("workstations", s -> s.permission(WORKSTATIONS).executes(ctx -> p.workstationsCommand(ctx.sender())))
                .sub("warppads", s -> s.permission(ComboManager.WARP_PERMISSION).playerOnly()
                        .executes(ctx -> p.warpPadsCommand(ctx.player(), args("warppads")))
                        .sub("list", t -> t.executes(ctx -> p.warpPadsCommand(ctx.player(), args("warppads", "list"))))
                        .sub("share", t -> t.arg(Arg.playerName("player"))
                                .executes(ctx -> p.warpPadsCommand(ctx.player(), args("warppads", "share", ctx.get("player")))))
                        .sub("unshare", t -> t.arg(Arg.playerName("player"))
                                .executes(ctx -> p.warpPadsCommand(ctx.player(), args("warppads", "unshare", ctx.get("player"))))))
                .sub("sortlock", s -> s.playerOnly()
                        .arg(Arg.word("slot").optional().suggests((ctx, prefix) -> slots()))
                        .executes(ctx -> p.sortLockCommand(ctx.player(), args("sortlock", ctx.get("slot"))))
                        .sub("list", t -> t.executes(ctx -> p.sortLockCommand(ctx.player(), args("sortlock", "list"))))
                        .sub("clear", t -> t.executes(ctx -> p.sortLockCommand(ctx.player(), args("sortlock", "clear"))))
                        .sub("item", t -> t.executes(ctx -> p.sortLockCommand(ctx.player(), args("sortlock", "item"))))
                        .sub("items", t -> t.executes(ctx -> p.sortLockCommand(ctx.player(), args("sortlock", "items"))))
                        .sub("clearitems", t -> t.executes(ctx -> p.sortLockCommand(ctx.player(), args("sortlock", "clearitems")))))
                .sub("chestpriority", s -> s.playerOnly()
                        .executes(ctx -> p.chestPriorityCommand(ctx.player(), args("chestpriority")))
                        .sub("add", t -> chest(t, p, "add"))
                        .sub("overflow", t -> chest(t, p, "overflow"))
                        .sub("remove", t -> chest(t, p, "remove"))
                        .sub("list", t -> chest(t, p, "list"))
                        .sub("clear", t -> chest(t, p, "clear")))
                .sub("reload", s -> s.permission(ADMIN).executes(ctx -> p.reloadCommand(ctx.sender())))
                .build();
    }

    private static void chest(CommandSpec.Builder node, BlockIntegrations p, String action) {
        node.executes(ctx -> p.chestPriorityCommand(ctx.player(), args("chestpriority", action)));
    }

    /** The old executor's argument array; a trailing {@code null} (left-out optional argument) is dropped. */
    static String[] args(String... parts) {
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) if (part != null) out.add(part);
        return out.toArray(String[]::new);
    }

    private static List<String> slots() {
        List<String> choices = new ArrayList<>();
        for (int slot = SortSlotLocks.FIRST_SLOT; slot <= SortSlotLocks.LAST_SLOT; slot++) choices.add(Integer.toString(slot));
        return choices;
    }

    private static List<String> onlinePlayers() {
        try {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        } catch (RuntimeException noServer) {
            return List.of();
        }
    }

}
