package dev.aegisac.common.combat;
import dev.aegisac.api.check.*;
import dev.aegisac.common.check.*;
import dev.aegisac.common.config.CombatSettings;
import java.util.*;
/** Independent combat buffers; speculative evidence cannot enter global risk or enforcement. */
public final class CombatDispatcher {
    private dev.aegisac.common.packet.PacketMetrics metrics;
    public void metrics(dev.aegisac.common.packet.PacketMetrics value) { metrics=value; }
    private java.util.function.Consumer<dev.aegisac.common.output.Detection> sink=d->{};
    public void output(java.util.function.Consumer<dev.aegisac.common.output.Detection> sink) { this.sink=sink; }
    private static final class State {
        final CheckBuffer buffer=new CheckBuffer();
        long evaluated,findings,diagnostics,lastDiagnostic;
        boolean diagnosticSeen;
        String status="WAITING";
        Set<String> reasons=Set.of("BASELINE_MISSING");
    }
    private final EnumMap<CombatId,State> states=new EnumMap<>(CombatId.class);
    private final ArrayDeque<CombatEvidence> evidence=new ArrayDeque<>();
    public CombatDispatcher() { for(var id:CombatId.values()) states.put(id,new State()); }
    public void clear(String reason) {
        evidence.clear();
        states.values().forEach(s->{ s.buffer.reset(); s.evaluated=s.findings=s.diagnostics=0; s.diagnosticSeen=false; s.status="SUPPRESSED"; s.reasons=Set.of(reason); });
    }
    public void evaluate(CombatId id,Check.Evaluation value,long sequence,long time,int target,long generation,
                         CombatSettings settings,Set<String> reasons,Set<String> bypasses) {
        var rule=settings.rules().get(id); State s=states.get(id);
        if(!settings.enabled()||!rule.enabled()) { s.buffer.reset(); s.status="DISABLED"; s.reasons=Set.of("CHECK_DISABLED"); return; }
        var gates=new HashSet<>(reasons);
        boolean bypass=bypasses.contains("*")||bypasses.contains(id.name());
        if(bypass) gates.add("PERMISSION_BYPASS");
        boolean exempt=gates.contains("TIMING_GRACE")||gates.contains("SPECIAL_MOVEMENT")||bypass||gates.contains("WORLD_EXEMPT")||gates.contains("GAMEMODE")||gates.contains("FLIGHT_ALLOWED")
                || gates.contains("UNKNOWN_EDITION")&&rule.bedrockMode().equals("disabled");
        s.reasons=Set.copyOf(gates);
        if(!value.applicable()) { s.buffer.reset(); s.status="SUPPRESSED"; if(gates.isEmpty()) s.reasons=Set.of("INSUFFICIENT_CONTEXT"); return; }
        s.evaluated++; if(metrics!=null)metrics.evaluated();
        boolean mismatch=value.excess()>rule.tolerance();
        if(!gates.isEmpty()) {
            s.buffer.reset(); s.status=mismatch&&!exempt?"DIAGNOSTIC":"SUPPRESSED";
            if(mismatch&&!exempt&&(!s.diagnosticSeen||time-s.lastDiagnostic>=rule.cooldownMillis()*1_000_000L)) {
                s.diagnosticSeen=true; s.lastDiagnostic=time; s.diagnostics++;
                add(new CombatEvidence(id.name(),sequence,time,generation,target,value.observed(),value.expected(),value.excess(),true,gates),settings.evidenceCapacity());
            }
        } else {
            boolean finding=s.buffer.accept(time,mismatch,rule);
            s.status=finding?"EXPERIMENTAL_FINDING":mismatch?"BUFFERING":"PASS";
            if(finding) { s.findings++; add(new CombatEvidence(id.name(),sequence,time,generation,target,value.observed(),value.expected(),value.excess(),false,Set.of()),settings.evidenceCapacity()); }
        }
    }
    private void add(CombatEvidence value,int capacity) { while(evidence.size()>=capacity) evidence.removeFirst(); evidence.addLast(value); sink.accept(new dev.aegisac.common.output.Detection(value.check(),"combat",value.sequence(),value.observedNanos(),value.generation(),value.observed(),value.expected(),states.get(CombatId.valueOf(value.check())).buffer.value(),value.diagnostic(),true,value.reasons())); }
    public Map<String,CheckSnapshot> states() {
        Map<String,CheckSnapshot> result=new LinkedHashMap<>();
        states.forEach((id,s)->result.put(id.name(),new CheckSnapshot(id.name(),s.status,s.buffer.value(),s.evaluated,s.findings,s.diagnostics,s.reasons)));
        return Map.copyOf(result);
    }
    public List<CombatEvidence> evidence() { return List.copyOf(evidence); }
}
