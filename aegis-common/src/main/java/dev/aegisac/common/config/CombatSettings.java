package dev.aegisac.common.config;
import dev.aegisac.common.combat.CombatId;
import java.util.*;
/** Resource budgets are restart-bound; thresholds/gates are immutable live policy. */
public record CombatSettings(boolean enabled,int maximumTargets,int historySize,int pendingAttacks,int statisticsSize,
        int evidenceCapacity,int historyMillis,int interpolationMillis,int swingWindowMillis,int targetWindowMillis,
        int minimumStatistics,int graceMillis,int maximumDelayMillis,int velocityWindowMillis,
        double boxExpansion,double clickVariation,double repeatedIntervals,double snapDegrees,double alignmentDegrees,
        double rotationVariation,double tinyFall,double velocityFraction,Map<CombatId,MovementSettings.Rule> rules) {
    public CombatSettings { rules=Map.copyOf(rules); }
    public boolean sameResources(CombatSettings other) {
        return maximumTargets==other.maximumTargets && historySize==other.historySize
                && pendingAttacks==other.pendingAttacks && statisticsSize==other.statisticsSize;
    }
    public static CombatSettings defaults() {
        var rules=new EnumMap<CombatId,MovementSettings.Rule>(CombatId.class);
        for(var id:CombatId.values()) rules.put(id,new MovementSettings.Rule(true,id==CombatId.ReachA?.1:0,1,.25,4,3,1000,"diagnostic"));
        return new CombatSettings(true,64,8,32,40,32,2000,150,200,100,24,2000,250,400,
                .1,.025,.85,60,1,.02,.03,.1,rules);
    }
}
