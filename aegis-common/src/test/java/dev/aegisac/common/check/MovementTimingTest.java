package dev.aegisac.common.check;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MovementTimingTest {
    private MovementTiming.Sample observe(MovementTiming timing,long millis) { return timing.observe(millis*1_000_000,2000,50,250,1000,200,8); }
    @Test void normalTwentyTpsAndConstantLatencyDoNotAccumulateSurplus() {
        var timing=new MovementTiming(); int windows=0;
        for(int i=0;i<300;i++) {
            var sample=observe(timing,900+i*50L);
            if(sample.timer().applicable()) { windows++; assertEquals(0,sample.timer().excess()); }
            assertFalse(sample.blink().applicable());
        }
        assertTrue(windows>=7);
    }
    @Test void sustainedTwentyFiveTpsProducesPositiveTimeDebt() {
        var timing=new MovementTiming(); double maximum=0;
        for(int i=0;i<151;i++) maximum=Math.max(maximum,observe(timing,i*40L).timer().excess());
        assertEquals(1500,maximum,1e-12);
    }
    @Test void shortBunchingConsumesCreditAndSlowPacketsNeverProduceNegativeEvidence() {
        var timing=new MovementTiming(); observe(timing,0);
        long now=0;
        for(int group=0;group<20;group++) {
            now+=250;
            for(int i=0;i<5;i++) assertEquals(0,observe(timing,now).timer().excess());
        }
        assertEquals(0,observe(timing,now+2000).timer().excess());
    }
    @Test void silenceAloneIsNotBlinkButFollowedBurstIsObservable() {
        var timing=new MovementTiming(); observe(timing,0); assertFalse(observe(timing,2000).blink().applicable());
        MovementTiming.Sample last=null;
        for(int i=1;i<8;i++) last=observe(timing,2000+i*10);
        assertNotNull(last); assertTrue(last.blink().applicable()); assertEquals(1,last.blink().excess());
        assertFalse(observe(timing,2090).blink().applicable());
    }
    @Test void clearingDebtOnLagPreservesBlinkPatternButCannotLeakOldTimerSurplus() {
        var timing=new MovementTiming(); for(int i=0;i<100;i++) observe(timing,i*40L);
        timing.clearDebt(); assertEquals(0,observe(timing,6000).timer().excess());
        for(int i=1;i<8;i++) { timing.clearDebt(); var sample=observe(timing,6000+i*10L); if(i==7) assertTrue(sample.blink().applicable()); }
        timing.reset(); assertFalse(observe(timing,7000).timer().applicable());
    }
}
