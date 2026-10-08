package dev.aegisac.common.connection;
import dev.aegisac.api.player.ConnectionSnapshot;
import dev.aegisac.api.player.TimingSnapshot;
import dev.aegisac.common.config.PipelineSettings;
import dev.aegisac.common.packet.NormalizedPacket.Timing;
import dev.aegisac.common.packet.NormalizedPacket.TimingKind;
import dev.aegisac.common.packet.PacketDirection;
import dev.aegisac.common.packet.PacketFrame;
import java.util.concurrent.TimeUnit;

/** Worker-owned network observations. Server TPS/MSPT are not inferred from client packet rates. */
public final class ConnectionTracker {
    private final TransactionTracker transactions;
    private final long stall, grace;
    private final double alpha;
    private boolean inboundSeen, uncertaintySeen;
    private long lastInbound, lastGap, lastSequence, count, uncertainUntil, delay, rateStart, ratePackets;
    private double rate, transactionRtt = -1, keepAliveRtt = -1, jitter;
    private double previousTransaction = -1, previousKeepAlive = -1;
    private long samples;
    public ConnectionTracker(PipelineSettings settings) {
        transactions = new TransactionTracker(settings.pendingTransactions(), TimeUnit.MILLISECONDS.toNanos(settings.transactionTimeoutMillis()));
        stall = TimeUnit.MILLISECONDS.toNanos(settings.stallMillis());
        grace = TimeUnit.MILLISECONDS.toNanos(settings.uncertaintyMillis());
        alpha = settings.smoothing();
    }
    public TransactionTracker.Acknowledgement observe(PacketFrame frame, long processingNow) {
        long now = frame.observedNanos();
        count++; lastSequence = frame.sequence(); delay = Math.max(0, processingNow - now);
        if (delay >= stall) uncertain(now + delay);
        if (frame.direction() == PacketDirection.INBOUND) {
            if (inboundSeen) {
                long gap = now - lastInbound;
                if (gap < 0 || gap >= stall) uncertain(now);
                lastGap = Math.max(0, gap);
                long elapsed = now - rateStart;
                ratePackets++;
                if (elapsed >= TimeUnit.SECONDS.toNanos(1)) {
                    rate = ratePackets * 1_000_000_000.0 / elapsed;
                    rateStart = now; ratePackets = 0;
                }
            } else { inboundSeen = true; rateStart = now; }
            lastInbound = now;
        }
        // Called only for timing/teleport packets, keeping ordinary movement work constant.
        if (frame.packet() instanceof Timing timing) return timing(timing, frame.direction(), now);
        return null;
    }
    public TransactionTracker.Acknowledgement timing(Timing timing, PacketDirection direction, long now) {
        if (direction == PacketDirection.OUTBOUND) {
            // Accepted legacy window confirmations do not require client acknowledgements.
            if (timing.type() != TimingKind.WINDOW || !timing.accepted())
                transactions.sent(timing.type(), timing.id(), timing.window(), now);
            return null;
        }
        if (timing.type() == TimingKind.WINDOW && !timing.accepted()) { uncertain(now); return null; }
        var result = transactions.acknowledge(timing.type(), timing.id(), timing.window(), now);
        if (!result.sampled()) { uncertain(now); return result; }
        if (timing.type() == TimingKind.TELEPORT) return result;
        double rtt = result.rttNanos() / 1_000_000.0;
        boolean keepAlive = timing.type() == TimingKind.KEEP_ALIVE;
        double prior = keepAlive ? previousKeepAlive : previousTransaction;
        if (prior >= 0) jitter += alpha * (Math.abs(rtt - prior) - jitter);
        if (keepAlive) {
            keepAliveRtt = keepAliveRtt < 0 ? rtt : keepAliveRtt + alpha * (rtt - keepAliveRtt);
            previousKeepAlive = rtt;
        } else {
            transactionRtt = transactionRtt < 0 ? rtt : transactionRtt + alpha * (rtt - transactionRtt);
            previousTransaction = rtt;
        }
        samples++;
        return result;
    }
    public void reset(long now) {
        transactions.invalidate(now); inboundSeen = false; ratePackets = 0; rate = 0;
        transactionRtt = keepAliveRtt = previousTransaction = previousKeepAlive = -1;
        jitter = 0; uncertain(now);
    }
    public void uncertain(long now) {
        long deadline = now + grace;
        if (!uncertaintySeen || deadline - uncertainUntil > 0) uncertainUntil = deadline;
        uncertaintySeen = true;
    }
    public ConnectionSnapshot snapshot(long now, long lossEpoch, long processedEpoch) {
        // Queries must not expire a token before an already-arrived, queued reply is processed.
        // Expiry follows observed timing events, not worker/UI wall-clock delay.
        var stats = transactions.stats();
        boolean stalled = inboundSeen && now - lastInbound >= stall;
        var timing = new TimingSnapshot(transactionRtt, keepAliveRtt, jitter, samples, stats.pending(),
                stats.matched(), stats.unknown(), stats.duplicates(), stats.outOfOrder(), stats.expired(), stats.evicted());
        return new ConnectionSnapshot(timing, count, lastSequence, lastGap, rate, lossEpoch, processedEpoch,
                lossEpoch != processedEpoch || (uncertaintySeen && now - uncertainUntil < 0) || stalled || !inboundSeen,
                uncertainUntil, delay);
    }
}
