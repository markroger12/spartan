package dev.aegisac.common.physics;
import dev.aegisac.common.collision.*;
import dev.aegisac.common.world.*;
import dev.aegisac.common.world.BlockSample.Surface;
import java.util.*;
public final class PhysicsFixtures {
    public static final UUID WORLD=new UUID(0,1);
    public static WorldSnapshot world(Surface surface,MotionContext context) {
        var floor=new Aabb(-16,0,-16,16,1,16);
        return new WorldSnapshot(WORLD,0,0,new Aabb(-16,-4,-16,16,20,16),
                List.of(new BlockSample(floor,List.of(floor),Set.of(surface))),List.of(),context,Set.of());
    }
    public static PhysicsEngine.State standing() { return new PhysicsEngine.State(new Vec3(0,1,0),Vec3.ZERO,true,1.8); }
    public static PhysicsEngine.Input input(boolean sprint,boolean jump) { return new PhysicsEngine.Input(1,0,0,sprint,false,jump); }
}
