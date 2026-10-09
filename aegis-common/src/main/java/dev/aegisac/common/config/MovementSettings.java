package dev.aegisac.common.config;
import dev.aegisac.common.check.CheckId;
import java.util.*;
/** Whole immutable live generation. No option enables enforcement or claims missing models. */
public record MovementSettings(boolean enabled,int evidenceCapacity,int joinGraceMillis,int teleportGraceMillis,
        int velocityGraceMillis,int lagGraceMillis,int maximumDelayMillis,int maximumJitterMillis,
        int safeSamples,int safeAgeMillis,Timing timing,Map<CheckId,Rule> rules) {
    public record Timing(int windowMillis,int nominalTickMillis,int bankMillis,int silenceMillis,int burstMillis,int burstPackets) { }
    public record Rule(boolean enabled,double tolerance,double increment,double decay,double threshold,
                       int minimumSamples,int cooldownMillis,String bedrockMode) { }
    public MovementSettings { rules=Map.copyOf(rules); }
    public static MovementSettings defaults() {
        EnumMap<CheckId,Rule> rules=new EnumMap<>(CheckId.class);
        for(CheckId id:CheckId.values()) rules.put(id,new Rule(id.implemented,id==CheckId.TimerA?150:id==CheckId.NoFallA?3:.03,1,.25,4,3,1000,"diagnostic"));
        return new MovementSettings(true,32,3000,2000,1500,2000,250,100,5,2000,new Timing(2000,50,250,1000,200,8),rules);
    }
}
