package dev.aegisac.api.check;
import java.util.Set;
import java.util.UUID;
/** A collision-clear candidate is not necessarily verified legal movement. Revalidation is always required. */
public record SafePositionSnapshot(UUID world,long sessionId,long worldRevision,long observedNanos,
                                   double x,double y,double z,boolean verified,int consecutiveSamples,Set<String> reasons) {
    public SafePositionSnapshot { reasons=Set.copyOf(reasons); }
}
