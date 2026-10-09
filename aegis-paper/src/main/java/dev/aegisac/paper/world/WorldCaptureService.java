package dev.aegisac.paper.world;
import dev.aegisac.common.config.PhysicsSettings;
import dev.aegisac.common.player.PlayerData;
import org.bukkit.Server;
import java.util.*;
import java.util.function.Consumer;
/** Owning-thread round-robin scheduling; no task/player allocation for each movement packet. */
public final class WorldCaptureService implements AutoCloseable {
    private static final class Entry {
        final PlayerData data; final org.bukkit.entity.Player address; volatile UUID world;
        final java.util.concurrent.atomic.AtomicBoolean pending=new java.util.concurrent.atomic.AtomicBoolean();
        Entry(PlayerData data,UUID world,org.bukkit.entity.Player address) { this.data=data; this.world=world; this.address=address; }
        PlayerData data() { return data; } UUID world() { return world; }
    }
    private final Map<UUID,Entry> entries=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<UUID> order=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final dev.aegisac.paper.scheduler.PlatformScheduler scheduler;
    private final Server server;
    private final PhysicsSettings settings;
    private final WorldCapture capture;
    private final Consumer<RuntimeException> failures;
    private final dev.aegisac.common.check.ServerTickHealth health=new dev.aegisac.common.check.ServerTickHealth();
    public dev.aegisac.common.check.ServerTickHealth.Snapshot health() { return scheduler!=null&&scheduler.mode()==dev.aegisac.paper.scheduler.PlatformScheduler.Mode.FOLIA?dev.aegisac.common.check.ServerTickHealth.Snapshot.unknown():health.snapshot(); }
    private final java.util.concurrent.atomic.AtomicLong captured=new java.util.concurrent.atomic.AtomicLong(),failed=new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicBoolean reported=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean closed;
    public WorldCaptureService(Server server,PhysicsSettings settings,Consumer<RuntimeException> failures) {
        this(server,settings,failures,null);
    }
    public WorldCaptureService(Server server,PhysicsSettings settings,Consumer<RuntimeException> failures,dev.aegisac.paper.scheduler.PlatformScheduler scheduler) {
        this.server=server; this.settings=settings; this.failures=failures; this.scheduler=scheduler;
        capture=new WorldCapture(settings,scheduler==null?WorldAccess.primary(server):WorldAccess.scheduled(scheduler));
    }
    public synchronized void attach(UUID uuid,UUID world,PlayerData data) {
        if(scheduler!=null) throw new IllegalStateException("Scheduled captures require an entity address");
        checkOwner(); register(uuid,new Entry(data,world,null));
    }
    public synchronized void attach(org.bukkit.entity.Player player,PlayerData data) {
        if(scheduler==null) checkOwner(); else scheduler.requireEntity(player);
        register(player.getUniqueId(),new Entry(data,player.getWorld().getUID(),player));
    }
    private void register(UUID uuid,Entry entry) {
        if(closed) throw new IllegalStateException("Capture service closed");
        if(entries.put(uuid,entry)==null) order.add(uuid);
    }
    public synchronized void detach(UUID uuid) { entries.remove(uuid); order.remove(uuid); }
    public void invalidate(UUID world) {
        entries.values().forEach(e->{ if(e.world().equals(world)) e.data().world().invalidate(); });
    }
    public void resetPlayer(UUID uuid,UUID world) {
        Entry e=entries.get(uuid); if(e!=null) { e.data().markGap(); e.world=world; }
    }
    public void tick() {
        checkOwner(); if(closed) return;
        health.tick(System.nanoTime());
        int count=Math.min(order.size(),settings.capturesPerTick());
        for(int i=0;i<count;i++) {
            UUID uuid; Entry entry;
            // Registration/retirement and queue membership change under the same short lock.
            synchronized(this) { uuid=order.poll(); if(uuid==null) break; entry=entries.get(uuid); if(entry==null) continue; order.add(uuid); }
            if(scheduler==null) { capture(uuid,entry,server.getPlayer(uuid)); continue; }
            if(!entry.pending.compareAndSet(false,true)) continue;
            try {
                scheduler.entity(entry.address,1,0,()->!closed&&entries.get(uuid)==entry,()->{
                    try { capture(uuid,entry,entry.address); } finally { entry.pending.set(false); }
                },()->entry.pending.set(false));
            } catch(java.util.concurrent.RejectedExecutionException|org.bukkit.plugin.IllegalPluginAccessException unavailable) {
                entry.pending.set(false); entry.data().world().invalidate(); failed.incrementAndGet();
            }
        }
    }
    private void capture(UUID uuid,Entry entry,org.bukkit.entity.Player player) {
            if(closed||entries.get(uuid)!=entry||player==null||!player.isOnline()) return;
            var view=entry.data().world(); var before=view.read();
            if(before.closed()) return;
            try {
                var bypasses=new java.util.HashSet<String>();
                if(player.hasPermission("aegisac.bypass")) bypasses.add("*");
                for(var id:dev.aegisac.common.check.CheckId.values())
                    if(player.hasPermission("aegisac.bypass."+id.name().toLowerCase(java.util.Locale.ROOT))) bypasses.add(id.name());
                for(var id:dev.aegisac.common.combat.CombatId.values())
                    if(player.hasPermission("aegisac.bypass."+id.name().toLowerCase(java.util.Locale.ROOT))) bypasses.add(id.name());
                for(var id:dev.aegisac.common.guard.GuardId.values())
                    if(id.implemented&&player.hasPermission("aegisac.bypass."+id.name().toLowerCase(java.util.Locale.ROOT))) bypasses.add(id.name());
                var blockRange=player.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE);
                entry.data().guardOwnerObservation(blockRange==null?null:new dev.aegisac.common.guard.GuardOwnerObservation(
                        System.nanoTime(),player.getEyeHeight(),blockRange.getValue()));
                var range=player.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE);
                entry.data().combatOwnerObservation(range==null?null:new dev.aegisac.common.combat.CombatOwnerObservation(
                        System.nanoTime(),player.getEyeHeight(),range.getValue()));
                entry.data().ownerObservation(new dev.aegisac.common.check.OwnerObservation(System.nanoTime(),player.getWorld().getName(),
                        player.getGameMode()==org.bukkit.GameMode.CREATIVE || player.getGameMode()==org.bukkit.GameMode.SPECTATOR,
                        player.getAllowFlight(),player.isInsideVehicle(),player.isGliding(),bypasses));
                if(!settings.enabled()) return;
                var snapshot=capture.capture(player,before.revision(),System.nanoTime());
                if(!closed&&entries.get(uuid)==entry&&view.publish(before,snapshot)) captured.incrementAndGet();
                entry.world=player.getWorld().getUID();
            } catch(RuntimeException problem) {
                entry.data().guardOwnerObservation(null);
                entry.data().combatOwnerObservation(null);
                entry.data().ownerObservation(null);
                view.invalidate(); failed.incrementAndGet();
                if(reported.compareAndSet(false,true)) failures.accept(problem);
            }
    }
    private void checkOwner() { if(!(scheduler==null?server.isPrimaryThread():scheduler.globalThread())) throw new IllegalStateException("Capture scheduler requires owning thread"); }
    public long captured() { return captured.get(); }
    public long failed() { return failed.get(); }
    @Override public synchronized void close() { closed=true; entries.values().forEach(e->e.data.world().invalidate()); entries.clear(); order.clear(); }
}
