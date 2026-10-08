package dev.aegisac.common.guard;
/** Owner-thread scalar attributes, never Bukkit references. */
public record GuardOwnerObservation(long observedNanos,double eyeHeight,double blockRange) {
    public GuardOwnerObservation {
        if(!Double.isFinite(eyeHeight)||eyeHeight<=0||eyeHeight>64||!Double.isFinite(blockRange)||blockRange<0||blockRange>128)
            throw new IllegalArgumentException("Invalid block interaction attributes");
    }
}
