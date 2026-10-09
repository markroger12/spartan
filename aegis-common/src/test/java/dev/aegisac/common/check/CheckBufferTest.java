package dev.aegisac.common.check;
import dev.aegisac.common.config.MovementSettings.Rule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CheckBufferTest {
    private final Rule rule=new Rule(true,.03,1,.25,3,3,1000,"diagnostic");
    @Test void isolatedNoiseDoesNotMeetConsecutiveEvidenceAndCooldownIsEnforced() {
        var buffer=new CheckBuffer();
        assertFalse(buffer.accept(0,true,rule)); assertFalse(buffer.accept(50_000_000,false,rule));
        assertFalse(buffer.accept(100_000_000,true,rule)); assertFalse(buffer.accept(150_000_000,true,rule));
        assertTrue(buffer.accept(200_000_000,true,rule)); assertFalse(buffer.accept(250_000_000,true,rule));
        assertTrue(buffer.accept(1_200_000_000,true,rule));
        assertTrue(buffer.value()<=6);
    }
    @Test void decayUsesElapsedTimeAndResetCannotRetainDebt() {
        var buffer=new CheckBuffer(); buffer.accept(0,true,rule); buffer.accept(2_000_000_000,false,rule);
        assertEquals(.5,buffer.value(),1e-12); buffer.reset(); assertEquals(0,buffer.value());
        assertFalse(buffer.accept(3_000_000_000L,true,rule));
    }
    @Test void monotonicWrapWorksAndBackwardsObservationResets() {
        var buffer=new CheckBuffer(); buffer.accept(Long.MAX_VALUE-100,true,rule);
        buffer.accept(Long.MIN_VALUE+100,true,rule); assertTrue(buffer.value()>1.9);
        buffer.accept(Long.MIN_VALUE,true,rule); assertEquals(1,buffer.value());
    }
}
