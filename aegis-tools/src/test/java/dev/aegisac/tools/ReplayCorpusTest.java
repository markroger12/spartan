package dev.aegisac.tools;

import dev.aegisac.common.config.ConfigurationLoader;
import dev.aegisac.common.trace.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ReplayCorpusTest {
    @TempDir Path directory;
    static Stream<String> scenarios() { return Stream.concat(FixtureCorpus.LEGITIMATE.stream(),FixtureCorpus.SUSPICIOUS.stream()); }
    @ParameterizedTest @MethodSource("scenarios")
    void corpusSurvivesDiskRoundTripAndProducesRepeatableGatedDiagnostics(String scenario)throws Exception {
        var config=new ConfigurationLoader(directory.resolve("config")).load(1);Path file=directory.resolve("fixture");
        try(var writer=new TraceFile.Writer(file)) { for(var entry:FixtureCorpus.entries(scenario,config))assertTrue(writer.append(entry));writer.finish(0,"FIXTURE"); }
        var recording=TraceFile.read(file);var report=TraceReplay.run(recording,config);
        assertEquals(report,TraceReplay.run(recording,config));assertEquals(80,report.records());
        assertEquals(0,report.findings(),"Unacknowledged captured world must never yield trusted enforcement");
        if(scenario.equals("speed-burst"))assertTrue(report.diagnostics()>0,"Suspicious corpus must exercise detection");
        String reason=switch(scenario) {
            case "water"->"FLUID_FLOW";case "ladder"->"CLIMBABLE";case "honey"->"HONEY";case "piston"->"PISTON";
            case "elytra"->"ELYTRA";case "slime"->"SLIME";case "velocity"->"VELOCITY_TIMING";case "teleport"->"TELEPORT";
            case "bedrock-translation"->"BEDROCK_PHYSICS_UNAVAILABLE";case "invalid-coordinate"->"INVALID_MOVEMENT";default->null;
        };
        if(scenario.equals("high-ping"))assertEquals(600,report.maximumTransactionRttMillis(),.001);
        if(scenario.equals("lag-spike"))assertEquals(1,report.maximumLossEpoch());
        if(reason!=null)assertTrue(report.physicsReasons().contains(reason),report.toString());
    }
    @Test void rejectsDroppedGapAndMismatchedSettings()throws Exception {
        var config=new ConfigurationLoader(directory.resolve("config")).load(1);var entries=FixtureCorpus.entries("walking",config);
        assertThrows(IOException.class,()->TraceReplay.run(new TraceFile.Recording(entries,1,"DROP"),config));
        var gap=new ArrayList<>(entries);gap.remove(4);assertThrows(IOException.class,()->TraceReplay.run(new TraceFile.Recording(gap,0,"GAP"),config));
        Path movement=directory.resolve("config/checks/movement.yml");String text=Files.readString(movement);
        Files.writeString(movement,text.replaceFirst("enabled: true","enabled: false"));var changed=new ConfigurationLoader(directory.resolve("config")).load(2);
        assertNotEquals(AnalysisFingerprint.of(config),AnalysisFingerprint.of(changed));
        assertThrows(IOException.class,()->TraceReplay.run(new TraceFile.Recording(entries,0,"TEST"),changed));
    }
    @Test void coldStartIsExplicitAndLargeSessionLoadCleansUp()throws Exception {
        var config=new ConfigurationLoader(directory.resolve("config")).load(1);var entries=FixtureCorpus.entries("walking",config);
        assertTrue(TraceReplay.run(new TraceFile.Recording(entries.subList(10,80),0,"PARTIAL"),config).limitations().contains("COLD_START_MISSING_PRIOR_HISTORY"));
        var load=SessionLoad.run(256,64);assertEquals(16384,load.processed());assertEquals(0,load.dropped());assertEquals(0,load.remainingQueues());
    }
}
