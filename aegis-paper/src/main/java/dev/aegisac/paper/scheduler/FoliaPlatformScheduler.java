package dev.aegisac.paper.scheduler;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
/** Native Folia domains. A global task is never a substitute for entity or region ownership. */
public final class FoliaPlatformScheduler implements PlatformScheduler {
    private final Plugin plugin; private final ManagedTasks tasks;
    public FoliaPlatformScheduler(Plugin plugin) { this(plugin,4096); }
    public FoliaPlatformScheduler(Plugin plugin,int capacity) { this.plugin=Objects.requireNonNull(plugin); tasks=new ManagedTasks(capacity); }
    @Override public Mode mode() { return Mode.FOLIA; }
    @Override public Task global(long delay,long period,Runnable action) {
        ManagedTasks.ticks(delay,period);
        var entry=tasks.reserve(period>0,()->{ if(!globalThread()) throw new IllegalStateException("Global task requires global region"); action.run(); },()->{});
        try {
            var scheduler=plugin.getServer().getGlobalRegionScheduler();
            var task=period==0?scheduler.runDelayed(plugin,t->entry.run(),delay):scheduler.runAtFixedRate(plugin,t->entry.run(),delay,period);
            bind(entry,task); return entry;
        } catch(RuntimeException|Error failure) { entry.cancel(); throw failure; }
    }
    @Override public Task entity(Entity entity,long delay,long period,BooleanSupplier current,Runnable action,Runnable retired) {
        Objects.requireNonNull(entity); Objects.requireNonNull(current); ManagedTasks.ticks(delay,period);
        var entry=tasks.reserve(period>0,()->{ requireEntity(entity); action.run(); },retired);
        try {
            java.util.function.Consumer<ScheduledTask> callback=t->entry.runIf(()->{
                requireEntity(entity); return current.getAsBoolean();
            });
            var scheduler=entity.getScheduler();
            var task=period==0?scheduler.runDelayed(plugin,callback,entry::retire,delay):scheduler.runAtFixedRate(plugin,callback,entry::retire,delay,period);
            if(task==null) entry.retire(); else bind(entry,task); return entry;
        } catch(RuntimeException|Error failure) { entry.cancel(); throw failure; }
    }
    @Override public Task region(World world,int x,int z,long delay,long period,Runnable action) {
        Objects.requireNonNull(world); ManagedTasks.ticks(delay,period);
        var entry=tasks.reserve(period>0,()->{ requireRegion(world,x,z,x,z); action.run(); },()->{});
        try {
            var scheduler=plugin.getServer().getRegionScheduler();
            var task=period==0?scheduler.runDelayed(plugin,world,x,z,t->entry.run(),delay):scheduler.runAtFixedRate(plugin,world,x,z,t->entry.run(),delay,period);
            bind(entry,task); return entry;
        } catch(RuntimeException|Error failure) { entry.cancel(); throw failure; }
    }
    @Override public Task async(long delay,long period,Runnable action) {
        ManagedTasks.millis(delay,period); var entry=tasks.reserve(period>0,action,()->{});
        try {
            var scheduler=plugin.getServer().getAsyncScheduler();
            var task=period>0?scheduler.runAtFixedRate(plugin,t->entry.run(),delay,period,TimeUnit.MILLISECONDS):
                    delay==0?scheduler.runNow(plugin,t->entry.run()):scheduler.runDelayed(plugin,t->entry.run(),delay,TimeUnit.MILLISECONDS);
            bind(entry,task); return entry;
        } catch(RuntimeException|Error failure) { entry.cancel(); throw failure; }
    }
    private static void bind(ManagedTasks.Entry entry,ScheduledTask task) { entry.bind(Objects.requireNonNull(task)::cancel); }
    @Override public boolean owns(Entity entity) { return plugin.getServer().isOwnedByCurrentRegion(entity); }
    @Override public boolean owns(World world,int minX,int minZ,int maxX,int maxZ) {
        return minX<=maxX&&minZ<=maxZ&&plugin.getServer().isOwnedByCurrentRegion(world,minX,minZ,maxX,maxZ);
    }
    @Override public boolean globalThread() { return plugin.getServer().isGlobalTickThread(); }
    @Override public int pendingTasks() { return tasks.size(); }
    @Override public void close() { tasks.close(); }
}
