package dev.aegisac.paper.scheduler;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
/** Conventional Paper/Spigot: world/entity/global tasks share the primary thread. */
public final class BukkitPlatformScheduler implements PlatformScheduler {
    private final Plugin plugin; private final ManagedTasks tasks;
    private final ScheduledThreadPoolExecutor async;
    public BukkitPlatformScheduler(Plugin plugin) { this(plugin,4096); }
    public BukkitPlatformScheduler(Plugin plugin,int capacity) {
        this.plugin=Objects.requireNonNull(plugin); tasks=new ManagedTasks(capacity);
        async=new ScheduledThreadPoolExecutor(1,r->{ var thread=new Thread(r,"AegisAC-scheduled-async"); thread.setDaemon(true); return thread; });
        async.setRemoveOnCancelPolicy(true); async.setExecuteExistingDelayedTasksAfterShutdownPolicy(false); async.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }
    @Override public Mode mode() { return Mode.BUKKIT; }
    @Override public Task global(long delay,long period,Runnable action) {
        return schedule(delay,period,()->{ if(!globalThread()) throw new IllegalStateException("Global task requires primary thread"); action.run(); },null,null);
    }
    @Override public Task entity(Entity entity,long delay,long period,BooleanSupplier current,Runnable action,Runnable retired) {
        Objects.requireNonNull(entity); Objects.requireNonNull(current);
        return schedule(delay,period,()->{ requireEntity(entity); action.run(); },()->{ requireEntity(entity); return entity.isValid()&&current.getAsBoolean(); },retired);
    }
    @Override public Task region(World world,int x,int z,long delay,long period,Runnable action) {
        Objects.requireNonNull(world);
        return schedule(delay,period,()->{ requireRegion(world,x,z,x,z); action.run(); },null,null);
    }
    private Task schedule(long delay,long period,Runnable action,BooleanSupplier current,Runnable retired) {
        ManagedTasks.ticks(delay,period); var entry=tasks.reserve(period>0,action,retired==null?()->{}:retired);
        try {
            Runnable callback=()->entry.runIf(current==null?()->true:current);
            var scheduler=plugin.getServer().getScheduler();
            BukkitTask nativeTask=period==0?scheduler.runTaskLater(plugin,callback,delay):scheduler.runTaskTimer(plugin,callback,delay,period);
            entry.bind(nativeTask::cancel); return entry;
        } catch(RuntimeException|Error failure) { entry.cancel(); throw failure; }
    }
    @Override public Task async(long delay,long period,Runnable action) {
        ManagedTasks.millis(delay,period); var entry=tasks.reserve(period>0,action,()->{});
        try {
            var nativeTask=period==0?async.schedule(entry::run,delay,TimeUnit.MILLISECONDS):async.scheduleAtFixedRate(entry::run,delay,period,TimeUnit.MILLISECONDS);
            entry.bind(()->nativeTask.cancel(false)); return entry;
        } catch(RuntimeException|Error failure) { entry.cancel(); throw failure; }
    }
    @Override public boolean owns(Entity entity) { return plugin.getServer().isPrimaryThread(); }
    @Override public boolean owns(World world,int minX,int minZ,int maxX,int maxZ) { return minX<=maxX&&minZ<=maxZ&&plugin.getServer().isPrimaryThread(); }
    @Override public boolean globalThread() { return plugin.getServer().isPrimaryThread(); }
    @Override public int pendingTasks() { return tasks.size(); }
    @Override public void close() { tasks.close(); async.shutdownNow(); }
}
