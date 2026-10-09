package dev.aegisac.common.connection;
import org.junit.jupiter.api.Test;
import static dev.aegisac.common.packet.NormalizedPacket.TimingKind.*;
import static dev.aegisac.common.connection.TransactionTracker.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionTrackerTest {
    @Test void exactMatchingSeparatesStreamsAndWindows() {
        var tracker = new TransactionTracker(8, 1000);
        tracker.sent(PING, 7, 0, 0); tracker.sent(KEEP_ALIVE, 7, 0, 1); tracker.sent(WINDOW, 7, 1, 2);
        assertEquals(UNKNOWN, tracker.acknowledge(WINDOW, 7, 2, 30).outcome());
        assertEquals(29, tracker.acknowledge(KEEP_ALIVE, 7, 0, 30).rttNanos());
        assertEquals(40, tracker.acknowledge(PING, 7, 0, 40).rttNanos());
        assertEquals(48, tracker.acknowledge(WINDOW, 7, 1, 50).rttNanos());
        assertEquals(0, tracker.stats().pending()); assertEquals(3, tracker.stats().matched());
    }
    @Test void duplicateAndForgedResponsesNeverCreateSamples() {
        var tracker = new TransactionTracker(8, 1000);
        assertEquals(UNKNOWN, tracker.acknowledge(PING, 1, 0, 20).outcome());
        tracker.sent(PING, 1, 0, 21);
        assertEquals(MATCHED, tracker.acknowledge(PING, 1, 0, 25).outcome());
        assertEquals(DUPLICATE, tracker.acknowledge(PING, 1, 0, 26).outcome());
        tracker.sent(PING, 1, 0, 27);
        assertEquals(AMBIGUOUS, tracker.acknowledge(PING, 1, 0, 28).outcome());
        assertEquals(1, tracker.stats().matched());
    }
    @Test void duplicateOutboundTokenIsAmbiguousAndDoesNotResetTimer() {
        var tracker = new TransactionTracker(8, 100);
        tracker.sent(PING, 1, 0, 0); tracker.sent(PING, 1, 0, 50);
        assertEquals(AMBIGUOUS, tracker.acknowledge(PING, 1, 0, 75).outcome());
        assertEquals(0, tracker.stats().matched());
    }
    @Test void outOfOrderReplyRetiresOnlyItsOwnTokenWithoutSampling() {
        var tracker = new TransactionTracker(8, 1000);
        tracker.sent(PING, 1, 0, 0); tracker.sent(PING, 2, 0, 5);
        assertEquals(OUT_OF_ORDER, tracker.acknowledge(PING, 2, 0, 20).outcome());
        assertEquals(1, tracker.stats().pending());
        assertEquals(MATCHED, tracker.acknowledge(PING, 1, 0, 25).outcome());
        assertEquals(1, tracker.stats().outOfOrder());
    }
    @Test void expirationAndCapacityEvictionAreBoundedAndVisible() {
        var tracker = new TransactionTracker(2, 100);
        tracker.sent(PING, 1, 0, 0); tracker.sent(PING, 2, 0, 5); tracker.sent(PING, 3, 0, 10);
        assertEquals(2, tracker.stats().pending()); assertEquals(1, tracker.stats().evicted());
        assertFalse(tracker.acknowledge(PING, 1, 0, 11).sampled());
        tracker.expire(110);
        assertEquals(0, tracker.stats().pending()); assertEquals(2, tracker.stats().expired());
        assertFalse(tracker.acknowledge(PING, 3, 0, 111).sampled());
    }
    @Test void signedIdentifiersAndNanoTimeWrapAreSupported() {
        var tracker = new TransactionTracker(8, 1000);
        tracker.sent(WINDOW, Short.MIN_VALUE, 255, Long.MAX_VALUE - 10);
        assertEquals(21, tracker.acknowledge(WINDOW, Short.MIN_VALUE, 255, Long.MIN_VALUE + 10).rttNanos());
        tracker.sent(KEEP_ALIVE, Long.MIN_VALUE, 0, 20);
        assertEquals(5, tracker.acknowledge(KEEP_ALIVE, Long.MIN_VALUE, 0, 25).rttNanos());
        tracker.sent(PING, -1, 0, 30);
        assertEquals(INVALID_TIME, tracker.acknowledge(PING, -1, 0, 29).outcome());
    }
    @Test void lossInvalidatesEveryPendingToken() {
        var tracker = new TransactionTracker(8, 1000);
        tracker.sent(PING, 3, 0, 0); tracker.sent(TELEPORT, 4, 0, 1);
        tracker.invalidate(2);
        assertEquals(0, tracker.stats().pending());
        assertFalse(tracker.acknowledge(PING, 3, 0, 3).sampled());
    }
}
