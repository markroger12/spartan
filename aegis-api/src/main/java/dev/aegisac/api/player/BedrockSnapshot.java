package dev.aegisac.api.player;
import java.util.*;
/** Translated Java-side observations, never native Bedrock input or proof of client physics. */
public record BedrockSnapshot(long generation,String status,Map<String,List<Observation>> histories,Set<String> reasons) {
    public record Observation(long sequence,long observedNanos,String action,Map<String,Double> values) {
        public Observation { values=Map.copyOf(values); }
    }
    public BedrockSnapshot {
        Map<String,List<Observation>> copy=new LinkedHashMap<>(); histories.forEach((k,v)->copy.put(k,List.copyOf(v)));
        histories=Map.copyOf(copy); reasons=Set.copyOf(reasons);
    }
    public static BedrockSnapshot empty(String reason) { return new BedrockSnapshot(0,"UNAVAILABLE",Map.of(),Set.of(reason)); }
}
