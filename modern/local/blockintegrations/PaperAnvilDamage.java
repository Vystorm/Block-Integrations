package local.blockintegrations;

import org.bukkit.event.*;
import com.destroystokyo.paper.event.block.AnvilDamagedEvent;

/** Optional Paper listener kept out of the common listener's reflected method signatures. */
public final class PaperAnvilDamage implements Listener {
    private final AnvilManager manager;
    public PaperAnvilDamage(AnvilManager manager){this.manager=manager;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(AnvilDamagedEvent event){if(manager.protectsDamage(event.getView()))event.setCancelled(true);}
}
