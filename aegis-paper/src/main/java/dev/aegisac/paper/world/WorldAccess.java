package dev.aegisac.paper.world;
import dev.aegisac.paper.scheduler.PlatformScheduler;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
/** Ownership checks precede live reads; global ownership never implies entity ownership on Folia. */
public interface WorldAccess {
    boolean entity(Entity entity);
    boolean area(World world,int minChunkX,int minChunkZ,int maxChunkX,int maxChunkZ);
    boolean synchronousTeleports();
    static WorldAccess primary(Server server) { return primary(server::isPrimaryThread); }
    static WorldAccess primary(java.util.function.BooleanSupplier owner) {
        return new WorldAccess() {
            public boolean entity(Entity entity) { return owner.getAsBoolean(); }
            public boolean area(World world,int minX,int minZ,int maxX,int maxZ) { return owner.getAsBoolean(); }
            public boolean synchronousTeleports() { return true; }
        };
    }
    static WorldAccess scheduled(PlatformScheduler scheduler) {
        return new WorldAccess() {
            public boolean entity(Entity entity) { return scheduler.owns(entity); }
            public boolean area(World world,int minX,int minZ,int maxX,int maxZ) { return scheduler.owns(world,minX,minZ,maxX,maxZ); }
            public boolean synchronousTeleports() { return scheduler.mode()==PlatformScheduler.Mode.BUKKIT; }
        };
    }
}
