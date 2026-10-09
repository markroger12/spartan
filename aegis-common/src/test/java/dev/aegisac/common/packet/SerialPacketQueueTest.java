package dev.aegisac.common.packet;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static dev.aegisac.common.packet.PipelineFixtures.*;
import static dev.aegisac.common.packet.PacketDirection.*;
import static org.junit.jupiter.api.Assertions.*;

class SerialPacketQueueTest {
    @Test void overflowDropsOldAnalysisMarksEpochAndKeepsOrdering() {
        var executor = new ManualExecutor(); var metrics = new PacketMetrics(); var epoch = new AtomicLong();
        List<PacketFrame> frames = new ArrayList<>();
        try (var queue = new SerialPacketQueue(2,1,executor,frames::add,epoch::incrementAndGet,epoch::get,
                () -> 0, problem -> fail(problem),metrics)) {
            queue.submit(INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,1);
            queue.submit(OUTBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,2);
            queue.submit(INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,3);
            assertEquals(1,queue.size()); assertEquals(1,epoch.get()); assertEquals(2,metrics.pipeline().dropped());
            executor.drain();
            assertEquals(List.of(3L),frames.stream().map(PacketFrame::sequence).toList());
            assertEquals(1,frames.getFirst().epoch()); assertEquals(0,metrics.pipeline().queued());
        }
        assertEquals(0,metrics.pipeline().activeQueues());
    }
    @Test void rejectedExecutorNeverProcessesOnCallerAndCanRecover() {
        var manual = new ManualExecutor(); var reject = new AtomicBoolean(true); var metrics = new PacketMetrics();
        var epoch = new AtomicLong(); var processed = new AtomicInteger();
        Executor executor = task -> { if(reject.get()) throw new RejectedExecutionException(); manual.execute(task); };
        try(var queue = new SerialPacketQueue(8,2,executor,frame -> processed.incrementAndGet(),epoch::incrementAndGet,epoch::get,
                () -> 0,problem -> fail(problem),metrics)) {
            assertFalse(queue.submit(INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,1));
            assertEquals(0,processed.get()); assertEquals(0,queue.size()); assertEquals(1,epoch.get());
            reject.set(false); assertTrue(queue.submit(INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,2));
            manual.drain(); assertEquals(1,processed.get()); assertEquals(1,metrics.pipeline().executorRejected());
        }
    }
    @Test void fairnessReschedulesAfterBatchInsteadOfMonopolizingWorker() {
        var manual = new ManualExecutor(); var epoch = new AtomicLong(); var seen = new ArrayList<Long>();
        try(var queue = new SerialPacketQueue(8,2,manual,frame -> seen.add(frame.sequence()),epoch::incrementAndGet,
                epoch::get,()->0,problem -> fail(problem),new PacketMetrics())) {
            for(int i=0;i<5;i++) queue.submit(INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,i);
            assertEquals(1,manual.pending()); manual.next();
            assertEquals(List.of(1L,2L),seen); assertEquals(1,manual.pending());
            manual.drain(); assertEquals(List.of(1L,2L,3L,4L,5L),seen);
        }
    }
    @Test void failureClearsPendingHistoryAndCloseCancelsScheduledWork() {
        var manual = new ManualExecutor(); var epoch = new AtomicLong(); var metrics = new PacketMetrics();
        var errors = new AtomicInteger();
        var queue = new SerialPacketQueue(8,2,manual,frame -> {throw new IllegalStateException("fixture");},
                epoch::incrementAndGet,epoch::get,()->0,problem -> errors.incrementAndGet(),metrics);
        queue.submit(INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,1);
        queue.submit(INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,2);
        manual.drain(); assertEquals(1,errors.get()); assertEquals(1,epoch.get()); assertEquals(1,metrics.pipeline().failures());
        queue.submit(INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,3);
        queue.close();queue.close();manual.drain();
        assertEquals(1,errors.get()); assertFalse(queue.submit(INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,4));
        assertEquals(0,metrics.pipeline().activeQueues()); assertEquals(0,metrics.pipeline().queued());
    }
    @Test void concurrentProducersHaveOneConsumerAndNoReordering() throws Exception {
        var epoch = new AtomicLong(); var metrics = new PacketMetrics(); var active = new AtomicInteger();
        var peak = new AtomicInteger(); var expected = new AtomicLong(); var done = new CountDownLatch(2000);
        var failure = new AtomicReference<Throwable>();
        try(var workers = Executors.newFixedThreadPool(3); var producers = Executors.newFixedThreadPool(4);
            var queue = new SerialPacketQueue(4096,16,workers,frame -> {
                int current=active.incrementAndGet();peak.accumulateAndGet(current,Math::max);
                try { if(frame.sequence()!=expected.incrementAndGet()) failure.set(new AssertionError("reordered")); }
                finally { active.decrementAndGet();done.countDown(); }
            },epoch::incrementAndGet,epoch::get,System::nanoTime,failure::set,metrics)) {
            var tasks = new ArrayList<Future<?>>();
            for(int p=0;p<4;p++) tasks.add(producers.submit(()->{for(int n=0;n<500;n++)queue.submit(INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE,System.nanoTime());}));
            for(var task:tasks)task.get(10,TimeUnit.SECONDS);
            assertTrue(done.await(10,TimeUnit.SECONDS)); assertNull(failure.get()); assertEquals(1,peak.get());
            assertEquals(2000,expected.get()); assertEquals(0,epoch.get());
        }
    }
}
