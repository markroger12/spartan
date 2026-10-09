package dev.aegisac.common.config;
import java.util.*;
public record EditionSettings(Identity identity,boolean historyEnabled,int historySize,int historyAgeMillis,
                              Map<String,Boolean> javaChecks,Map<String,Rule> bedrockChecks) {
    public enum Mode { BEDROCK_SUPPORTED,BEDROCK_ADJUSTED,BEDROCK_DISABLED }
    public record Rule(Mode mode,double multiplier,double extraTolerance) { }
    public record Identity(boolean floodgate,boolean geyser,int pollMillis,int queriesPerTick,int maximumAgeMillis,
                           boolean authoritativeNegatives,boolean javaOnlyServer) { }
    public EditionSettings { javaChecks=Map.copyOf(javaChecks); bedrockChecks=Map.copyOf(bedrockChecks); }
    public static Map<String,Boolean> catalog() {
        Map<String,Boolean> entries=new LinkedHashMap<>();
        for(var id:dev.aegisac.common.check.CheckId.values()) entries.put(id.name(),id.implemented);
        for(var id:dev.aegisac.common.combat.CombatId.values()) entries.put(id.name(),true);
        for(var id:dev.aegisac.common.guard.GuardId.values()) entries.put(id.name(),id.implemented);
        return Map.copyOf(entries);
    }
    public static EditionSettings defaults() {
        var javaChecks=new LinkedHashMap<String,Boolean>(); var bedrock=new LinkedHashMap<String,Rule>();
        var supported=Set.of("BadPacketsA","InvalidPositionA","InvalidPitchA","InvalidSlotA","InvalidEntityInteractionA","InvalidAttackA","ImpossibleInteractionA","PayloadSizeA");
        var adjusted=Set.of("PacketSpamA","MovementSpamA","InteractionSpamA","PayloadSpamA","FastPlaceA","FastUseA","NukerA");
        catalog().forEach((id,implemented)-> { javaChecks.put(id,implemented);
            Mode mode=supported.contains(id)?Mode.BEDROCK_SUPPORTED:adjusted.contains(id)?Mode.BEDROCK_ADJUSTED:Mode.BEDROCK_DISABLED;
            bedrock.put(id,new Rule(mode,mode==Mode.BEDROCK_ADJUSTED?.5:1,0)); });
        return new EditionSettings(new Identity(true,true,1000,8,3000,false,false),true,16,5000,javaChecks,bedrock);
    }
}
