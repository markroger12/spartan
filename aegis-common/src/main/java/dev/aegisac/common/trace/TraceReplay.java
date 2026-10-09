package dev.aegisac.common.trace;

import dev.aegisac.common.config.ConfigSnapshot;
import dev.aegisac.common.player.PlayerData;
import java.io.IOException;
import java.util.*;

/** Offline diagnostic replay. No Bukkit, storage, webhooks, punishment or setback services exist here. */
public final class TraceReplay {
    private TraceReplay() { }
    public record Report(int records,long diagnostics,long findings,Set<String> limitations,Set<String> physicsReasons,Map<String,Long> diagnosticChecks,double maximumTransactionRttMillis,long maximumLossEpoch) {
        public Report { limitations=Set.copyOf(limitations);physicsReasons=Set.copyOf(physicsReasons);diagnosticChecks=Map.copyOf(diagnosticChecks); }
    }
    public static Report run(TraceFile.Recording recording,ConfigSnapshot configuration)throws IOException {
        if(recording.dropped()!=0)throw new IOException("Trace contains dropped records; capture again with lower traffic");
        String fingerprint=AnalysisFingerprint.of(configuration);
        var data=PlayerData.replaySession();var counts=new long[2];var diagnostics=new TreeMap<String,Long>();
        data.configureOutput(e->{ if(e.detection().diagnostic()) { counts[0]++;diagnostics.merge(e.detection().check(),1L,Long::sum); }else counts[1]++; });
        var limitations=new TreeSet<String>();limitations.add("ENTRY_OBSERVATIONS_MAY_RACE_OWNER_PUBLICATION");
        limitations.add("SYNTHETIC_REPLAY_NOT_CLIENT_CONFORMANCE");
        if(recording.reason().contains("LIMIT"))limitations.add("RECORDING_STOPPED_AT_RESOURCE_LIMIT");
        var reasons=new TreeSet<String>();long sequence=0,maximumLoss=0;double maximumRtt=-1;
        for(TraceEntry entry:recording.entries()) {
            if(!fingerprint.equals(entry.analysisFingerprint()))throw new IOException("Analysis settings mismatch: supply the original configuration directory");
            if(entry.manualExemptions())throw new IOException("Manual exemption/freeze history is not replayable");
            if(sequence==0&&entry.frame().sequence()!=1)limitations.add("COLD_START_MISSING_PRIOR_HISTORY");
            if(sequence!=0&&entry.frame().sequence()!=sequence+1)throw new IOException("Trace sequence gap");
            sequence=entry.frame().sequence();
            var c=configuration;
            if(c.generation()!=entry.generation())c=new ConfigSnapshot(entry.generation(),c.defaultProfile(),c.worldProfiles(),c.packetMetrics(),c.pipeline(),c.physics(),c.movement(),c.combat(),c.guard(),c.edition(),c.output(),c.exemptions(),c.messages(),c.documents());
            data.replay(entry,c);
            var snapshot=data.snapshot(entry.processedNanos());
            reasons.addAll(snapshot.physics().uncertainty());
            maximumRtt=Math.max(maximumRtt,snapshot.connection().timing().transactionRttMillis());
            maximumLoss=Math.max(maximumLoss,snapshot.connection().lossEpoch());
        }
        return new Report(recording.entries().size(),counts[0],counts[1],limitations,reasons,diagnostics,maximumRtt,maximumLoss);
    }
}
