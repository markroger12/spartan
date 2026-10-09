package dev.aegisac.common.check;
import dev.aegisac.common.collision.*;
import dev.aegisac.common.config.*;
import dev.aegisac.common.physics.MotionContext;
import dev.aegisac.common.world.*;
import java.util.*;
public final class CheckFixtures {
    public static final UUID WORLD=new UUID(0,1);
    public static WorldSnapshot world(long revision) {
        var floor=new Aabb(-10,0,-10,10,1,10);
        return new WorldSnapshot(WORLD,revision,0,new Aabb(-10,-2,-10,10,20,10),
                List.of(new BlockSample(floor,List.of(floor),Set.of(BlockSample.Surface.SOLID))),List.of(),MotionContext.NORMAL,Set.of());
    }
    public static MovementFrame frame(long sequence,long time,boolean anomalous,Set<String> reasons) {
        return new MovementFrame(sequence,time,new Vec3(0,1,0),new Vec3(anomalous?1:.1,1,0),new Vec3(.1,0,0),
                .2,-.08,.42,.15,true,true,true,true,true,true,false,false,0,0,world(0),reasons);
    }
    public static ConfigSnapshot config(long generation,MovementSettings settings) {
        return new ConfigSnapshot(generation,"balanced",Map.of(),true,dev.aegisac.common.packet.PipelineFixtures.settings(),
                PhysicsSettings.DEFAULT,settings,CombatSettings.defaults(),GuardSettings.defaults(),EditionSettings.defaults(),null,new ExemptionSettings(true,true,true,true,Set.of("lobby")),Map.of(),Map.of());
    }
    public static MovementSettings noGrace() {
        var d=MovementSettings.defaults();
        return new MovementSettings(true,4,0,0,0,0,250,100,3,2000,d.timing(),d.rules());
    }
}
