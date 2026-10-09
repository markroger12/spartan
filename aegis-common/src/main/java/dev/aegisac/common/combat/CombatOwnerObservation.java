package dev.aegisac.common.combat;
/** Server-side eye/range attributes sampled on the owning thread, not acknowledged client state. */
public record CombatOwnerObservation(long observedNanos,double eyeHeight,double entityRange) {
    public CombatOwnerObservation {
        if(!Double.isFinite(eyeHeight)||eyeHeight<=0||eyeHeight>64||!Double.isFinite(entityRange)||entityRange<0||entityRange>128)
            throw new IllegalArgumentException("Unsupported combat attributes");
    }
}
