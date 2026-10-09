package dev.aegisac.common.check;
import dev.aegisac.api.check.*;
import dev.aegisac.common.config.MovementSettings;
import java.util.*;
/** Per-session bounded dispatcher. Uncertainty permits diagnostic records but never increments check buffers. */
public final class MovementDispatcher {
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
    private final EnumMap<CheckId,State> states=new EnumMap<>(CheckId.class);
    private final EnumMap<CheckId,MovementCheck> movement=new EnumMap<>(CheckId.class);
    private final ArrayDeque<MovementEvidence> evidence=new ArrayDeque<>();
    private final SafePositionTracker safe=new SafePositionTracker();
    private MovementSettings settings=MovementSettings.defaults();
    private long generation;
    public MovementDispatcher() {
        for(CheckId id:CheckId.values()) {
            states.put(id,new State());
            if(id.implemented && !id.timing()) movement.put(id,new MovementCheck(id));
        }
    }
    public void configure(long generation,MovementSettings settings) {
        if(this.generation==generation && this.settings.equals(settings)) return;
        this.generation=generation; this.settings=settings; reset("CONFIGURATION_CHANGED");
    }
    public void reset(String reason) {
        evidence.clear(); safe.reset();
        for(State state:states.values()) {
            state.buffer.reset(); state.evaluated=state.findings=state.diagnostics=0;
            state.status="SUPPRESSED"; state.reasons=Set.of(reason); state.diagnosticSeen=false;
        }
    }
    public void unavailable(long sequence,long time,Set<String> reasons) {
        safe.reset();
        for(CheckId id:movement.keySet()) accept(id,Check.Evaluation.absent(),sequence,time,reasons,false);
        for(CheckId id:CheckId.values()) if(!id.implemented) accept(id,Check.Evaluation.absent(),sequence,time,Set.of("MODEL_UNAVAILABLE"),true);
    }
    public void movement(MovementFrame f,long session,Set<String> gates,Set<String> bypasses) {
        boolean noMismatch=true;
        HashSet<String> reasons=new HashSet<>(f.reasons()); reasons.addAll(gates);
        for(var entry:movement.entrySet()) {
            CheckId id=entry.getKey();long started=metrics==null?0:System.nanoTime();
            var value=entry.getValue().evaluate(f);
            if(metrics!=null)metrics.movementEvaluation(System.nanoTime()-started);
            boolean bypass=bypasses.contains("*") || bypasses.contains(id.name());
            var specific=new HashSet<>(reasons); if(bypass) specific.add("PERMISSION_BYPASS");
            if(!settings.rules().get(id).enabled()) specific.add("CHECK_DISABLED");
            if(value.applicable() && value.excess()>settings.rules().get(id).tolerance()) noMismatch=false;
            accept(id,value,f.sequence(),f.observedNanos(),specific,!bypass);
        }
        for(CheckId id:CheckId.values()) if(!id.implemented) accept(id,Check.Evaluation.absent(),f.sequence(),f.observedNanos(),Set.of("MODEL_UNAVAILABLE"),true);
        if(!settings.enabled()) { safe.reset(); return; }
        if(!bypasses.isEmpty()) reasons.add("PERMISSION_BYPASS");
        if(movement.keySet().stream().anyMatch(id->!settings.rules().get(id).enabled())) reasons.add("CHECK_DISABLED");
        safe.observe(f,session,noMismatch,settings.safeSamples(),reasons);
    }
    public void timing(long sequence,long now,MovementTiming.Sample sample,Set<String> gates,Set<String> bypasses) {
        for(CheckId id:List.of(CheckId.TimerA,CheckId.BlinkA)) {
            var reasons=new HashSet<>(gates);
            boolean bypass=bypasses.contains("*") || bypasses.contains(id.name());
            if(bypass) reasons.add("PERMISSION_BYPASS");
            accept(id,id==CheckId.TimerA?sample.timer():sample.blink(),sequence,now,reasons,!bypass);
        }
    }
    private void accept(CheckId id,Check.Evaluation value,long sequence,long time,Set<String> reasons,boolean diagnosticsAllowed) {
        if(reasons.contains("GAMEMODE") || reasons.contains("FLIGHT_ALLOWED") || reasons.contains("WORLD_EXEMPT")) diagnosticsAllowed=false;
        State state=states.get(id); var rule=settings.rules().get(id);
        if(reasons.contains("UNKNOWN_EDITION") && rule.bedrockMode().equals("disabled")) diagnosticsAllowed=false;
        if(!id.implemented) { state.status="UNAVAILABLE"; state.reasons=Set.of("MODEL_UNAVAILABLE"); return; }
        if(!settings.enabled() || !rule.enabled()) {
            state.buffer.reset(); state.status="DISABLED"; state.reasons=Set.of("CHECK_DISABLED"); return;
        }
        if(id.timing() && !value.applicable() && reasons.isEmpty()) return;
        if(!value.applicable()) { state.buffer.reset(); state.status="SUPPRESSED"; state.reasons=reasons.isEmpty()?Set.of("NOT_APPLICABLE"):Set.copyOf(reasons); return; }
        state.evaluated++; if(metrics!=null)metrics.evaluated();
        boolean mismatch=value.excess()>rule.tolerance();
        state.reasons=Set.copyOf(reasons);
        if(!reasons.isEmpty()) {
            state.buffer.reset(); state.status=mismatch && diagnosticsAllowed?"DIAGNOSTIC":"SUPPRESSED";
            if(mismatch && diagnosticsAllowed && !(reasons.contains("UNKNOWN_EDITION") && rule.bedrockMode().equals("disabled"))
                    && (!state.diagnosticSeen || time-state.lastDiagnostic>=rule.cooldownMillis()*1_000_000L)) {
                state.diagnostics++; state.lastDiagnostic=time; state.diagnosticSeen=true;
                add(new MovementEvidence(id.name(),sequence,time,generation,value.observed(),value.expected(),value.excess(),true,reasons));
            }
            return;
        }
        boolean finding=state.buffer.accept(time,mismatch,rule);
        state.status=finding?"EXPERIMENTAL_FINDING":mismatch?"BUFFERING":"PASS";
        if(finding) {
            state.findings++;
            add(new MovementEvidence(id.name(),sequence,time,generation,value.observed(),value.expected(),value.excess(),false,Set.of()));
        }
    }
    private void add(MovementEvidence value) {
        while(evidence.size()>=settings.evidenceCapacity()) evidence.removeFirst();
        evidence.addLast(value);
        sink.accept(new dev.aegisac.common.output.Detection(value.check(),"movement",value.sequence(),value.observedNanos(),value.configurationGeneration(),value.observed(),value.expected(),states.get(CheckId.valueOf(value.check())).buffer.value(),value.diagnostic(),true,value.reasons()));
    }
    public MovementChecksSnapshot snapshot(long now,long worldRevision) {
        Map<String,CheckSnapshot> snapshots=new LinkedHashMap<>();
        states.forEach((id,s)->snapshots.put(id.name(),new CheckSnapshot(id.name(),s.status,s.buffer.value(),s.evaluated,s.findings,s.diagnostics,s.reasons)));
        return new MovementChecksSnapshot(generation,snapshots,List.copyOf(evidence),safe.snapshot(now,worldRevision,settings.safeAgeMillis()));
    }
}
