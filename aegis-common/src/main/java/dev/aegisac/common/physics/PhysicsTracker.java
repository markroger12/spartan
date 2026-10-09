package dev.aegisac.common.physics;
import dev.aegisac.api.player.*;
import dev.aegisac.common.collision.*;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.common.world.*;
import java.util.*;
/** Worker-owned exploratory predictor. Reanchors to reports; never promotes them to verified state. */
public final class PhysicsTracker {
    private final PhysicsEngine engine=new PhysicsEngine();
    private dev.aegisac.common.check.MovementFrame movementFrame;
    private Vec3 previousDelta=Vec3.ZERO;
    private double fallDistance;
    public dev.aegisac.common.check.MovementFrame movementFrame() { return movementFrame; }
    private Vec3 previous, velocity=Vec3.ZERO;
    private float yaw;
    private boolean grounded;
    private long previousTime;
    private PhysicsSnapshot result=PhysicsSnapshot.unavailable("BASELINE_MISSING");
    public PhysicsSnapshot snapshot() { return result; }
    public void reset(Uncertainty reason) {
        previous=null; velocity=Vec3.ZERO; grounded=false; movementFrame=null; previousDelta=Vec3.ZERO; fallDistance=0;
        result=PhysicsSnapshot.unavailable(reason.name());
    }
    public void process(PacketFrame frame, WorldView.State view, ActivitySnapshot activity, BedrockStatus edition, long maxAgeNanos) {
        if(edition==BedrockStatus.BEDROCK) { reset(Uncertainty.BEDROCK_PHYSICS_UNAVAILABLE); return; }
        PacketKind kind=frame.packet().kind();
        if(kind==PacketKind.TELEPORT || kind==PacketKind.WORLD_RESET) { reset(Uncertainty.TELEPORT); return; }
        if(kind==PacketKind.VELOCITY || kind==PacketKind.EXPLOSION) { reset(Uncertainty.VELOCITY_TIMING); return; }
        if(kind==PacketKind.WORLD_CHANGE || kind==PacketKind.EFFECT || kind==PacketKind.ABILITIES || kind==PacketKind.VEHICLE) {
            reset(Uncertainty.INVALIDATED_WORLD); return;
        }
        if(frame.direction()!=PacketDirection.INBOUND || !(frame.packet() instanceof Movement m)) return;
        movementFrame=null;
        if(m.rotation()) {
            if(!Float.isFinite(m.yaw()) || !Float.isFinite(m.pitch())) { reset(Uncertainty.INVALID_MOVEMENT); return; }
            yaw=m.yaw();
        }
        if(!m.position()) {
            // Position-suppressed packets may represent a tick or multiple idle ticks. Do not invent tick count.
            reset(Uncertainty.PACKET_GAP); return;
        }
        if(!Double.isFinite(m.x()) || !Double.isFinite(m.y()) || !Double.isFinite(m.z())
                || Math.abs(m.x())>30_000_000 || Math.abs(m.y())>30_000_000 || Math.abs(m.z())>30_000_000) {
            reset(Uncertainty.INVALID_MOVEMENT); return;
        }
        Vec3 current=new Vec3(m.x(),m.y(),m.z());
        EnumSet<Uncertainty> reasons=EnumSet.of(Uncertainty.INPUT_INFERRED,Uncertainty.UNACKNOWLEDGED_WORLD);
        if(edition!=BedrockStatus.JAVA) reasons.add(Uncertainty.UNKNOWN_EDITION);
        PhysicsProfile profile=frame.protocol().known()?PhysicsProfile.forProtocol(frame.protocol().id()):null;
        if(profile==null) reasons.add(Uncertainty.UNSUPPORTED_VERSION);
        if(profile!=null && profile!=PhysicsProfile.JAVA_1_21_11) reasons.add(Uncertainty.TRANSLATED_GEOMETRY);
        if(result.worldRevision()>=0 && result.worldRevision()!=view.revision()) {
            previous=null; velocity=Vec3.ZERO; grounded=false; movementFrame=null; previousDelta=Vec3.ZERO; fallDistance=0; reasons.add(Uncertainty.INVALIDATED_WORLD);
        }
        WorldSnapshot world=view.snapshot();
        if(world==null) reasons.add(Uncertainty.NO_WORLD);
        else {
            long age=frame.observedNanos()-world.capturedNanos();
            if(age<0) reasons.add(Uncertainty.FUTURE_WORLD);
            if(age>maxAgeNanos) reasons.add(Uncertainty.STALE_WORLD);
        }
        if(previous==null) reasons.add(Uncertainty.BASELINE_MISSING);
        long interval=frame.observedNanos()-previousTime;
        if(previous!=null && (interval<25_000_000 || interval>100_000_000)) reasons.add(Uncertainty.PACKET_GAP);
        double maxHorizontal=0,minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxAcceleration=0;
        PhysicsEngine.Result best=null; double residual=Double.POSITIVE_INFINITY; int candidates=0;
        if(world!=null && previous!=null && profile!=null && !reasons.contains(Uncertainty.STALE_WORLD)
                && !reasons.contains(Uncertainty.FUTURE_WORLD) && !reasons.contains(Uncertainty.PACKET_GAP)) {
            for(int forward=-1;forward<=1;forward++) for(int side=-1;side<=1;side++) for(int jump=0;jump<2;jump++) {
                var step=engine.step(profile,new PhysicsEngine.State(previous,velocity,grounded,1.8),
                        new PhysicsEngine.Input(forward,side,yaw,activity.sprinting(),activity.sneaking(),jump==1),world);
                maxHorizontal=Math.max(maxHorizontal,Math.sqrt(step.movement().horizontalSquared()));
                minY=Math.min(minY,step.movement().y()); maxY=Math.max(maxY,step.movement().y());
                maxAcceleration=Math.max(maxAcceleration,Math.sqrt(new Vec3(step.movement().x()-previousDelta.x(),0,
                        step.movement().z()-previousDelta.z()).horizontalSquared()));
                // Every alternative contributes uncertainty; selecting the nearest must not erase missing mechanics.
                reasons.addAll(step.uncertainty());
                double distance=step.state().feet().subtract(current).lengthSquared(); candidates++;
                if(distance<residual) { best=step; residual=distance; }
            }
        }
        if(best!=null) {
            reasons.addAll(best.uncertainty());
            velocity=best.state().velocity(); grounded=best.state().grounded();
        } else { velocity=Vec3.ZERO; grounded=false; }
        if(best!=null) {
            Vec3 delta=current.subtract(previous);
            Aabb before=Aabb.player(previous,1.8), after=Aabb.player(current,1.8);
            boolean supported=support(after,world), wasSupported=support(before,world);
            boolean clear=world.shapes().stream().noneMatch(after::intersects);
            if(!world.coverage().contains(before.sweep(delta))) reasons.add(Uncertainty.OUTSIDE_SNAPSHOT);
            var clipped=new CollisionSolver().move(before,delta,world.shapes(),.6,wasSupported,profile.modernAxisOrder);
            double phase=Math.sqrt(clipped.movement().subtract(delta).lengthSquared());
            boolean liquid=false,climbable=false;
            for(var block:world.blocks()) if(block.volume().intersects(before.sweep(delta))) {
                liquid |= block.surfaces().contains(BlockSample.Surface.WATER) || block.surfaces().contains(BlockSample.Surface.LAVA);
                climbable |= block.surfaces().contains(BlockSample.Surface.CLIMBABLE);
            }
            if(supported) fallDistance=0; else if(delta.y()<0) fallDistance=Math.min(60_000_000,fallDistance-delta.y());
            movementFrame=new dev.aegisac.common.check.MovementFrame(frame.sequence(),frame.observedNanos(),previous,current,
                    previousDelta,maxHorizontal,minY,maxY,maxAcceleration,wasSupported,supported,clear,m.onGround(),
                    activity.sprinting(),activity.sneaking(),climbable,liquid,phase,fallDistance,world,
                    reasons.stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()));
            previousDelta=delta;
        } else { previousDelta=Vec3.ZERO; fallDistance=0; }
        previous=current; previousTime=frame.observedNanos();
        Vec3 predicted=best==null?current:best.state().feet();
        result=new PhysicsSnapshot(frame.sequence(),view.revision(),profile==null?"unsupported":profile.name(),candidates,
                best==null?-1:Math.sqrt(residual),predicted.x(),predicted.y(),predicted.z(),grounded,
                reasons.stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
    private static boolean support(Aabb box,WorldSnapshot world) {
        var foot=new Aabb(box.minX(),box.minY()-.001,box.minZ(),box.maxX(),box.minY(),box.maxZ());
        return world.shapes().stream().anyMatch(foot::intersects);
    }
}
