package dev.aegisac.common.check;
import dev.aegisac.common.collision.Vec3;
import dev.aegisac.common.world.WorldSnapshot;
import java.util.Set;
/** Worker-owned numeric evidence from the full candidate set, not just the closest candidate. */
public record MovementFrame(long sequence,long observedNanos,Vec3 from,Vec3 to,Vec3 previousDelta,
        double maximumHorizontal,double minimumY,double maximumY,double maximumAcceleration,
        boolean wasSupported,boolean supported,boolean clear,boolean groundClaim,boolean sprint,boolean sneak,
        boolean climbable,boolean liquid,double phaseDistance,double fallDistance,WorldSnapshot world,Set<String> reasons) {
    public MovementFrame { reasons=Set.copyOf(reasons); }
    public Vec3 delta() { return to.subtract(from); }
    public double horizontal() { return Math.sqrt(delta().horizontalSquared()); }
}
