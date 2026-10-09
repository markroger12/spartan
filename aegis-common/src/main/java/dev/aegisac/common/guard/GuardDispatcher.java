package dev.aegisac.common.guard;
import dev.aegisac.api.check.*;
import dev.aegisac.common.config.GuardSettings;
import java.util.*;
/** Phase 6 diagnostics never enter a trusted buffer, risk score or enforcement path. */
public final class GuardDispatcher {
    private dev.aegisac.common.packet.PacketMetrics metrics;
    public void metrics(dev.aegisac.common.packet.PacketMetrics value) { metrics=value; }
    private java.util.function.Consumer<dev.aegisac.common.output.Detection> sink=d->{};
    public void output(java.util.function.Consumer<dev.aegisac.common.output.Detection> sink) { this.sink=sink; }
    private static final class State {
        long evaluated,diagnostics,last;
        boolean seen;
        String status="WAITING";
        Set<String> reasons=Set.of("BASELINE_MISSING");
    }
    private final EnumMap<GuardId,State> states=new EnumMap<>(GuardId.class);
    private final Map<String,ArrayDeque<GuardEvidence>> evidence=new LinkedHashMap<>();
    public GuardDispatcher() { for(var id:GuardId.values()) states.put(id,new State()); GuardSettings.CATEGORIES.forEach(c->evidence.put(c,new ArrayDeque<>())); }
    public void reset(String reason,GuardSettings settings) {
        evidence.values().forEach(ArrayDeque::clear);
        states.forEach((id,s)-> { s.evaluated=s.diagnostics=0; s.seen=false;
            s.status=!id.implemented?"UNAVAILABLE":!settings.category(id).enabled()||!settings.rules().get(id).enabled()?"DISABLED":"SUPPRESSED";
            s.reasons=Set.of(!id.implemented?"MODEL_UNAVAILABLE":reason); });
    }
    public void evaluate(GuardId id,Double observed,long sequence,long time,long generation,GuardSettings settings,Set<String> reasons,Set<String> bypasses) {
        var s=states.get(id); var rule=settings.rules().get(id); var category=settings.category(id);
        if(!id.implemented||!category.enabled()||!rule.enabled()) return;
        var gates=new HashSet<>(reasons); gates.add("OBSERVATION_ONLY");
        if(bypasses.contains("*")||bypasses.contains(id.name())) gates.add("PERMISSION_BYPASS");
        s.reasons=Set.copyOf(gates);
        boolean exempt=gates.stream().anyMatch(Set.of("PERMISSION_BYPASS","WORLD_EXEMPT","GAMEMODE","FLIGHT_ALLOWED","TIMING_GRACE","SPECIAL_MOVEMENT")::contains)
                ||gates.contains("UNKNOWN_EDITION")&&rule.bedrockMode().equals("disabled");
        if(observed==null||!Double.isFinite(observed)) { s.status="SUPPRESSED"; return; }
        s.evaluated++; if(metrics!=null)metrics.evaluated();
        boolean mismatch=observed>rule.limit(); s.status=mismatch&&!exempt?"DIAGNOSTIC":"SUPPRESSED";
        if(mismatch&&!exempt&&(!s.seen||time-s.last>=rule.cooldownMillis()*1_000_000L)) {
            s.seen=true; s.last=time; s.diagnostics++;
            var ring=evidence.get(id.category); while(ring.size()>=category.evidenceCapacity()) ring.removeFirst();
            ring.addLast(new GuardEvidence(id.name(),id.category,sequence,time,generation,observed,rule.limit(),gates));
            sink.accept(new dev.aegisac.common.output.Detection(id.name(),id.category,sequence,time,generation,observed,rule.limit(),0,true,true,gates));
        }
    }
    public GuardSnapshot snapshot(long generation) {
        Map<String,CheckSnapshot> copy=new LinkedHashMap<>();
        states.forEach((id,s)->copy.put(id.name(),new CheckSnapshot(id.name(),s.status,0,s.evaluated,0,s.diagnostics,s.reasons)));
        return new GuardSnapshot(generation,copy,evidence.values().stream().flatMap(Collection::stream).sorted(Comparator.comparingLong(GuardEvidence::sequence)).toList());
    }
}
