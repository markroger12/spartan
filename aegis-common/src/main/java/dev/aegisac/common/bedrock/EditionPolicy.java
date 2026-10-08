package dev.aegisac.common.bedrock;
import dev.aegisac.api.player.BedrockStatus;
import dev.aegisac.common.config.*;
import java.util.*;
/** Derive an immutable session policy only at configuration/edition transitions, not on every packet. */
public final class EditionPolicy {
    private EditionPolicy() { }
    private static boolean enabled(ConfigSnapshot config,String id,BedrockStatus edition) {
        return edition==BedrockStatus.JAVA?config.edition().javaChecks().get(id):config.edition().bedrockChecks().get(id).mode()!=EditionSettings.Mode.BEDROCK_DISABLED;
    }
    private static double limit(ConfigSnapshot config,String id,double original,BedrockStatus edition) {
        if(edition==BedrockStatus.JAVA) return original;
        var rule=config.edition().bedrockChecks().get(id);
        return rule.mode()==EditionSettings.Mode.BEDROCK_ADJUSTED?original/rule.multiplier()+rule.extraTolerance():original;
    }
    private static MovementSettings.Rule rule(ConfigSnapshot config,String id,MovementSettings.Rule r,BedrockStatus edition) {
        boolean active=r.enabled()&&enabled(config,id,edition)&&(edition==BedrockStatus.JAVA||!r.bedrockMode().equals("disabled"));
        return new MovementSettings.Rule(active,limit(config,id,r.tolerance(),edition),r.increment(),r.decay(),r.threshold(),r.minimumSamples(),r.cooldownMillis(),r.bedrockMode());
    }
    public static ConfigSnapshot apply(ConfigSnapshot c,BedrockStatus edition) {
        var m=c.movement(); var movementRules=new EnumMap<dev.aegisac.common.check.CheckId,MovementSettings.Rule>(dev.aegisac.common.check.CheckId.class);
        m.rules().forEach((id,r)->movementRules.put(id,rule(c,id.name(),r,edition)));
        var movement=new MovementSettings(m.enabled(),m.evidenceCapacity(),m.joinGraceMillis(),m.teleportGraceMillis(),m.velocityGraceMillis(),m.lagGraceMillis(),m.maximumDelayMillis(),m.maximumJitterMillis(),m.safeSamples(),m.safeAgeMillis(),m.timing(),movementRules);
        var b=c.combat(); var combatRules=new EnumMap<dev.aegisac.common.combat.CombatId,MovementSettings.Rule>(dev.aegisac.common.combat.CombatId.class);
        b.rules().forEach((id,r)->combatRules.put(id,rule(c,id.name(),r,edition)));
        var combat=new CombatSettings(b.enabled(),b.maximumTargets(),b.historySize(),b.pendingAttacks(),b.statisticsSize(),b.evidenceCapacity(),b.historyMillis(),b.interpolationMillis(),b.swingWindowMillis(),b.targetWindowMillis(),b.minimumStatistics(),b.graceMillis(),b.maximumDelayMillis(),b.velocityWindowMillis(),b.boxExpansion(),b.clickVariation(),b.repeatedIntervals(),b.snapDegrees(),b.alignmentDegrees(),b.rotationVariation(),b.tinyFall(),b.velocityFraction(),combatRules);
        var guardRules=new EnumMap<dev.aegisac.common.guard.GuardId,GuardSettings.Rule>(dev.aegisac.common.guard.GuardId.class);
        c.guard().rules().forEach((id,r)->guardRules.put(id,new GuardSettings.Rule(r.enabled()&&enabled(c,id.name(),edition)&&(edition==BedrockStatus.JAVA||!r.bedrockMode().equals("disabled")),limit(c,id.name(),r.limit(),edition),r.cooldownMillis(),r.bedrockMode())));
        return new ConfigSnapshot(c.generation(),c.defaultProfile(),c.worldProfiles(),c.packetMetrics(),c.pipeline(),c.physics(),movement,combat,new GuardSettings(c.guard().categories(),guardRules),c.edition(),c.output(),c.exemptions(),c.messages(),c.documents());
    }
}
