package dev.aegisac.common.connection;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import org.junit.jupiter.api.Test;
import static dev.aegisac.common.packet.PacketDirection.*;
import static dev.aegisac.common.packet.PipelineFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionTrackerTest {
    private PacketFrame frame(long seq, long millis, PacketDirection direction, NormalizedPacket packet) {
        return new PacketFrame(seq, 0, millis * 1_000_000, direction, PROTOCOL, packet);
    }
    @Test void keepAliveAndTransactionEwmasStaySeparateAndJitterUsesSameStream() {
        var tracker = new ConnectionTracker(settings());
        tracker.observe(frame(1, 0, OUTBOUND, new Timing(TimingKind.PING, 1, 0, true)), 0);
        tracker.observe(frame(2, 100, INBOUND, new Timing(TimingKind.PING, 1, 0, true)), 100_000_000);
        tracker.observe(frame(3, 101, OUTBOUND, new Timing(TimingKind.KEEP_ALIVE, 1, 0, true)), 101_000_000);
        tracker.observe(frame(4, 301, INBOUND, new Timing(TimingKind.KEEP_ALIVE, 1, 0, true)), 301_000_000);
        tracker.observe(frame(5, 302, OUTBOUND, new Timing(TimingKind.PING, 2, 0, true)), 302_000_000);
        tracker.observe(frame(6, 442, INBOUND, new Timing(TimingKind.PING, 2, 0, true)), 442_000_000);
        var sample = tracker.snapshot(442_000_000, 0, 0).timing();
        assertEquals(120, sample.transactionRttMillis()); assertEquals(200, sample.keepAliveRttMillis());
        assertEquals(20, sample.jitterMillis()); assertEquals(3, sample.samples());
    }
    @Test void delayedWorkersDoNotInflateObservedRttButCreateUncertainty() {
        var tracker = new ConnectionTracker(settings());
        tracker.observe(frame(1, 0, OUTBOUND, new Timing(TimingKind.PING, 1, 0, true)), 900_000_000);
        tracker.observe(frame(2, 50, INBOUND, new Timing(TimingKind.PING, 1, 0, true)), 950_000_000);
        var snapshot = tracker.snapshot(950_000_000, 0, 0);
        assertEquals(50, snapshot.timing().transactionRttMillis());
        assertTrue(snapshot.uncertain()); assertEquals(900_000_000, snapshot.processingDelayNanos());
    }
    @Test void stallsLossAndRecoveryAreExplicitWithoutBanOrGlobalBypass() {
        var tracker = new ConnectionTracker(settings());
        tracker.observe(frame(1, 0, INBOUND, Other.INSTANCE), 0);
        assertFalse(tracker.snapshot(1, 0, 0).uncertain());
        assertTrue(tracker.snapshot(300_000_000, 0, 0).uncertain());
        tracker.observe(frame(2, 300, INBOUND, Other.INSTANCE), 300_000_000);
        for (int i=3;i<=24;i++) tracker.observe(frame(i, 300+(i-2)*50L, INBOUND, Other.INSTANCE), (300+(i-2)*50L)*1_000_000);
        assertFalse(tracker.snapshot(1_400_000_000, 0, 0).uncertain());
        assertTrue(tracker.snapshot(1_400_000_000, 1, 0).uncertain());
        assertTrue(tracker.snapshot(1_400_000_000, 0, 0).inboundPacketsPerSecond() > 0);
    }
    @Test void acceptedLegacyWindowsDoNotCreateUnanswerablePendingTransactions() {
        var tracker = new ConnectionTracker(settings());
        tracker.observe(frame(1, 0, OUTBOUND, new Timing(TimingKind.WINDOW, 1, 0, true)), 0);
        assertEquals(0, tracker.snapshot(0,0,0).timing().pending());
        tracker.observe(frame(2, 1, OUTBOUND, new Timing(TimingKind.WINDOW, 2, 0, false)), 1_000_000);
        tracker.observe(frame(3, 10, INBOUND, new Timing(TimingKind.WINDOW, 2, 0, true)), 10_000_000);
        assertEquals(9, tracker.snapshot(10_000_000,0,0).timing().transactionRttMillis());
    }
    @Test void negativeNanoTimeOriginIsNotAnArtificialLagSpike() {
        var tracker = new ConnectionTracker(settings());
        tracker.observe(frame(1, -1000, INBOUND, Other.INSTANCE), -1_000_000_000);
        assertFalse(tracker.snapshot(-999_999_999,0,0).uncertain());
    }
}
