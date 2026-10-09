package dev.aegisac.paper.scheduler;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import java.util.*;
import java.util.function.BooleanSupplier;
/** Deterministic, distinct ownership domains; callbacks may be delayed across reconnects. */
public final class OwnershipHarness implements PlatformScheduler {
    public Entity owner; public boolean global=true,area=true; public final List<Job> jobs=new ArrayList<>();
    public final class Job implements Task {
        public final Entity entity; final boolean recurring; final BooleanSupplier current; final Runnable action,retired; boolean done;
        Job(Entity entity,boolean recurring,BooleanSupplier current,Runnable action,Runnable retired) { this.entity=entity; this.recurring=recurring; this.current=current; this.action=action; this.retired=retired; }
        public void fire() {
            if(done) return;
            Entity previous=owner; boolean prior=global; owner=entity; global=entity==null;
            try { if(!current.getAsBoolean()) { done=true; retired.run(); } else { action.run(); if(!recurring) done=true; } }
            finally { owner=previous; global=prior; }
        }
        @Override public boolean cancel() { if(done) return false; done=true; return true; }
        @Override public boolean done() { return done; }
    }
    public void at(Entity entity,Runnable action) { Entity previous=owner; boolean prior=global; owner=entity; global=entity==null; try { action.run(); } finally { owner=previous; global=prior; } }
    public void drain(Entity entity) { for(var job:List.copyOf(jobs)) if(job.entity==entity&&!job.recurring) job.fire(); }
    @Override public Mode mode() { return Mode.FOLIA; }
    private Task job(Entity entity,long period,BooleanSupplier current,Runnable action,Runnable retired) { var job=new Job(entity,period>0,current,action,retired); jobs.add(job); return job; }
    @Override public Task global(long delay,long period,Runnable action) { return job(null,period,()->true,action,()->{}); }
    @Override public Task entity(Entity entity,long delay,long period,BooleanSupplier current,Runnable action,Runnable retired) { return job(entity,period,current,action,retired); }
    @Override public Task region(World world,int x,int z,long delay,long period,Runnable action) { throw new UnsupportedOperationException(); }
    @Override public Task async(long delay,long period,Runnable action) { throw new UnsupportedOperationException(); }
    @Override public boolean owns(Entity entity) { return entity!=null&&entity==owner; }
    @Override public boolean owns(World world,int minX,int minZ,int maxX,int maxZ) { return area&&owner!=null; }
    @Override public boolean globalThread() { return global; }
    @Override public int pendingTasks() { return (int)jobs.stream().filter(j->!j.done()).count(); }
    @Override public void close() { jobs.forEach(Job::cancel); }
}
