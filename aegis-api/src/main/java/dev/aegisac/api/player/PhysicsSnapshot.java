package dev.aegisac.api.player;
import java.util.Set;
/** Experimental one-tick diagnostics. Residual is distance, NOT a violation or verified speed limit. */
public record PhysicsSnapshot(long sequence, long worldRevision, String profile, int candidates,
                              double residual, double predictedX, double predictedY, double predictedZ,
                              boolean grounded, Set<String> uncertainty) {
    public PhysicsSnapshot { uncertainty=Set.copyOf(uncertainty); }
    public static PhysicsSnapshot unavailable(String reason) {
        return new PhysicsSnapshot(0,-1,"unsupported",0,-1,0,0,0,false,Set.of(reason));
    }
}
