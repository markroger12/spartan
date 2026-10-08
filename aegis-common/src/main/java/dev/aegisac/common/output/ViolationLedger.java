package dev.aegisac.common.output;
import dev.aegisac.api.output.ViolationSnapshot;
import dev.aegisac.common.config.OutputSettings;
import java.util.*;
/** Bounded rolling evidence ledger with linear per-evidence decay; diagnostics never enter it. */
public final class ViolationLedger {
    private record Entry(Detection detection,double weight,double confidence) { }
    private final ArrayDeque<Entry> entries=new ArrayDeque<>();
    private final Map<String,Long> lastSequence=new HashMap<>();
    private long lastTime; private boolean seen;
    public synchronized boolean accept(Detection value,OutputSettings.Punishments settings) {
        if(!value.scoreEligible()||!settings.rules().containsKey(value.check())) return false;
        if(seen&&value.time()<lastTime) { clear(); return false; }
        if(value.sequence()<=lastSequence.getOrDefault(value.check(),-1L)) return false;
        lastSequence.put(value.check(),value.sequence()); lastTime=value.time(); seen=true;
        prune(value.time(),settings); while(entries.size()>=256) entries.removeFirst();
        var rule=settings.rules().get(value.check()); entries.addLast(new Entry(value,rule.weight(),rule.confidence())); return true;
    }
    public synchronized void clear() { entries.clear(); lastSequence.clear(); seen=false; }
    private void prune(long now,OutputSettings.Punishments s) {
        if(seen&&now<lastTime) { clear(); return; }
        while(!entries.isEmpty()&&now-entries.peekFirst().detection.time()>=Math.min(s.windowMillis(),s.decayMillis())*1_000_000L) entries.removeFirst();
    }
    public synchronized ViolationSnapshot snapshot(long now,OutputSettings.Punishments settings) {
        prune(now,settings); Map<String,Double> checks=new HashMap<>(),categories=new HashMap<>(); double total=0,weightedConfidence=0;
        for(var entry:entries) {
            double fraction=Math.max(0,1-(now-entry.detection.time())/(settings.decayMillis()*1_000_000.0));
            double risk=entry.weight*fraction; total+=risk; weightedConfidence+=risk*entry.confidence;
            checks.merge(entry.detection.check(),fraction,Double::sum); categories.merge(entry.detection.category(),risk,Double::sum);
        }
        double confidence=total>0?Math.min(100,weightedConfidence/total*(1+Math.min(3,Math.max(0,checks.size()-1))*.05)):0;
        return new ViolationSnapshot(total,confidence,entries.size(),checks,categories);
    }
    public static boolean qualifies(Detection d,ViolationSnapshot score,OutputSettings.Punishments policy) {
        var r=policy.rules().get(d.check());
        return policy.enabled()&&r!=null&&r.enabled()&&d.scoreEligible()&&(!d.experimental()||policy.allowExperimental())
                &&score.checks().getOrDefault(d.check(),0.0)>=r.minimumVl()
                &&score.categories().getOrDefault(d.category(),0.0)>=r.minimumCategoryRisk()
                &&score.totalRisk()>=r.minimumTotalRisk()&&score.confidence()>=r.minimumConfidence()&&score.findings()>=r.minimumFindings();
    }
}
