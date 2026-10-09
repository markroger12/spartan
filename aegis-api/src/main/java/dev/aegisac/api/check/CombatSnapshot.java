package dev.aegisac.api.check;
import java.util.*;
public record CombatSnapshot(long generation,long attacks,long swings,int trackedTargets,
        double swingRate,double intervalVariation,Map<String,CheckSnapshot> checks,List<CombatEvidence> evidence) {
    public CombatSnapshot { checks=Map.copyOf(checks); evidence=List.copyOf(evidence); }
    public static CombatSnapshot empty() { return new CombatSnapshot(0,0,0,0,0,0,Map.of(),List.of()); }
}
