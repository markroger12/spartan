package dev.aegisac.common.check;
import dev.aegisac.common.config.MovementSettings;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.aegisac.common.check.CheckFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
class MovementDispatcherTest {
    @Test void repeatedTrustedMismatchBuildsPerCheckBuffersAndBoundedEvidence() {
        var dispatcher=new MovementDispatcher(); dispatcher.configure(1,noGrace());
        for(int i=0;i<20;i++) dispatcher.movement(frame(i+1,i*1_000_000_000L,true,Set.of()),42,Set.of(),Set.of());
        var snapshot=dispatcher.snapshot(19_000_000_000L,0);
        assertTrue(snapshot.checks().get("SpeedA").findings()>0); assertTrue(snapshot.evidence().size()<=4);
        assertNull(snapshot.safePosition()); assertEquals("UNAVAILABLE",snapshot.checks().get("ElytraA").status());
        assertThrows(UnsupportedOperationException.class,()->snapshot.evidence().clear());
    }
    @Test void uncertainMismatchesAreDiagnosticAndCannotBuildBuffersOrTrustedPositions() {
        var dispatcher=new MovementDispatcher(); dispatcher.configure(1,noGrace());
        for(int i=0;i<20;i++) dispatcher.movement(frame(i+1,i*50_000_000L,true,Set.of("UNACKNOWLEDGED_WORLD")),42,Set.of(),Set.of());
        var snapshot=dispatcher.snapshot(950_000_000,0); var speed=snapshot.checks().get("SpeedA");
        assertEquals(0,speed.buffer()); assertEquals(0,speed.findings()); assertTrue(speed.diagnostics()>0);
        assertTrue(snapshot.evidence().stream().allMatch(e->e.diagnostic())); assertNull(snapshot.safePosition());
    }
    @Test void explicitExemptionsAndPerCheckBypassSuppressEvidenceAndEraseBuffers() {
        var dispatcher=new MovementDispatcher(); dispatcher.configure(1,noGrace());
        dispatcher.movement(frame(1,0,true,Set.of()),42,Set.of(),Set.of());
        dispatcher.movement(frame(2,50_000_000,true,Set.of()),42,Set.of(),Set.of("SpeedA"));
        assertEquals(0,dispatcher.snapshot(50_000_000,0).checks().get("SpeedA").buffer());
        dispatcher.reset("test");
        for(int i=0;i<20;i++) dispatcher.movement(frame(i+1,i*50_000_000L,true,Set.of()),42,Set.of("WORLD_EXEMPT"),Set.of());
        assertTrue(dispatcher.snapshot(950_000_000,0).evidence().isEmpty());
    }
    @Test void configurationGenerationResetRemovesPriorEvidenceAndDisabledChecks() {
        var dispatcher=new MovementDispatcher(); dispatcher.configure(1,noGrace());
        for(int i=0;i<20;i++) dispatcher.movement(frame(i+1,i*50_000_000L,true,Set.of()),42,Set.of(),Set.of());
        var d=noGrace(); var disabled=new MovementSettings(false,4,0,0,0,0,250,100,3,2000,d.timing(),d.rules());
        dispatcher.configure(2,disabled); dispatcher.movement(frame(21,1_000_000_000,true,Set.of()),42,Set.of(),Set.of());
        var snapshot=dispatcher.snapshot(1_000_000_000,0); assertEquals(2,snapshot.generation());
        assertTrue(snapshot.evidence().isEmpty()); assertEquals("DISABLED",snapshot.checks().get("SpeedA").status());
    }
    @Test void disablingOneCheckDoesNotSuppressOtherEnabledChecks() {
        var d=noGrace(); var rules=new EnumMap<CheckId,MovementSettings.Rule>(CheckId.class); rules.putAll(d.rules());
        var r=rules.get(CheckId.SpeedA);
        rules.put(CheckId.SpeedA,new MovementSettings.Rule(false,r.tolerance(),r.increment(),r.decay(),r.threshold(),r.minimumSamples(),r.cooldownMillis(),r.bedrockMode()));
        var settings=new MovementSettings(true,4,0,0,0,0,250,100,3,2000,d.timing(),rules);
        var dispatcher=new MovementDispatcher(); dispatcher.configure(1,settings);
        for(int i=0;i<20;i++) dispatcher.movement(frame(i+1,i*50_000_000L,true,Set.of()),42,Set.of(),Set.of());
        var snapshot=dispatcher.snapshot(950_000_000,0);
        assertEquals("DISABLED",snapshot.checks().get("SpeedA").status());
        assertTrue(snapshot.checks().get("AccelerationA").findings()>0);
    }
    @Test void timerBuffersSurviveMovementPacketsBetweenCompletedWindows() {
        var dispatcher=new MovementDispatcher(); dispatcher.configure(1,noGrace());
        var excess=Check.Evaluation.above(500,0); var absent=Check.Evaluation.absent();
        for(int window=1;window<=8;window++) {
            dispatcher.timing(window*2,window*2_000_000_000L,new MovementTiming.Sample(excess,absent),Set.of(),Set.of());
            dispatcher.timing(window*2+1,window*2_000_000_000L+50_000_000,new MovementTiming.Sample(absent,absent),Set.of(),Set.of());
        }
        assertTrue(dispatcher.snapshot(16_050_000_000L,0).checks().get("TimerA").findings()>0);
    }
}
