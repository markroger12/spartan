package dev.aegisac.common.combat;

import dev.aegisac.api.check.CombatSnapshot;
import dev.aegisac.api.player.*;
import dev.aegisac.common.check.*;
import dev.aegisac.common.collision.Vec3;
import dev.aegisac.common.config.*;
import dev.aegisac.common.connection.TransactionTracker;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import java.util.*;

/** Worker-owned bounded combat observations. No observation here establishes client execution. */
public final class CombatMonitor {
    public void metrics(dev.aegisac.common.packet.PacketMetrics value) { dispatcher.metrics(value); }
    private Set<String> manualBypasses=Set.of();
    public void manualBypasses(Set<String> values) { manualBypasses=Set.copyOf(values); }
    private Set<String> combined(Set<String> values) { if(manualBypasses.isEmpty()) return values; var result=new HashSet<>(values); result.addAll(manualBypasses); return Set.copyOf(result); }

    private record Pending(Set<String> reasons,Set<String> bypasses,boolean miss,boolean switching,boolean regularAttacks) { }
    private record Recent(long time,int target) { }
    private record Velocity(long time,Vec3 position,Vec3 impulse,Set<String> reasons) { }
    private final CombatDispatcher dispatcher=new CombatDispatcher();
    public void output(java.util.function.Consumer<dev.aegisac.common.output.Detection> sink) { dispatcher.output(sink); }
    private TargetHistory targets;
    private SwingMatcher matcher;
    private RollingStatistics intervals,rotations,attackIntervals;
    private final Map<Long,Pending> pending=new HashMap<>();
    private final ArrayDeque<Recent> recent=new ArrayDeque<>();
    private long generation=-1,attacks,swings,lastSwing,lastMovement,lastRotation,graceUntil,lastObserved;
    private long lastAttack;
    private boolean attackSeen;
    private boolean swingSeen,movementSeen,rotationSeen,observed;
    private float yaw,pitch;
    private double y,descent,turn;
    private int tinyDescents;
    private long lastTinyMovement;
    private Velocity velocity;
    public void reset(long now,int graceMillis,String reason) {
        if(targets!=null) targets.clear();
        if(matcher!=null) matcher.clear();
        if(intervals!=null) intervals.clear();
        if(rotations!=null) rotations.clear();
        if(attackIntervals!=null) attackIntervals.clear();
        pending.clear(); recent.clear(); velocity=null;
        attackSeen=swingSeen=movementSeen=rotationSeen=observed=false; attacks=swings=0; tinyDescents=0; descent=turn=0;
        graceUntil=now+graceMillis*1_000_000L; dispatcher.clear(reason);
    }
    public void process(ConfigSnapshot config,PacketFrame frame,MovementSnapshot movement,
            TransactionTracker.Acknowledgement acknowledgement,ConnectionSnapshot connection,
            OwnerObservation owner,CombatOwnerObservation dimensions,ServerTickHealth.Snapshot health,
            BedrockStatus edition,int localEntity) {
        CombatSettings s=config.combat(); long now=frame.observedNanos();
        if(generation!=config.generation()) {
            targets=new TargetHistory(s); matcher=new SwingMatcher(s.pendingAttacks());
            intervals=new RollingStatistics(s.statisticsSize()); rotations=new RollingStatistics(s.statisticsSize()); attackIntervals=new RollingStatistics(s.statisticsSize());
            generation=config.generation(); reset(now,s.graceMillis(),"CONFIGURATION_CHANGED");
        }
        if(!s.enabled()) { reset(now,0,"CHECK_DISABLED"); return; }
        if(observed && now<lastObserved) reset(now,s.graceMillis(),"OBSERVATION_ORDER_UNKNOWN");
        observed=true; lastObserved=now;
        if(frame.packet().kind()==PacketKind.TELEPORT) {
            reset(now,s.graceMillis(),"TELEPORT_OR_WORLD_CHANGE"); return;
        }
        if(frame.packet().kind()==PacketKind.WORLD_CHANGE) velocity=null;
        targets.observe(frame,acknowledgement);
        Set<String> gates=gates(config,frame,connection,owner,health,edition);
        Set<String> bypasses=combined(owner!=null&&config.exemptions().enabled()&&config.exemptions().permissions()?owner.bypasses():Set.of());
        if(frame.direction()==PacketDirection.OUTBOUND && frame.packet() instanceof Impulse impulse
                && (impulse.explosion()||impulse.entityId()==localEntity)) {
            velocity=null;
            if(positionValid(movement)&&finite(impulse.x(),impulse.y(),impulse.z())) {
                Vec3 vector=new Vec3(impulse.x(),impulse.y(),impulse.z());
                if(vector.lengthSquared()>1e-8) velocity=new Velocity(now,position(movement),vector,Set.copyOf(gates));
            }
        }
        if(frame.direction()==PacketDirection.INBOUND && frame.packet() instanceof Movement packet) {
            if(packet.position()&&!finite(packet.x(),packet.y(),packet.z()) || packet.rotation()&&(!Float.isFinite(packet.yaw())||!Float.isFinite(packet.pitch()))) {
                reset(now,s.graceMillis(),"INVALID_MOVEMENT_OBSERVATION"); return;
            }
            if(movementSeen && now-lastMovement>1_000_000_000L) { rotations.clear(); tinyDescents=0; movementSeen=rotationSeen=false; }
            descent=packet.position()&&movementSeen?y-packet.y():0;
            if(packet.rotation()&&Float.isFinite(packet.yaw())&&Float.isFinite(packet.pitch())) {
                turn=rotationSeen?Math.hypot(CombatGeometry.wrappedDelta(yaw,packet.yaw()),(double)packet.pitch()-pitch):0;
                if(turn>1e-5) rotations.add(turn);
                yaw=packet.yaw(); pitch=packet.pitch(); lastRotation=now; rotationSeen=true;
            }
            if(packet.position()&&finite(packet.x(),packet.y(),packet.z())) {
                y=packet.y(); movementSeen=true; lastMovement=now;
                if(velocity!=null && now-velocity.time()>=s.velocityWindowMillis()*1_000_000L) {
                    var reasons=new HashSet<>(gates); reasons.addAll(velocity.reasons());
                    reasons.add("VELOCITY_APPLICATION_UNCONFIRMED"); reasons.add("COLLISION_CONTEXT_UNKNOWN");
                    Vec3 delta=position(movement).subtract(velocity.position()),v=velocity.impulse();
                    double fraction=(delta.x()*v.x()+delta.y()*v.y()+delta.z()*v.z())/v.lengthSquared();
                    evaluate(CombatId.VelocityA,Check.Evaluation.above(s.velocityFraction()-fraction,0),frame,-1,s,reasons,bypasses);
                    velocity=null;
                }
            }
        }
        if(frame.direction()==PacketDirection.INBOUND && frame.packet() instanceof Action action
                && action.kind()==PacketKind.SWING && action.action().equals("MAIN_HAND")) {
            swings++; matcher.swing(now,s.swingWindowMillis()*1_000_000L);
            if(swingSeen) {
                long delta=now-lastSwing;
                if(delta<=0||delta>1_000_000_000L) intervals.clear(); else intervals.add(delta/1_000_000.0);
            }
            swingSeen=true; lastSwing=now;
            var stats=intervals.summary(1);
            var reasons=new HashSet<>(gates); reasons.add("SWING_TIMING_NOT_PHYSICAL_CLICKS");
            evaluate(CombatId.AutoClickerA,stats.samples()<s.minimumStatistics()?Check.Evaluation.absent():
                    flag(stats.variation()<=s.clickVariation()&&stats.repeatRatio()>=s.repeatedIntervals()),frame,-1,s,reasons,bypasses);
        }
        if(frame.direction()==PacketDirection.INBOUND && frame.packet() instanceof Interaction attack && attack.action().equals("ATTACK")) {
            attacks++;
            if(attackSeen) {
                long delta=now-lastAttack;
                if(delta<=0||delta>1_000_000_000L) attackIntervals.clear(); else attackIntervals.add(delta/1_000_000.0);
            }
            attackSeen=true; lastAttack=now;
            var attackStats=attackIntervals.summary(1);
            boolean regularAttacks=attackStats.samples()>=s.minimumStatistics()&&attackStats.variation()<=s.clickVariation()&&attackStats.repeatRatio()>=s.repeatedIntervals();
            evaluate(CombatId.InvalidAttackA,localEntity<0?Check.Evaluation.absent():flag(attack.entityId()==localEntity),frame,attack.entityId(),s,gates,bypasses);
            evaluate(CombatId.ImpossibleInteractionA,!movement.rotationKnown()||!Float.isFinite(movement.pitch())?Check.Evaluation.absent():
                    Check.Evaluation.above(Math.abs(movement.pitch()),90),frame,attack.entityId(),s,gates,bypasses);
            while(!recent.isEmpty()&&now-recent.peekFirst().time()>s.targetWindowMillis()*1_000_000L) recent.removeFirst();
            if(recent.size()>=s.statisticsSize()) recent.removeFirst();
            recent.addLast(new Recent(now,attack.entityId()));
            long unique=recent.stream().map(Recent::target).distinct().count();
            boolean switching=unique>1;
            evaluate(CombatId.MultiAuraA,Check.Evaluation.above(unique,1),frame,attack.entityId(),s,gates,bypasses);
            List<Recent> pattern=List.copyOf(recent); int n=pattern.size();
            boolean alternating=n>=4&&pattern.get(n-1).target()==pattern.get(n-3).target()
                    &&pattern.get(n-2).target()==pattern.get(n-4).target()&&pattern.get(n-1).target()!=pattern.get(n-2).target();
            evaluate(CombatId.AttackPatternA,n<4?Check.Evaluation.absent():flag(alternating),frame,attack.entityId(),s,gates,bypasses);
            var view=targets.view(attack.entityId(),now,s); var geometryReasons=new HashSet<>(gates); geometryReasons.addAll(view.reasons());
            CombatGeometry.Result geometry=null;
            if(positionValid(movement)&&movement.rotationKnown()&&Float.isFinite(movement.yaw())&&Float.isFinite(movement.pitch())
                    && now-movement.observedNanos()>=0 && now-movement.observedNanos()<=s.maximumDelayMillis()*1_000_000L
                    && dimensions!=null && now-dimensions.observedNanos()>=0 && now-dimensions.observedNanos()<=s.maximumDelayMillis()*1_000_000L
                    && !view.boxes().isEmpty()) {
                geometry=CombatGeometry.evaluate(position(movement).add(new Vec3(0,dimensions.eyeHeight(),0)),movement.yaw(),movement.pitch(),view.boxes());
            } else geometryReasons.add("ATTACK_GEOMETRY_UNAVAILABLE");
            evaluate(CombatId.ReachA,geometry==null?Check.Evaluation.absent():Check.Evaluation.above(geometry.minimumDistance(),dimensions.entityRange()),frame,attack.entityId(),s,geometryReasons,bypasses);
            evaluate(CombatId.HitboxA,geometry==null?Check.Evaluation.absent():flag(!geometry.hit()),frame,attack.entityId(),s,geometryReasons,bypasses);
            evaluate(CombatId.AimA,geometry==null||now-lastRotation>s.targetWindowMillis()*1_000_000L?Check.Evaluation.absent():
                    flag(turn>=s.snapDegrees()&&geometry.alignmentDegrees()<=s.alignmentDegrees()),frame,attack.entityId(),s,geometryReasons,bypasses);
            var rotation=rotations.summary(.001);
            evaluate(CombatId.RotationA,rotation.samples()<s.minimumStatistics()||now-lastRotation>s.maximumDelayMillis()*1_000_000L?Check.Evaluation.absent():
                    flag(rotation.variation()<=s.rotationVariation()),frame,attack.entityId(),s,gates,bypasses);
            evaluate(CombatId.AimAssistA,geometry==null||rotation.samples()<s.minimumStatistics()||now-lastRotation>s.maximumDelayMillis()*1_000_000L?Check.Evaluation.absent():
                    flag(rotation.variation()<=s.rotationVariation()&&geometry.alignmentDegrees()<=s.alignmentDegrees()),frame,attack.entityId(),s,geometryReasons,bypasses);
            if(lastTinyMovement!=lastMovement) tinyDescents=movementSeen&&!movement.onGround()&&descent>0&&descent<s.tinyFall()&&now-lastMovement<=s.maximumDelayMillis()*1_000_000L?Math.min(s.minimumStatistics(),tinyDescents+1):0;
            lastTinyMovement=lastMovement;
            var criticalReasons=new HashSet<>(gates); criticalReasons.add("CRITICAL_DAMAGE_UNCONFIRMED");
            evaluate(CombatId.CriticalsA,flag(tinyDescents>=s.minimumStatistics()),frame,attack.entityId(),s,criticalReasons,bypasses);
            matcher.attack(new SwingMatcher.Attack(frame.sequence(),now,attack.entityId()),s.swingWindowMillis()*1_000_000L);
            if(matcher.consumeLoss()) { pending.clear(); dispatcher.clear("SWING_HISTORY_OVERFLOW"); }
            pending.put(frame.sequence(),new Pending(Set.copyOf(geometryReasons),Set.copyOf(bypasses),geometry!=null&&!geometry.hit(),switching,regularAttacks));
        }
        for(var expired:matcher.expire(now,s.swingWindowMillis()*1_000_000L)) {
            Pending p=pending.remove(expired.attack().sequence()); if(p==null) continue;
            var reasons=new HashSet<>(gates); reasons.addAll(p.reasons());
            var bypass=new HashSet<>(bypasses); bypass.addAll(p.bypasses());
            dispatcher.evaluate(CombatId.NoSwingA,flag(!expired.matched()),expired.attack().sequence(),now,expired.attack().target(),generation,s,reasons,bypass);
            dispatcher.evaluate(CombatId.AttackTimingA,flag(!expired.matched()&&p.regularAttacks()),expired.attack().sequence(),now,expired.attack().target(),generation,s,reasons,bypass);
            dispatcher.evaluate(CombatId.KillAuraA,flag(!expired.matched()&&p.miss()&&p.switching()),expired.attack().sequence(),now,expired.attack().target(),generation,s,reasons,bypass);
        }
    }
    private Set<String> gates(ConfigSnapshot config,PacketFrame frame,ConnectionSnapshot connection,OwnerObservation owner,ServerTickHealth.Snapshot health,BedrockStatus edition) {
        var result=new HashSet<String>(); long now=frame.observedNanos(),age=config.combat().maximumDelayMillis()*1_000_000L;
        result.add("CLIENT_ACTIONS_UNCONFIRMED");
        if(edition!=BedrockStatus.JAVA) result.add("UNKNOWN_EDITION");
        if(!frame.protocol().known()) result.add("UNKNOWN_PROTOCOL");
        if(now<graceUntil) result.add("TIMING_GRACE");
        if(connection==null||connection.uncertain()||connection.lossEpoch()!=connection.processedEpoch()||connection.processingDelayNanos()>age) result.add("CONNECTION_UNCERTAIN");
        if(!health.known()||now-health.observedNanos()<0||now-health.observedNanos()>age||health.intervalNanos()>age||health.tps()<18) result.add("SERVER_HEALTH_UNKNOWN");
        if(owner==null||now-owner.observedNanos()<0||now-owner.observedNanos()>age) result.add("OWNER_STATE_UNKNOWN");
        if(owner!=null) {
            if(owner.vehicle()||owner.gliding()) result.add("SPECIAL_MOVEMENT");
            if(config.exemptions().enabled()) {
                if(config.exemptions().worlds().contains(owner.world())) result.add("WORLD_EXEMPT");
                if(config.exemptions().creativeOrSpectator()&&owner.creativeOrSpectator()) result.add("GAMEMODE");
                if(config.exemptions().flight()&&owner.flight()) result.add("FLIGHT_ALLOWED");
            }
        }
        return result;
    }
    private void evaluate(CombatId id,Check.Evaluation evaluation,PacketFrame frame,int target,CombatSettings settings,Set<String> reasons,Set<String> bypasses) {
        dispatcher.evaluate(id,evaluation,frame.sequence(),frame.observedNanos(),target,generation,settings,reasons,bypasses);
    }
    private static Check.Evaluation flag(boolean value) { return Check.Evaluation.above(value?1:0,0); }
    private static boolean finite(double x,double y,double z) { return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<30_000_001&&Math.abs(y)<30_000_001&&Math.abs(z)<30_000_001; }
    private static boolean positionValid(MovementSnapshot m) { return m.positionKnown()&&finite(m.x(),m.y(),m.z()); }
    private static Vec3 position(MovementSnapshot m) { return new Vec3(m.x(),m.y(),m.z()); }
    public CombatSnapshot snapshot() { return snapshot(lastObserved); }
    public CombatSnapshot snapshot(long now) {
        var stats=intervals==null?new RollingStatistics.Summary(0,0,0,0,0):intervals.summary(1);
        return new CombatSnapshot(generation,attacks,swings,targets==null?0:targets.size(),swingSeen&&now-lastSwing>=0&&now-lastSwing<=1_000_000_000L&&stats.mean()>0?1000/stats.mean():0,stats.variation(),dispatcher.states(),dispatcher.evidence());
    }
}
