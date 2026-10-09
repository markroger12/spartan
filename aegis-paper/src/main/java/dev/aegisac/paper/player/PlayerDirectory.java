package dev.aegisac.paper.player;
import dev.aegisac.common.player.*;
import dev.aegisac.common.check.ServerTickHealth;
import dev.aegisac.paper.scheduler.PlatformScheduler;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import java.util.*;
import java.util.concurrent.*;
/** Entity addresses plus immutable owner-captured staff data. Foreign regions never inspect live player state. */
@SuppressWarnings("deprecation")
public final class PlayerDirectory implements AutoCloseable {
    public record Observation(long time,long epoch,UUID worldId,String world,int ping,String effects,String environment) { }
    public final class Handle {
        private final UUID uuid; private final String name; private final Player address; private final PlayerData data;
        private final ServerTickHealth health=new ServerTickHealth(); private volatile Observation observation;
        private volatile PlatformScheduler.Task ticker; private int ticks;
        Handle(Player player,PlayerData data) { address=player; uuid=player.getUniqueId(); name=player.getName(); this.data=data; }
        public UUID getUniqueId() { return uuid; } public String getName() { return name; } public PlayerData data() { return data; }
        /** Scheduling address only. Live getters require this entity's owner. */
        public Player address() { return address; }
        public boolean current() { return !closed&&handles.get(uuid)==this&&registry.find(uuid)==data; }
        public Observation observation() {
            var value=observation; long now=System.nanoTime();
            return current()&&value!=null&&value.epoch()==data.lossEpoch()&&now-value.time()>=0&&now-value.time()<=2_000_000_000L?value:null;
        }
        public ServerTickHealth.Snapshot health() {
            var value=health.snapshot(); long age=System.nanoTime()-value.observedNanos();
            return current()&&value.known()&&age>=0&&age<=250_000_000L?value:ServerTickHealth.Snapshot.unknown();
        }
        public PlatformScheduler.Task execute(Runnable action,Runnable retired) { return scheduler.entity(address,1,0,this::current,action,retired); }
        public void run(Runnable action,Runnable retired) {
            if(scheduler.owns(address)) { if(current()) action.run(); else retired.run(); }
            else execute(action,retired);
        }
        private void tick() {
            health.tick(System.nanoTime());
            if(ticks++%20==0||observation==null||observation.epoch()!=data.lossEpoch()) capture();
        }
        public void capture() {
            scheduler.requireEntity(address); if(!current()) return;
            long epoch=data.lossEpoch(); var world=address.getWorld();
            var effects=address.getActivePotionEffects().stream().limit(32).map(e->e.getType().getName()+":"+e.getAmplifier()).collect(java.util.stream.Collectors.joining(", "));
            observation=new Observation(System.nanoTime(),epoch,world.getUID(),world.getName(),address.getPing(),effects,
                    world.getName()+" / "+address.getGameMode()+" / gliding="+address.isGliding()+" / swimming="+address.isSwimming()+" / vehicle="+address.isInsideVehicle());
        }
        private void close() { var task=ticker; if(task!=null) task.cancel(); observation=null; }
    }
    private final ConcurrentMap<UUID,Handle> handles=new ConcurrentHashMap<>();
    private final PlayerRegistry registry; private final PlatformScheduler scheduler; private volatile boolean closed;
    public PlayerDirectory(PlayerRegistry registry,PlatformScheduler scheduler) { this.registry=registry; this.scheduler=scheduler; }
    public Handle attach(Player player,PlayerData data) {
        scheduler.requireEntity(player); var handle=new Handle(player,data);
        synchronized(this) { if(closed) throw new IllegalStateException("Directory closed"); var old=handles.put(handle.uuid,handle); if(old!=null) old.close(); }
        try { handle.capture(); handle.ticker=scheduler.entity(player,1,1,handle::current,handle::tick,handle::close); }
        catch(RuntimeException failure) { handles.remove(handle.uuid,handle); handle.close(); throw failure; }
        if(!handle.current()) handle.close(); return handle;
    }
    public Handle find(UUID uuid) { var handle=handles.get(uuid); return handle!=null&&handle.current()?handle:null; }
    public Handle find(CommandSender viewer,String name) { return handles.values().stream().filter(h->h.name.equalsIgnoreCase(name)&&h.current()&&visible(viewer,h)).findFirst().orElse(null); }
    public List<Handle> visible(CommandSender viewer) { return handles.values().stream().filter(h->h.current()&&visible(viewer,h)).sorted(Comparator.comparing(Handle::getName)).toList(); }
    public boolean visible(CommandSender viewer,Handle target) {
        // canSee belongs to the viewer; the target is only its immutable UUID address in Bukkit's visibility API.
        if(viewer instanceof Player player) { scheduler.requireEntity(player); return player.canSee(target.address); } return true;
    }
    public void detach(UUID uuid) { var handle=handles.remove(uuid); if(handle!=null) handle.close(); }
    public ServerTickHealth.Snapshot health(UUID uuid) { var h=find(uuid); return h==null?ServerTickHealth.Snapshot.unknown():h.health(); }
    @Override public synchronized void close() { closed=true; handles.values().forEach(Handle::close); handles.clear(); }
}
