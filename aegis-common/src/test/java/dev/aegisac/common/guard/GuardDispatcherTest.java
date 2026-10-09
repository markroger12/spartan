package dev.aegisac.common.guard;
import dev.aegisac.common.config.GuardSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class GuardDispatcherTest {
    @Test void diagnosticsAreBoundedPerCategoryAndNeverCreateTrustedFindings() {
        var d=new GuardDispatcher(); var settings=GuardSettings.defaults(); d.reset("TEST",settings);
        for(int i=0;i<100;i++) for(var id:List.of(GuardId.InvalidSlotA,GuardId.InvalidPitchA))
            d.evaluate(id,1.0,i,i*1_000_000_000L,1,settings,Set.of(),Set.of());
        var snapshot=d.snapshot(1); assertEquals(64,snapshot.evidence().size());
        assertTrue(snapshot.evidence().stream().allMatch(e->e.reasons().contains("OBSERVATION_ONLY")));
        snapshot.checks().values().forEach(c->{assertEquals(0,c.buffer()); assertEquals(0,c.findings());});
        assertThrows(UnsupportedOperationException.class,()->snapshot.evidence().clear());
        d.reset("CLOSED",settings); assertTrue(d.snapshot(1).evidence().isEmpty());
    }
    @Test void cooldownAndExemptionsSuppressRepeatedEvidence() {
        var d=new GuardDispatcher(); var s=GuardSettings.defaults(); d.reset("TEST",s);
        d.evaluate(GuardId.InvalidSlotA,1.0,1,0,1,s,Set.of(),Set.of("*"));
        d.evaluate(GuardId.InvalidSlotA,1.0,2,1,1,s,Set.of("WORLD_EXEMPT"),Set.of());
        assertTrue(d.snapshot(1).evidence().isEmpty());
        d.evaluate(GuardId.InvalidSlotA,1.0,3,2,1,s,Set.of(),Set.of());
        d.evaluate(GuardId.InvalidSlotA,1.0,4,3,1,s,Set.of(),Set.of());
        assertEquals(1,d.snapshot(1).evidence().size());
        d.evaluate(GuardId.InvalidSlotA,Double.NaN,5,4,1,s,Set.of(),Set.of());
        assertEquals(1,d.snapshot(1).evidence().size());
    }
    @Test void disabledRulesAndUnknownEditionPolicyAreIndependent() {
        var defaults=GuardSettings.defaults(); var rules=new EnumMap<GuardId,GuardSettings.Rule>(defaults.rules());
        rules.put(GuardId.InvalidSlotA,new GuardSettings.Rule(false,0,1,"diagnostic"));
        rules.put(GuardId.InvalidPitchA,new GuardSettings.Rule(true,0,1,"disabled"));
        var settings=new GuardSettings(defaults.categories(),rules); var d=new GuardDispatcher(); d.reset("TEST",settings);
        d.evaluate(GuardId.InvalidSlotA,1.0,1,0,1,settings,Set.of(),Set.of());
        d.evaluate(GuardId.InvalidPitchA,1.0,2,0,1,settings,Set.of("UNKNOWN_EDITION"),Set.of());
        assertTrue(d.snapshot(1).evidence().isEmpty()); assertEquals("DISABLED",d.snapshot(1).checks().get("InvalidSlotA").status());
        assertEquals("UNAVAILABLE",d.snapshot(1).checks().get("FastBreakA").status());
    }
}
