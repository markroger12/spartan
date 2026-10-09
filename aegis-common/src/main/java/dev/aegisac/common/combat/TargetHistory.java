package dev.aegisac.common.combat;
import dev.aegisac.common.collision.*;
import dev.aegisac.common.config.CombatSettings;
import dev.aegisac.common.connection.TransactionTracker;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import java.util.*;
/** Per-viewer entity history. Observed ping acknowledgement is not proof of socket write order. */
public final class TargetHistory {
    public record Sample(long sequence,long time,Vec3 position) { }
    public record View(List<Aabb> boxes,boolean known,long acknowledgedSequence,Set<String> reasons) {
        public View { boxes=List.copyOf(boxes); reasons=Set.copyOf(reasons); }
    }
    private static final class Target {
        final boolean player;
        final ArrayDeque<Sample> samples=new ArrayDeque<>();
        boolean metadata,discontinuity,trimmed;
        Target(boolean player) { this.player=player; }
    }
    private final LinkedHashMap<Integer,Target> targets=new LinkedHashMap<>();
    private final LinkedHashMap<Long,Long> fences=new LinkedHashMap<>();
    private final int capacity,depth;
    private long acknowledgedSequence;
    public TargetHistory(CombatSettings settings) { capacity=settings.maximumTargets(); depth=settings.historySize(); }
    public int size() { return targets.size(); }
    public void clear() { targets.clear(); fences.clear(); acknowledgedSequence=0; }
    public void observe(PacketFrame frame,TransactionTracker.Acknowledgement acknowledgement) {
        if(frame.packet() instanceof Timing timing && timing.type()==TimingKind.PING) {
            if(frame.direction()==PacketDirection.OUTBOUND) {
                if(fences.size()>=capacity) fences.remove(fences.keySet().iterator().next());
                fences.putIfAbsent(timing.id(),frame.sequence());
            } else {
                Long sequence=fences.remove(timing.id());
                if(sequence!=null && acknowledgement!=null && acknowledgement.sampled()) acknowledgedSequence=Math.max(acknowledgedSequence,sequence);
            }
            return;
        }
        if(frame.direction()!=PacketDirection.OUTBOUND) return;
        if(frame.packet() instanceof EntityRemove remove) { remove.entityIds().forEach(targets::remove); return; }
        if(frame.packet() instanceof EntitySpawn spawn) {
            targets.remove(spawn.entityId());
            if(!finite(spawn.x(),spawn.y(),spawn.z())) return;
            if(targets.size()>=capacity) targets.remove(targets.keySet().iterator().next());
            Target target=new Target(spawn.player()); targets.put(spawn.entityId(),target);
            target.samples.add(new Sample(frame.sequence(),frame.observedNanos(),new Vec3(spawn.x(),spawn.y(),spawn.z())));
        } else if(frame.packet() instanceof EntityDimensionsUnknown update) {
            Target target=targets.get(update.entityId()); if(target!=null) target.metadata=true;
        } else if(frame.packet() instanceof EntityMove move) {
            Target target=targets.get(move.entityId()); if(target==null) return; // A move is never a spawn.
            if(!finite(move.x(),move.y(),move.z()) || move.relativeFlags()!=0) { targets.remove(move.entityId()); return; }
            var last=target.samples.peekLast();
            if(last==null || frame.observedNanos()-last.time()<0) { targets.remove(move.entityId()); return; }
            Vec3 value=new Vec3(move.x(),move.y(),move.z());
            if(move.relative()) value=last.position().add(value);
            if(!finite(value.x(),value.y(),value.z())) { targets.remove(move.entityId()); return; }
            if(move.discontinuity()) { target.samples.clear(); target.discontinuity=true; }
            if(target.samples.size()>=depth) { target.samples.removeFirst(); target.trimmed=true; }
            target.samples.addLast(new Sample(frame.sequence(),frame.observedNanos(),value));
        }
    }
    public View view(int entityId,long now,CombatSettings settings) {
        Target target=targets.get(entityId);
        var reasons=new HashSet<String>(); reasons.add("UNCONFIRMED_DELIVERY"); reasons.add("TARGET_DIMENSIONS_UNCONFIRMED");
        if(target==null) return new View(List.of(),false,acknowledgedSequence,Set.of("TARGET_UNKNOWN"));
        if(!target.player) return new View(List.of(),true,acknowledgedSequence,Set.of("UNSUPPORTED_TARGET_TYPE"));
        if(target.metadata) reasons.add("TARGET_METADATA_CHANGED");
        if(target.discontinuity) reasons.add("TARGET_TELEPORT");
        if(target.trimmed) reasons.add("TARGET_HISTORY_TRIMMED");
        List<Sample> retained=target.samples.stream().filter(s->now-s.time()>=0 && now-s.time()<=settings.historyMillis()*1_000_000L).toList();
        if(retained.isEmpty()) return new View(List.of(),true,acknowledgedSequence,Set.of("TARGET_HISTORY_EXPIRED"));
        if(retained.getLast().sequence()>acknowledgedSequence) reasons.add("TARGET_UPDATES_UNACKNOWLEDGED");
        if(retained.getFirst().sequence()>acknowledgedSequence) reasons.add("NO_ACKNOWLEDGED_TARGET_BASELINE");
        List<Aabb> boxes=new ArrayList<>(); Sample previous=null;
        // Include every retained location: broad envelopes prefer missed detections to fabricated precision.
        for(Sample sample:retained) {
            Aabb box=Aabb.player(sample.position(),1.8).expand(settings.boxExpansion(),settings.boxExpansion(),settings.boxExpansion());
            if(previous!=null) {
                if(sample.time()-previous.time()<=settings.interpolationMillis()*1_000_000L) {
                    Aabb prior=Aabb.player(previous.position(),1.8).expand(settings.boxExpansion(),settings.boxExpansion(),settings.boxExpansion());
                    box=union(box,prior);
                } else reasons.add("INTERPOLATION_GAP");
            }
            boxes.add(box); previous=sample;
        }
        return new View(boxes,true,acknowledgedSequence,reasons);
    }
    private static Aabb union(Aabb a,Aabb b) { return new Aabb(Math.min(a.minX(),b.minX()),Math.min(a.minY(),b.minY()),Math.min(a.minZ(),b.minZ()),Math.max(a.maxX(),b.maxX()),Math.max(a.maxY(),b.maxY()),Math.max(a.maxZ(),b.maxZ())); }
    private static boolean finite(double x,double y,double z) { return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30_000_000&&Math.abs(y)<=30_000_000&&Math.abs(z)<=30_000_000; }
}
