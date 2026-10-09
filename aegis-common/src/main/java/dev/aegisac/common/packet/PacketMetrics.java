package dev.aegisac.common.packet;
import java.util.concurrent.atomic.LongAdder;
/** All retained counters are constant-space. Queue gauges stay enabled for correctness/health. */
public final class PacketMetrics {
    private final LongAdder inbound = new LongAdder(), outbound = new LongAdder(), untracked = new LongAdder();
    private final LongAdder enqueued = new LongAdder(), processed = new LongAdder(), dropped = new LongAdder();
    private final LongAdder decodeRejected = new LongAdder(), executorRejected = new LongAdder(), failures = new LongAdder();
    private final LongAdder queued = new LongAdder(), active = new LongAdder(), nanos = new LongAdder();
    private final LongAdder checkEvaluations=new LongAdder(),movementEvaluations=new LongAdder(),movementNanos=new LongAdder();
    public void evaluated() { checkEvaluations.increment(); }
    public void movementEvaluation(long elapsed) { movementEvaluations.increment();movementNanos.add(Math.max(0,elapsed)); }
    private final LongAdder checkBatches=new LongAdder(),checkNanos=new LongAdder(),debugDropped=new LongAdder();
    public void checkBatch(long elapsed) { checkBatches.increment();checkNanos.add(Math.max(0,elapsed)); }
    public void debugDropped(long count) { debugDropped.add(count); }
    public AnalysisSnapshot analysis() { return new AnalysisSnapshot(checkBatches.sum(),checkNanos.sum(),debugDropped.sum(),checkEvaluations.sum(),movementEvaluations.sum(),movementNanos.sum()); }
    /** One batch is the guard + movement + combat dispatch for a normalized packet (including gates).
     * It includes physics/state work between those dispatches; it is not an individual evaluator timer. */
    public record AnalysisSnapshot(long batches,long nanos,long droppedDebugRecords,long checkEvaluations,long movementEvaluations,long movementNanos) {
        public double averageMovementMicros() { return movementEvaluations==0?0:movementNanos/(1000.0*movementEvaluations); }
        public double averageMicros() { return batches==0?0:nanos/(1000.0*batches); }
    }
    public void tracked(PacketDirection direction) { (direction == PacketDirection.INBOUND ? inbound : outbound).increment(); }
    public void untracked() { untracked.increment(); }
    public void enqueued() { enqueued.increment(); queued.increment(); }
    public void dequeued() { queued.decrement(); }
    public void discarded(int count) { dropped.add(count); queued.add(-count); }
    public void processed(long elapsed) { processed.increment(); nanos.add(Math.max(0, elapsed)); }
    public void decodeRejected() { decodeRejected.increment(); }
    public void executorRejected() { executorRejected.increment(); }
    public void failed() { failures.increment(); }
    public void opened() { active.increment(); }
    public void closed() { active.decrement(); }
    public Snapshot snapshot() { return new Snapshot(inbound.sum(), outbound.sum(), untracked.sum()); }
    public PipelineSnapshot pipeline() {
        return new PipelineSnapshot(enqueued.sum(), processed.sum(), dropped.sum(), decodeRejected.sum(),
                executorRejected.sum(), failures.sum(), queued.sum(), active.sum(), nanos.sum());
    }
    public record Snapshot(long inbound, long outbound, long untracked) { }
    public record PipelineSnapshot(long enqueued, long processed, long dropped, long decodeRejected,
                                   long executorRejected, long failures, long queued, long activeQueues, long processingNanos) { }
}
