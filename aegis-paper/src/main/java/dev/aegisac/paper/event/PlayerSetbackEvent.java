package dev.aegisac.paper.event;
import dev.aegisac.common.output.OutputRecord;
import dev.aegisac.api.output.ViolationSnapshot;
import org.bukkit.event.*;
/** Synchronous owner-thread event. Values are immutable; no live Player escapes. */
public final class PlayerSetbackEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final OutputRecord record; private final ViolationSnapshot violations;
    public PlayerSetbackEvent(OutputRecord record,ViolationSnapshot violations) { this.record=record; this.violations=violations; }
    public OutputRecord record() { return record; }
    public ViolationSnapshot violations() { return violations; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
    private boolean cancelled;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean value) { cancelled=value; }
}
