package dev.aegisac.common.physics;
import dev.aegisac.common.collision.*;
import dev.aegisac.common.world.*;
import dev.aegisac.common.world.BlockSample.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static dev.aegisac.common.physics.PhysicsFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
class PhysicsReplayTest {
    private final PhysicsEngine engine=new PhysicsEngine();
    @ParameterizedTest @EnumSource(PhysicsProfile.class)
    void walkingAndSprintMatchIndependentOneTickReference(PhysicsProfile p) {
        var world=world(Surface.SOLID,MotionContext.NORMAL);
        var walk=engine.step(p,standing(),input(false,false),world);
        // Independently evaluated decimal reference: .98*.1*.16277136/(.6*.91)^3 for legacy;
        // modern uses .98*.1*.216/.6^3. Float/trig bit-for-bit conformance is not claimed.
        assertEquals(p==PhysicsProfile.JAVA_1_8?.09800001444971859173:.098,walk.movement().z(),1e-12);
        assertEquals(-.0784,walk.state().velocity().y(),1e-12);
        assertTrue(walk.state().grounded()); assertTrue(walk.uncertainty().isEmpty());
        var sprint=engine.step(p,standing(),input(true,false),world);
        assertEquals(walk.movement().z()*1.3,sprint.movement().z(),1e-12);
    }
    @Test void jumpArcReturnsToFloorWithoutPenetration() {
        var world=world(Surface.SOLID,MotionContext.NORMAL); var state=standing(); double highest=state.feet().y();
        for(int tick=0;tick<20;tick++) {
            var step=engine.step(PhysicsProfile.JAVA_1_16_5,state,new PhysicsEngine.Input(0,0,0,false,false,tick==0),world);
            if(tick==0) { assertEquals(.42,step.movement().y(),1e-12); assertEquals(.3332,step.state().velocity().y(),1e-12); }
            state=step.state(); highest=Math.max(highest,state.feet().y()); assertTrue(state.feet().y()>=1);
        }
        assertEquals(2.2522033525119995,highest,1e-10); assertEquals(1,state.feet().y()); assertTrue(state.grounded());
    }
    @Test void diagonalInputIsNormalizedAndSprintJumpAddsYawAlignedImpulse() {
        var world=world(Surface.SOLID,MotionContext.NORMAL);
        var straight=engine.step(PhysicsProfile.JAVA_1_8,standing(),input(false,false),world);
        var diagonal=engine.step(PhysicsProfile.JAVA_1_8,standing(),new PhysicsEngine.Input(1,1,0,false,false,false),world);
        assertEquals(straight.movement().horizontalSquared()/(.98*.98),diagonal.movement().horizontalSquared(),1e-12);
        var jump=engine.step(PhysicsProfile.JAVA_1_8,standing(),new PhysicsEngine.Input(1,0,90,true,false,true),world);
        assertTrue(jump.movement().x()<-.3); assertEquals(0,jump.movement().z(),1e-12);
    }
    @Test void iceAndSlimeHaveDistinctMomentumAndSlimeIsUncertain() {
        var initial=new PhysicsEngine.State(new Vec3(0,1,0),new Vec3(.3,0,0),true,1.8);
        var input=new PhysicsEngine.Input(0,0,0,false,false,false);
        var ice=engine.step(PhysicsProfile.JAVA_1_16_5,initial,input,world(Surface.ICE,MotionContext.NORMAL));
        var normal=engine.step(PhysicsProfile.JAVA_1_16_5,initial,input,world(Surface.SOLID,MotionContext.NORMAL));
        assertEquals(.3*.98*.91,ice.state().velocity().x(),1e-12);
        assertTrue(ice.state().velocity().x()>normal.state().velocity().x());
        assertTrue(engine.step(PhysicsProfile.JAVA_1_16_5,initial,input,world(Surface.SLIME,MotionContext.NORMAL)).uncertainty().contains(Uncertainty.SLIME));
    }
    @Test void speedSlownessJumpBoostLevitationAndSlowFallUseCapturedContext() {
        var fast=world(Surface.SOLID,new MotionContext(.12,.42,1,-1,false,Set.of()));
        var slow=world(Surface.SOLID,new MotionContext(.085,.42,-1,-1,false,Set.of()));
        var a=engine.step(PhysicsProfile.JAVA_1_16_5,standing(),input(false,true),fast);
        var b=engine.step(PhysicsProfile.JAVA_1_16_5,standing(),input(false,false),slow);
        assertEquals(.62,a.movement().y(),1e-12); assertTrue(a.movement().z()>b.movement().z());
        var fall=new PhysicsEngine.State(new Vec3(0,5,0),new Vec3(0,-.1,0),false,1.8);
        var floating=world(Surface.SOLID,new MotionContext(.1,.42,-1,-1,true,Set.of()));
        assertEquals(-.1078,engine.step(PhysicsProfile.JAVA_1_16_5,fall,input(false,false),floating).state().velocity().y(),1e-12);
        var levitating=world(Surface.SOLID,new MotionContext(.1,.42,-1,1,false,Set.of()));
        assertEquals(-.0588,engine.step(PhysicsProfile.JAVA_1_16_5,fall,input(false,false),levitating).state().velocity().y(),1e-12);
    }
    @ParameterizedTest @EnumSource(value=Surface.class,names={"WATER","LAVA","CLIMBABLE","COBWEB","HONEY","SOUL_SAND","PISTON","BUBBLE_COLUMN","SCAFFOLDING","SPECIAL"})
    void specialMechanicsNeverSilentlyProduceCertainResults(Surface surface) {
        var area=new Aabb(-3,1,-3,3,5,3);
        var base=world(Surface.SOLID,MotionContext.NORMAL);
        var blocks=new ArrayList<>(base.blocks()); blocks.add(new BlockSample(area,List.of(),Set.of(surface)));
        var w=new WorldSnapshot(WORLD,0,0,base.coverage(),blocks,List.of(),MotionContext.NORMAL,Set.of());
        var result=engine.step(PhysicsProfile.JAVA_1_21_11,standing(),input(false,true),w);
        assertFalse(result.uncertainty().isEmpty()); assertTrue(Double.isFinite(result.state().feet().y()));
    }
    @ParameterizedTest @EnumSource(value=Uncertainty.class,names={"ELYTRA","VEHICLE","RIPTIDE","UNSUPPORTED_EFFECT","UNSUPPORTED_ATTRIBUTE","FLIGHT"})
    void unsupportedEntityMechanicsPropagateWithoutInventingEnvelopes(Uncertainty reason) {
        var w=world(Surface.SOLID,new MotionContext(.1,.42,-1,-1,false,Set.of(reason)));
        assertTrue(engine.step(PhysicsProfile.JAVA_1_21_11,standing(),input(false,false),w).uncertainty().contains(reason));
    }
    @Test void versionSpecificSmallMotionAndUnknownVersionHaveExplicitBehavior() {
        var state=new PhysicsEngine.State(new Vec3(0,5,0),new Vec3(.004,0,0),false,1.8);
        var input=new PhysicsEngine.Input(0,0,0,false,false,false); var w=world(Surface.SOLID,MotionContext.NORMAL);
        assertEquals(0,engine.step(PhysicsProfile.JAVA_1_8,state,input,w).movement().x());
        assertEquals(.004,engine.step(PhysicsProfile.JAVA_1_16_5,state,input,w).movement().x());
        assertNull(PhysicsProfile.forProtocol(999));
        assertTrue(engine.step(null,state,input,w).uncertainty().contains(Uncertainty.UNSUPPORTED_VERSION));
    }
    @Test void appliedVelocityAndExplosionVectorAreSimulatedAndClippedAtWall() {
        var base=world(Surface.SOLID,MotionContext.NORMAL); var wall=new Aabb(1,1,-2,2,5,2);
        var blocks=new ArrayList<>(base.blocks()); blocks.add(new BlockSample(wall,List.of(wall),Set.of(Surface.SOLID)));
        var w=new WorldSnapshot(WORLD,0,0,base.coverage(),blocks,List.of(),MotionContext.NORMAL,Set.of());
        var impulse=new Vec3(.8,.2,0).add(new Vec3(.5,.1,0));
        var result=engine.step(PhysicsProfile.JAVA_1_16_5,new PhysicsEngine.State(new Vec3(0,1,0),impulse,false,1.8),
                new PhysicsEngine.Input(0,0,0,false,false,false),w);
        assertEquals(.7,result.movement().x(),1e-12); assertEquals(0,result.state().velocity().x());
        assertEquals(.3,result.movement().y(),1e-12); assertTrue(result.horizontalCollision());
    }
    @Test void fallingOnSlimeBouncesAndSneakingSuppressesBounce() {
        var world=world(Surface.SLIME,MotionContext.NORMAL);
        var falling=new PhysicsEngine.State(new Vec3(0,1.2,0),new Vec3(0,-.5,0),false,1.8);
        var bounce=engine.step(PhysicsProfile.JAVA_1_16_5,falling,new PhysicsEngine.Input(0,0,0,false,false,false),world);
        assertEquals(1,bounce.state().feet().y(),1e-12); assertEquals(.4116,bounce.state().velocity().y(),1e-12);
        assertTrue(bounce.uncertainty().contains(Uncertainty.SLIME));
        var sneak=engine.step(PhysicsProfile.JAVA_1_16_5,falling,new PhysicsEngine.Input(0,0,0,false,true,false),world);
        assertEquals(-.0784,sneak.state().velocity().y(),1e-12);
    }
    @Test void nominalWaterAndLavaDampingRemainUncertain() {
        var base=world(Surface.SOLID,MotionContext.NORMAL);
        for(Surface medium:List.of(Surface.WATER,Surface.LAVA)) {
            var volume=new Aabb(-3,1,-3,3,8,3);
            var w=new WorldSnapshot(WORLD,0,0,base.coverage(),List.of(new BlockSample(volume,List.of(),Set.of(medium))),
                    List.of(),MotionContext.NORMAL,Set.of());
            var state=new PhysicsEngine.State(new Vec3(0,3,0),new Vec3(.1,-.1,0),false,1.8);
            var result=engine.step(PhysicsProfile.JAVA_1_16_5,state,new PhysicsEngine.Input(0,0,0,false,false,false),w);
            assertEquals(medium==Surface.WATER?.08:.05,result.state().velocity().x(),1e-12);
            assertEquals(medium==Surface.WATER?-.1:-.07,result.state().velocity().y(),1e-12);
            assertTrue(result.uncertainty().contains(Uncertainty.FLUID_FLOW));
        }
    }
    @Test void missingCoverageInitialOverlapAndNearbyEntitiesAreNotAssumedClear() {
        var w=world(Surface.SOLID,MotionContext.NORMAL);
        var outside=new PhysicsEngine.State(new Vec3(16,1,0),Vec3.ZERO,true,1.8);
        assertTrue(engine.step(PhysicsProfile.JAVA_1_8,outside,input(false,false),w).uncertainty().contains(Uncertainty.OUTSIDE_SNAPSHOT));
        var overlap=new PhysicsEngine.State(new Vec3(0,.9,0),Vec3.ZERO,true,1.8);
        assertTrue(engine.step(PhysicsProfile.JAVA_1_8,overlap,input(false,false),w).uncertainty().contains(Uncertainty.INITIAL_OVERLAP));
        var entityWorld=new WorldSnapshot(WORLD,0,0,w.coverage(),w.blocks(),List.of(Aabb.player(new Vec3(0,1,.5),1.8)),MotionContext.NORMAL,Set.of());
        assertTrue(engine.step(PhysicsProfile.JAVA_1_8,standing(),input(false,false),entityWorld).uncertainty().contains(Uncertainty.ENTITY_COLLISION));
    }
}
