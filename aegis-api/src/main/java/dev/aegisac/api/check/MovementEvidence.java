package dev.aegisac.api.check;
import java.util.Set;
/** Compact immutable evidence. No output in Phase 4 authorizes enforcement. */
public record MovementEvidence(String check,long sequence,long observedNanos,long configurationGeneration,
                               double observed,double expected,double excess,boolean diagnostic,Set<String> reasons) {
    public MovementEvidence { reasons=Set.copyOf(reasons); }
}
