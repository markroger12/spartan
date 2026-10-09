package dev.aegisac.common.packet;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * One active consumer per session, bounded ingress and fair batches. Processing is outside
 * the queue lock. Executor rejection never runs analysis on the submitting network thread.
 * Clearing on overflow preserves a visible loss epoch rather than hiding holes in history.
 */
public final class SerialPacketQueue implements AutoCloseable {
    private final ArrayDeque<PacketFrame> queue = new ArrayDeque<>();
    private final int capacity, batch;
    private final Executor executor;
    private final Consumer<PacketFrame> processor;
    private final Runnable gap;
    private final LongSupplier epoch, clock;
    private final Consumer<RuntimeException> failure;
    private final PacketMetrics metrics;
    private boolean scheduled, closed;
    private long sequence;
    public SerialPacketQueue(int capacity, int batch, Executor executor, Consumer<PacketFrame> processor,
            Runnable gap, LongSupplier epoch, LongSupplier clock, Consumer<RuntimeException> failure, PacketMetrics metrics) {
        if (capacity < 1 || batch < 1) throw new IllegalArgumentException("Positive queue bounds required");
        this.capacity = capacity; this.batch = batch; this.executor = executor; this.processor = processor;
        this.gap = gap; this.epoch = epoch; this.clock = clock; this.failure = failure; this.metrics = metrics;
        metrics.opened();
    }
    public synchronized boolean submit(PacketDirection direction, ClientProtocol protocol, NormalizedPacket packet, long now) {
        if (closed) return false;
        if (queue.size() == capacity) discard(true);
        queue.addLast(new PacketFrame(++sequence, epoch.getAsLong(), now, direction, protocol, packet));
        metrics.enqueued();
        if (!scheduled) { scheduled = true; return schedule(); }
        return true;
    }
    private boolean schedule() {
        try { executor.execute(this::drain); return true; }
        catch (RejectedExecutionException rejected) {
            metrics.executorRejected(); scheduled = false; discard(true); return false;
        }
    }
    private void drain() {
        for (int i = 0; i < batch; i++) {
            PacketFrame frame;
            synchronized (this) {
                if (closed || (frame = queue.pollFirst()) == null) { scheduled = false; return; }
                metrics.dequeued();
            }
            long start = clock.getAsLong();
            try { processor.accept(frame); metrics.processed(clock.getAsLong() - start); }
            catch (RuntimeException problem) {
                synchronized (this) { metrics.failed(); discard(true); }
                failure.accept(problem);
            }
        }
        synchronized (this) {
            if (closed || queue.isEmpty()) scheduled = false;
            else schedule(); // scheduled remains true while transferring consumer ownership
        }
    }
    /** Invalid decoding is ordered with ingress. In-flight older work remains visibly uncertain. */
    public synchronized void invalidate() { if (!closed) discard(true); }
    private void discard(boolean uncertain) {
        int size = queue.size(); queue.clear(); metrics.discarded(size);
        if (uncertain) gap.run();
    }
    public synchronized int size() { return queue.size(); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; discard(true); metrics.closed();
    }
}
