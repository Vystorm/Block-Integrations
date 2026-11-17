package local.blockintegrations;

import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Protection/integration veto before a trap changes velocity, applies an effect or deals damage. */
public final class TrapTriggerEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Block trap;
    private final LivingEntity target;
    private final UUID owner;
    private boolean cancelled;
    public TrapTriggerEvent(Block trap, LivingEntity target, UUID owner) { this.trap = trap; this.target = target; this.owner = owner; }
    public Block getTrap() { return trap; }
    public LivingEntity getTarget() { return target; }
    public UUID getOwner() { return owner; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
