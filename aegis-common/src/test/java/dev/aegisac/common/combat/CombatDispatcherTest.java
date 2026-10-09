package dev.aegisac.common.combat;
import dev.aegisac.common.check.Check;
import dev.aegisac.common.config.CombatSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
class CombatDispatcherTest {
    @Test void trustedFixtureRequiresBufferWhileUncertaintyAlwaysResetsIt() {
        var d=new CombatDispatcher(); var s=CombatSettings.defaults();
        for(int i=0;i<6;i++) d.evaluate(CombatId.ReachA,Check.Evaluation.above(5,3),i,i*50_000_000L,7,1,s,Set.of(),Set.of());
        assertTrue(d.states().get("ReachA").findings()>0);
        d.evaluate(CombatId.ReachA,Check.Evaluation.above(5,3),7,1_000_000_000,7,1,s,Set.of("UNCONFIRMED_DELIVERY"),Set.of());
        assertEquals(0,d.states().get("ReachA").buffer()); assertTrue(d.evidence().getLast().diagnostic());
    }
    @Test void bypassAndGraceSuppressDiagnosticsAndEvidenceRemainsBounded() {
        var d=new CombatDispatcher(); var s=CombatSettings.defaults();
        d.evaluate(CombatId.ReachA,Check.Evaluation.above(5,3),1,0,7,1,s,Set.of(),Set.of("*"));
        d.evaluate(CombatId.ReachA,Check.Evaluation.above(5,3),2,1,7,1,s,Set.of("TIMING_GRACE"),Set.of());
        assertTrue(d.evidence().isEmpty());
        for(int i=0;i<100;i++) d.evaluate(CombatId.ReachA,Check.Evaluation.above(5,3),i,i*1_000_000_000L,7,1,s,Set.of("UNKNOWN_EDITION"),Set.of());
        assertEquals(s.evidenceCapacity(),d.evidence().size()); assertEquals(0,d.states().get("ReachA").findings());
        assertThrows(UnsupportedOperationException.class,()->d.evidence().clear());
    }
    @Test void statisticsStayBoundedAndRejectNonfiniteInput() {
        var s=new RollingStatistics(4); for(int i=0;i<10;i++) s.add(i);
        assertEquals(4,s.summary(0).samples()); assertEquals(7.5,s.summary(0).mean());
        s.clear(); assertEquals(0,s.summary(0).samples());
        assertThrows(IllegalArgumentException.class,()->s.add(Double.NaN));
    }
}
