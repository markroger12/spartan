package dev.aegisac.common.check;
/** Ordinary movement evaluators. Context-specific IDs are correlated signals, never independent risk multipliers. */
public final class MovementCheck implements Check<MovementFrame> {
    private final CheckId id;
    public MovementCheck(CheckId id) {
        if(!id.implemented || id.timing()) throw new IllegalArgumentException("Not a geometry evaluator: "+id);
        this.id=id;
    }
    @Override public CheckId id() { return id; }
    @Override public Evaluation evaluate(MovementFrame f) {
        var delta=f.delta();
        return switch(id) {
            case SpeedA -> Evaluation.above(f.horizontal(),f.maximumHorizontal());
            case SprintA -> f.sprint()?Evaluation.above(f.horizontal(),f.maximumHorizontal()):Evaluation.absent();
            case SneakA -> f.sneak()?Evaluation.above(f.horizontal(),f.maximumHorizontal()):Evaluation.absent();
            case FlyA -> !f.supported() && delta.y()>=0 && f.maximumY()<0 ? Evaluation.above(delta.y(),f.maximumY()):Evaluation.absent();
            case GroundA -> f.groundClaim()?Evaluation.above(f.supported()?0:1,0):Evaluation.absent();
            case NoFallA -> f.groundClaim() && !f.supported() && f.fallDistance()>0 ? Evaluation.above(f.fallDistance(),0):Evaluation.absent();
            case StepA -> f.wasSupported() && f.supported() && delta.y()>0 ? Evaluation.above(delta.y(),f.maximumY()):Evaluation.absent();
            case ClimbA -> f.climbable()?Evaluation.above(delta.y(),f.maximumY()):Evaluation.absent();
            case LiquidA -> f.liquid()?vertical(f):Evaluation.absent();
            case PhaseA -> Evaluation.above(f.phaseDistance(),0);
            case AccelerationA -> Evaluation.above(Math.sqrt(new dev.aegisac.common.collision.Vec3(
                    delta.x()-f.previousDelta().x(),0,delta.z()-f.previousDelta().z()).horizontalSquared()),f.maximumAcceleration());
            case GravityA -> !f.supported()?vertical(f):Evaluation.absent();
            case AirJumpA -> !f.wasSupported() && f.previousDelta().y()<=0 && delta.y()>0 && f.maximumY()<=0
                    ? Evaluation.above(delta.y(),f.maximumY()):Evaluation.absent();
            default -> throw new IllegalStateException("No evaluator for "+id);
        };
    }
    private Evaluation vertical(MovementFrame f) {
        double y=f.delta().y(); double expected=Math.max(f.minimumY(),Math.min(f.maximumY(),y));
        return new Evaluation(true,y,expected,Math.abs(y-expected));
    }
}
