package dev.aegisac.common.physics;

import dev.aegisac.common.collision.*;
import dev.aegisac.common.world.*;
import dev.aegisac.common.world.BlockSample.Surface;
import java.util.*;

/** Deterministic one-tick Java model. Results with uncertainty are illustrative, never legal envelopes. */
public final class PhysicsEngine {
    public record State(Vec3 feet, Vec3 velocity, boolean grounded, double height) {
        public State {
            Objects.requireNonNull(feet); Objects.requireNonNull(velocity);
            if (!Double.isFinite(height) || height<.2 || height>2) throw new IllegalArgumentException("Invalid height");
        }
    }
    public record Input(float forward, float sideways, float yaw, boolean sprint, boolean sneak, boolean jump) {
        public Input {
            if (!Float.isFinite(forward) || !Float.isFinite(sideways) || !Float.isFinite(yaw)
                    || Math.abs(forward)>1 || Math.abs(sideways)>1) throw new IllegalArgumentException("Invalid input");
        }
    }
    public record Result(State state, Vec3 movement, boolean horizontalCollision, boolean stepped, Set<Uncertainty> uncertainty) {
        public Result { uncertainty=Set.copyOf(uncertainty); }
    }
    private final CollisionSolver collisions = new CollisionSolver();
    public Result step(PhysicsProfile profile, State state, Input input, WorldSnapshot world) {
        EnumSet<Uncertainty> uncertain=EnumSet.noneOf(Uncertainty.class);
        uncertain.addAll(world.uncertainty()); uncertain.addAll(world.context().uncertainty());
        if (profile==null) { uncertain.add(Uncertainty.UNSUPPORTED_VERSION); return unchanged(state,uncertain); }
        Aabb box=Aabb.player(state.feet(),state.height());
        if (!world.coverage().contains(box)) { uncertain.add(Uncertainty.OUTSIDE_SNAPSHOT); return unchanged(state,uncertain); }
        for(Aabb shape:world.shapes()) if(box.intersects(shape)) uncertain.add(Uncertainty.INITIAL_OVERLAP);
        EnumSet<Surface> media=EnumSet.noneOf(Surface.class);
        EnumSet<Surface> support=EnumSet.noneOf(Surface.class);
        Aabb feet=box.move(new Vec3(0,-.001,0));
        for(BlockSample block:world.blocks()) {
            if(block.volume().intersects(box)) media.addAll(block.surfaces());
            for(Aabb shape:block.shapes()) if(feet.intersects(shape)) { support.addAll(block.surfaces()); break; }
        }
        for(Surface surface:media) addUncertainty(surface,uncertain);
        for(Surface surface:support) addUncertainty(surface,uncertain);
        if(input.sneak()) uncertain.add(Uncertainty.SNEAK_EDGE);
        double friction=support.contains(Surface.BLUE_ICE)?.989:support.contains(Surface.ICE)?.98:support.contains(Surface.SLIME)?.8:.6;
        if(profile==PhysicsProfile.JAVA_1_8 && (support.contains(Surface.BLUE_ICE) || support.contains(Surface.HONEY)))
            uncertain.add(Uncertainty.TRANSLATED_GEOMETRY);
        boolean water=media.contains(Surface.WATER), lava=media.contains(Surface.LAVA), liquid=water||lava;
        double drag=state.grounded()?friction*.91:.91;
        MotionContext context=world.context();
        if(profile==PhysicsProfile.JAVA_1_8 && (context.slowFalling() || context.levitation()>=0))
            uncertain.add(Uncertainty.UNSUPPORTED_EFFECT);
        double speed=context.movementSpeed()*(input.sprint()?1.3:1);
        double groundAcceleration=profile==PhysicsProfile.JAVA_1_8 ? speed*.16277136/(drag*drag*drag)
                : speed*.216/(friction*friction*friction);
        double acceleration=liquid?.02:state.grounded()?groundAcceleration:input.sprint()?.026:.02;
        double forward=input.forward()*.98*(input.sneak()?.3:1), side=input.sideways()*.98*(input.sneak()?.3:1);
        double length=Math.sqrt(forward*forward+side*side);
        if(length<1) length=1;
        forward=forward/length*acceleration; side=side/length*acceleration;
        double radians=Math.toRadians(input.yaw()), sin=Math.sin(radians), cos=Math.cos(radians);
        double x=small(state.velocity().x(),profile)+side*cos-forward*sin;
        double z=small(state.velocity().z(),profile)+forward*cos+side*sin;
        double y=small(state.velocity().y(),profile);
        if(input.jump() && liquid) y+=.04;
        else if(input.jump() && state.grounded()) {
            y=context.jumpStrength()+(context.jumpBoost()<0?0:.1*(context.jumpBoost()+1));
            if(input.sprint()) { x-=sin*.2; z+=cos*.2; }
        }
        if(media.contains(Surface.CLIMBABLE)) {
            x=clamp(x,-.15,.15); z=clamp(z,-.15,.15); y=Math.max(y,input.sneak()?0:-.15);
        }
        if(media.contains(Surface.COBWEB)) { x*=.25; y*=.05; z*=.25; }
        if(media.contains(Surface.HONEY) && y<-.08) y=-.05;
        Vec3 desired=new Vec3(x,y,z);
        Aabb sweep=box.sweep(desired).sweep(new Vec3(0,.6,0));
        if (!world.coverage().contains(sweep) || Math.abs(x)>4 || Math.abs(y)>4 || Math.abs(z)>4) {
            uncertain.add(Uncertainty.OUTSIDE_SNAPSHOT); return unchanged(state,uncertain);
        }
        for(BlockSample block:world.blocks()) if(block.volume().intersects(sweep))
            for(Surface surface:block.surfaces()) addUncertainty(surface,uncertain);
        for(Aabb entity:world.entities()) if(sweep.intersects(entity)) uncertain.add(Uncertainty.ENTITY_COLLISION);
        CollisionSolver.Result resolved=collisions.move(box,desired,world.shapes(),.6,state.grounded(),profile.modernAxisOrder);
        if(resolved.stepped()) uncertain.add(Uncertainty.STEP_MODEL);
        boolean grounded=resolved.grounded();
        if(y==0 || resolved.stepped()) {
            Aabb below=resolved.box().move(new Vec3(0,-.000001,0));
            grounded=world.shapes().stream().anyMatch(below::intersects);
        }
        double vx=resolved.movement().x()!=x?0:x, vy=resolved.movement().y()!=y?0:y, vz=resolved.movement().z()!=z?0:z;
        EnumSet<Surface> landing=EnumSet.noneOf(Surface.class);
        Aabb landingFeet=resolved.box().move(new Vec3(0,-.001,0));
        for(BlockSample block:world.blocks()) for(Aabb shape:block.shapes()) if(landingFeet.intersects(shape)) {
            landing.addAll(block.surfaces()); break;
        }
        for(Surface surface:landing) addUncertainty(surface,uncertain);
        if(grounded && y<0 && landing.contains(Surface.SLIME) && !input.sneak()) vy=-y;
        if(media.contains(Surface.CLIMBABLE) && (resolved.horizontalCollision() || input.jump())) vy=.2;
        if(media.contains(Surface.COBWEB)) { vx=0; vy=0; vz=0; }
        if(liquid) { double damping=water?.8:.5; vx*=damping; vz*=damping; vy=vy*damping-.02; }
        else {
            if(context.levitation()>=0) vy+=(.05*(context.levitation()+1)-vy)*.2;
            else vy-=context.slowFalling() && vy<=0?.01:.08;
            vy*=.98; vx*=drag; vz*=drag;
        }
        if(support.contains(Surface.SOUL_SAND) || support.contains(Surface.HONEY)) { vx*=.4; vz*=.4; }
        State next=new State(state.feet().add(resolved.movement()),new Vec3(vx,vy,vz),grounded,state.height());
        return new Result(next,resolved.movement(),resolved.horizontalCollision(),resolved.stepped(),uncertain);
    }
    private static Result unchanged(State state,Set<Uncertainty> reasons) { return new Result(state,Vec3.ZERO,false,false,reasons); }
    private static double small(double value,PhysicsProfile profile) { return Math.abs(value)<profile.negligibleMotion?0:value; }
    private static double clamp(double v,double min,double max) { return Math.max(min,Math.min(max,v)); }
    private static void addUncertainty(Surface surface,Set<Uncertainty> reasons) {
        Uncertainty reason=switch(surface) {
            case WATER,LAVA -> Uncertainty.FLUID_FLOW;
            case CLIMBABLE -> Uncertainty.CLIMBABLE;
            case PISTON -> Uncertainty.PISTON;
            case SLIME -> Uncertainty.SLIME;
            case HONEY -> Uncertainty.HONEY;
            case SOUL_SAND -> Uncertainty.SOUL_SAND;
            case COBWEB -> Uncertainty.COBWEB;
            case BUBBLE_COLUMN -> Uncertainty.BUBBLE_COLUMN;
            case SCAFFOLDING -> Uncertainty.SCAFFOLDING;
            case SPECIAL -> Uncertainty.SPECIAL_BLOCK;
            default -> null;
        };
        if(reason!=null) reasons.add(reason);
    }
}
