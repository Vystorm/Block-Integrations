package local.blockintegrations;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * The optional web page as the rest of the plugin sees it. The implementation ({@link BlocksWeb}) is the only class
 * that uses Vystorm Core's web API; it is only created when Core has it (Core 0.24.0+ with the capabilities {@code web}
 * and {@code web-open}). Without it the plugin works exactly as before.
 */
interface StructureWeb {
    /** Structures of this owner changed: open pages reload (coalesced, cheap without an open page). Server thread. */
    void changed(UUID owner);

    /** Whether Core's web platform is running (otherwise no "Open in web" buttons are shown). */
    boolean available();

    /** Opens the page in the client overlay or sends the chat login link; {@code subPath} "" or e.g. "tower/<id>". */
    void open(Player player, String subPath);

    void shutdown();
}
