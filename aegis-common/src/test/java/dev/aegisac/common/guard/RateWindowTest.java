package dev.aegisac.common.guard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RateWindowTest {
    @Test void requiresWarmupAndDropsExpiredBuckets() {
        var window=new RateWindow(); window.add(0); assertFalse(window.ready(999_999_999)); assertTrue(window.ready(1_000_000_000));
        assertEquals(0,window.count(1_000_000_000));
        for(int i=0;i<80;i++) window.add(1_001_000_000); assertEquals(80,window.count(1_999_000_000));
        assertEquals(0,window.count(2_000_000_000));
    }
    @Test void partialOldestBucketIsExcludedRatherThanInflatingRate() {
        var window=new RateWindow(); window.add(49_000_000); window.add(50_000_000);
        assertEquals(1,window.count(1_001_000_000));
    }
    @Test void negativeMonotonicTimesAndReversalAreHandled() {
        var window=new RateWindow(); window.add(-2_000_000_000); window.add(-1_000_000_000); assertTrue(window.ready(-1_000_000_000));
        window.add(-1_000_000_001); assertFalse(window.ready(-1_000_000_001)); assertEquals(1,window.count(-1_000_000_001));
        window.clear(); assertEquals(0,window.count(0));
    }
}
