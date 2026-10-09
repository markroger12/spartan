package dev.aegisac.common.output;
import java.util.Set;
/** Immutable evidence produced by an evaluator, not a command or punishment request. */
public record Detection(String check,String category,long sequence,long time,long generation,double observed,double expected,
                        double buffer,boolean diagnostic,boolean experimental,Set<String> reasons) {
    public Detection { reasons=Set.copyOf(reasons); }
    public boolean scoreEligible() { return !diagnostic&&reasons.isEmpty()&&Double.isFinite(observed)&&Double.isFinite(expected)&&Double.isFinite(buffer); }
}
