package dev.aegisac.paper.scheduler;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import java.util.function.BooleanSupplier;
/** Explicit ownership domains. Async callbacks must never read live world/entity state. */
public interface PlatformScheduler extends AutoCloseable {
    enum Mode { BUKKIT, FOLIA }
    interface Task {
        boolean cancel();
        boolean done();
    }
    Mode mode();
    /** Delay >=1 tick; period 0 means one-shot, otherwise >=1 tick. Global does not own entities on Folia. */
    Task global(long delayTicks,long periodTicks,Runnable action);
    /** Entity reference is a scheduler address, not permission to read it from the submitting thread. */
    Task entity(Entity entity,long delayTicks,long periodTicks,BooleanSupplier current,Runnable action,Runnable retired);
    Task region(World world,int chunkX,int chunkZ,long delayTicks,long periodTicks,Runnable action);
    /** Wall-clock milliseconds; period 0 means one-shot. Never use async work for Bukkit reads. */
    Task async(long delayMillis,long periodMillis,Runnable action);
    boolean owns(Entity entity);
    boolean owns(World world,int minChunkX,int minChunkZ,int maxChunkX,int maxChunkZ);
    boolean globalThread();
    int pendingTasks();
    default void requireEntity(Entity entity) { if(!owns(entity)) throw new IllegalStateException("Entity access requires its owning scheduler"); }
    default void requireRegion(World world,int minX,int minZ,int maxX,int maxZ) {
        if(!owns(world,minX,minZ,maxX,maxZ)) throw new IllegalStateException("World access crosses an unowned region");
    }
    @Override void close();
}
