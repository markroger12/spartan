package dev.aegisac.common.check;
import dev.aegisac.api.check.SafePositionSnapshot;
import java.util.Set;
/** One bounded candidate. Verified requires a complete trusted observation chain, not merely a clear box. */
public final class SafePositionTracker {
    private SafePositionSnapshot candidate;
    private int consecutive;
    public void reset() { candidate=null; consecutive=0; }
    public void observe(MovementFrame f,long session,boolean noMismatch,int required,Set<String> reasons) {
        if(!noMismatch || !f.clear() || !f.supported() || f.liquid() || f.climbable()) { reset(); return; }
        if(candidate!=null && (!candidate.world().equals(f.world().world()) || candidate.worldRevision()!=f.world().revision() || candidate.sessionId()!=session)) reset();
        consecutive=Math.min(required,consecutive+1);
        // Uncertain samples cannot build a trusted streak.
        if(!reasons.isEmpty()) consecutive=0;
        candidate=new SafePositionSnapshot(f.world().world(),session,f.world().revision(),f.observedNanos(),
                f.to().x(),f.to().y(),f.to().z(),reasons.isEmpty() && consecutive>=required,consecutive,reasons);
    }
    public SafePositionSnapshot snapshot(long now,long revision,int maximumAgeMillis) {
        if(candidate==null || candidate.worldRevision()!=revision || now-candidate.observedNanos()<0
                || now-candidate.observedNanos()>maximumAgeMillis*1_000_000L) return null;
        return candidate;
    }
}
