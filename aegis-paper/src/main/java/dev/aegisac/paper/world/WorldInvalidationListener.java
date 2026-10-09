package dev.aegisac.paper.world;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
/** Conservative world-wide invalidation; TTL and outgoing block/chunk traffic cover silent plugin edits. */
public final class WorldInvalidationListener implements Listener {
    private final WorldCaptureService captures;
    public WorldInvalidationListener(WorldCaptureService captures) { this.captures=captures; }
    private void block(BlockEvent event) { captures.invalidate(event.getBlock().getWorld().getUID()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e) { block(e); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void placed(BlockPlaceEvent e) { block(e); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void physics(BlockPhysicsEvent e) { block(e); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void flow(BlockFromToEvent e) { block(e); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void extend(BlockPistonExtendEvent e) { block(e); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e) { block(e); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void grow(BlockGrowEvent e) { block(e); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void form(BlockFormEvent e) { block(e); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void fade(BlockFadeEvent e) { block(e); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explode(BlockExplodeEvent e) { block(e); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explode(EntityExplodeEvent e) { captures.invalidate(e.getLocation().getWorld().getUID()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void change(EntityChangeBlockEvent e) { captures.invalidate(e.getBlock().getWorld().getUID()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void tree(StructureGrowEvent e) { captures.invalidate(e.getWorld().getUID()); }
    @EventHandler(priority=EventPriority.MONITOR) public void load(ChunkLoadEvent e) { captures.invalidate(e.getWorld().getUID()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void unload(ChunkUnloadEvent e) { captures.invalidate(e.getWorld().getUID()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void unload(WorldUnloadEvent e) { captures.invalidate(e.getWorld().getUID()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e) {
        captures.resetPlayer(e.getPlayer().getUniqueId(),e.getTo().getWorld().getUID());
    }
    @EventHandler(priority=EventPriority.MONITOR) public void respawn(PlayerRespawnEvent e) {
        captures.resetPlayer(e.getPlayer().getUniqueId(),e.getRespawnLocation().getWorld().getUID());
    }
    @EventHandler(priority=EventPriority.MONITOR) public void world(PlayerChangedWorldEvent e) {
        captures.resetPlayer(e.getPlayer().getUniqueId(),e.getPlayer().getWorld().getUID());
    }
}
