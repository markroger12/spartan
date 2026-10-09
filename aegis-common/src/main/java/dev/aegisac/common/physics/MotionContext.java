package dev.aegisac.common.physics;
import java.util.Set;
/** Values belong to the captured server tick, not an acknowledged client tick. */
public record MotionContext(double movementSpeed, double jumpStrength, int jumpBoost, int levitation,
                            boolean slowFalling, Set<Uncertainty> uncertainty) {
    public static final MotionContext NORMAL = new MotionContext(.1,.42,-1,-1,false,Set.of());
    public MotionContext {
        if (!Double.isFinite(movementSpeed) || movementSpeed<0 || movementSpeed>4
                || !Double.isFinite(jumpStrength) || jumpStrength<0 || jumpStrength>4
                || jumpBoost < -1 || jumpBoost>255 || levitation < -1 || levitation>255)
            throw new IllegalArgumentException("Invalid motion context");
        uncertainty=Set.copyOf(uncertainty);
    }
}
