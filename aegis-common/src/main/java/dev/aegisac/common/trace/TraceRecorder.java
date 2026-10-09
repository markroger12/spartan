package dev.aegisac.common.trace;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** One explicitly selected development session. Nonblocking offers; bounded queue, lifetime and disk.
 * close() requests draining on the daemon writer; it never blocks a server/packet thread on disk.
 */
public final class TraceRecorder implements AutoCloseable {
    private final ArrayBlockingQueue<TraceEntry> queue=new ArrayBlockingQueue<>(32);
    private final AtomicBoolean accepting=new AtomicBoolean(true);
    private final AtomicLong dropped=new AtomicLong();
    private final dev.aegisac.common.packet.PacketMetrics metrics;
    private final CompletableFuture<Path> completion=new CompletableFuture<>();
    private final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
    public TraceRecorder(Path directory,dev.aegisac.common.packet.PacketMetrics metrics,Consumer<IOException> failures) {
        this.metrics=metrics;
        Thread worker=new Thread(()->write(directory,failures),"AegisAC-development-trace");worker.setDaemon(true);worker.start();
    }
    public boolean accepting() { return accepting.get(); }
    public synchronized void offer(TraceEntry entry) {
        if(!accepting.get())return;
        if(System.nanoTime()-deadline>=0) { accepting.set(false);return; }
        if(!queue.offer(entry))drop(1);
    }
    private void drop(long count) { dropped.addAndGet(count);metrics.debugDropped(count); }
    private void write(Path directory,Consumer<IOException> failures) {
        try {
            Files.createDirectories(directory);
            if(Files.isSymbolicLink(directory))throw new IOException("Trace directory must not be a symlink");
            // No automatic deletion. A full development directory requires deliberate operator cleanup.
            long size=0,count=0;
            try(var files=Files.newDirectoryStream(directory)) {
                for(Path file:files) { count++;size+=Files.size(file);if(count>=8||size>7L*TraceFile.MAX_BYTES)throw new IOException("Trace directory budget exceeded (8 files / 128 MiB)"); }
            }
            Path path=directory.resolve("trace-"+java.util.UUID.randomUUID()+".aegistrace");
            String reason="STOPPED";
            try(var writer=new TraceFile.Writer(path)) {
                while(accepting.get()||!queue.isEmpty()) {
                    if(System.nanoTime()-deadline>=0) { close();reason="DURATION_LIMIT"; }
                    var entry=queue.poll(100,TimeUnit.MILLISECONDS);
                    if(entry!=null&&!writer.append(entry)) {
                        synchronized(this) { accepting.set(false);drop(1L+queue.size());queue.clear(); }
                        reason="RECORD_OR_BYTE_LIMIT";break;
                    }
                }
                writer.finish(dropped.get(),reason);
            }
            completion.complete(path);
        } catch(IOException|InterruptedException|RuntimeException error) {
            synchronized(this) { accepting.set(false);drop(queue.size());queue.clear(); }
            if(error instanceof InterruptedException)Thread.currentThread().interrupt();
            var problem=error instanceof IOException io?io:new IOException(error);
            completion.completeExceptionally(problem);failures.accept(problem);
        }
    }
    public CompletableFuture<Path> completion() { return completion; }
    @Override public synchronized void close() { accepting.set(false); }
}
