package dev.aegisac.api.check;
import java.util.*;
public record GuardSnapshot(long generation,Map<String,CheckSnapshot> checks,List<GuardEvidence> evidence) {
    public GuardSnapshot { checks=Map.copyOf(checks); evidence=List.copyOf(evidence); }
    public static GuardSnapshot empty() { return new GuardSnapshot(0,Map.of(),List.of()); }
}
