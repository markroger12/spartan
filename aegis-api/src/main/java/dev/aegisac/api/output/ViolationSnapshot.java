package dev.aegisac.api.output;
import java.util.Map;
/** Session-local policy scores, not a calibrated probability of cheating. */
public record ViolationSnapshot(double totalRisk,double confidence,int findings,Map<String,Double> checks,Map<String,Double> categories) {
    public ViolationSnapshot { checks=Map.copyOf(checks); categories=Map.copyOf(categories); }
    public static ViolationSnapshot empty() { return new ViolationSnapshot(0,0,0,Map.of(),Map.of()); }
}
