package dev.aegisac.api.check;
import java.util.Set;
/** Numeric diagnostic; no raw payload, item metadata or platform objects. */
public record GuardEvidence(String check,String category,long sequence,long observedNanos,long generation,
        double observed,double limit,Set<String> reasons) {
    public GuardEvidence { reasons=Set.copyOf(reasons); }
}
