package dev.aegisac.common.check;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static dev.aegisac.common.check.CheckFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
class SafePositionTest {
    @Test void onlyConsecutiveTrustedCollisionClearSamplesPromoteAndRevisionExpiryInvalidates() {
        var tracker=new SafePositionTracker();
        for(int i=1;i<=3;i++) tracker.observe(frame(i,i*50_000_000L,false,Set.of()),17,true,3,Set.of());
        var position=tracker.snapshot(150_000_000,0,2000); assertNotNull(position); assertTrue(position.verified());
        assertEquals(17,position.sessionId()); assertNull(tracker.snapshot(150_000_000,1,2000));
        assertNull(tracker.snapshot(3_000_000_000L,0,2000));
    }
    @Test void uncertainClearPositionsAreCandidatesAndBreakTrustedStreak() {
        var tracker=new SafePositionTracker();
        for(int i=1;i<=2;i++) tracker.observe(frame(i,i*50_000_000L,false,Set.of()),17,true,3,Set.of());
        tracker.observe(frame(3,150_000_000,false,Set.of("INPUT_INFERRED")),17,true,3,Set.of("INPUT_INFERRED"));
        var candidate=tracker.snapshot(150_000_000,0,2000); assertFalse(candidate.verified()); assertEquals(0,candidate.consecutiveSamples());
        tracker.observe(frame(4,200_000_000,false,Set.of()),17,true,3,Set.of());
        assertFalse(tracker.snapshot(200_000_000,0,2000).verified());
        tracker.observe(frame(5,250_000_000,true,Set.of()),17,false,3,Set.of());
        assertNull(tracker.snapshot(250_000_000,0,2000));
    }
}
