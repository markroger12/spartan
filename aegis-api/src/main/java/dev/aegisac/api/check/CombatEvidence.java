package dev.aegisac.api.check;
import java.util.Set;
/** Experimental observation, never an instruction to punish. */
public record CombatEvidence(String check,long sequence,long observedNanos,long generation,int target,
                             double observed,double expected,double excess,boolean diagnostic,Set<String> reasons) {
    public CombatEvidence { reasons=Set.copyOf(reasons); }
}
