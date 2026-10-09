package dev.aegisac.common.packet;
import dev.aegisac.common.config.PipelineSettings;
import dev.aegisac.common.player.PlayerData;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

/** Transport identity is checked before enqueue; disconnect invalidates all pending work. */
public final class PacketRouter<C> implements AutoCloseable {
    private final ConcurrentHashMap<UUID, Binding<C>> bindings = new ConcurrentHashMap<>();
    private final PacketMetrics metrics;
    private final BooleanSupplier metricsEnabled;
    private final PipelineSettings settings;
    private final Executor executor;
    private final boolean ownsExecutor;
    private final LongSupplier clock;
    private final PacketListener processor;
    private final Consumer<RuntimeException> failures;
    private final AtomicBoolean reported = new AtomicBoolean();
    private boolean closed;
    public PacketRouter(PacketMetrics metrics, BooleanSupplier metricsEnabled, PipelineSettings settings,
                        Consumer<RuntimeException> failures) {
        this(metrics, metricsEnabled, settings, workers(settings), true, System::nanoTime,
                new PacketProcessor(System::nanoTime), failures);
    }
    public PacketRouter(PacketMetrics metrics, BooleanSupplier metricsEnabled, PipelineSettings settings,
                        Executor executor, LongSupplier clock, PacketListener processor, Consumer<RuntimeException> failures) {
        this(metrics, metricsEnabled, settings, executor, false, clock, processor, failures);
    }
    private PacketRouter(PacketMetrics metrics, BooleanSupplier metricsEnabled, PipelineSettings settings,
            Executor executor, boolean ownsExecutor, LongSupplier clock, PacketListener processor, Consumer<RuntimeException> failures) {
        this.metrics = metrics; this.metricsEnabled = metricsEnabled; this.settings = settings;
        this.executor = executor; this.ownsExecutor = ownsExecutor; this.clock = clock;
        this.processor = processor; this.failures = failures;
    }
    private static ExecutorService workers(PipelineSettings settings) {
        AtomicInteger counter = new AtomicInteger();
        return new ThreadPoolExecutor(settings.workers(), settings.workers(), 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.executorCapacity()), runnable -> {
                    Thread thread = new Thread(runnable, "AegisAC-packets-" + counter.incrementAndGet());
                    thread.setDaemon(true); return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }
    public synchronized void attach(UUID uuid, C connection, PlayerData data) {
        if (closed) throw new IllegalStateException("Packet router is closed");
        java.util.Objects.requireNonNull(connection);
        data.configure(settings);
        data.configureMetrics(metrics);
        var queue = new SerialPacketQueue(settings.sessionCapacity(), settings.batchSize(), executor,
                frame -> processor.onPacket(data, frame), data::markGap, data::lossEpoch, clock,
                problem -> { if (reported.compareAndSet(false, true)) failures.accept(problem); }, metrics);
        Binding<C> previous = bindings.put(uuid, new Binding<>(connection, data, queue));
        if (previous != null) previous.queue().close();
    }
    public synchronized void detach(UUID uuid) {
        Binding<C> removed = bindings.remove(uuid);
        if (removed != null) removed.queue().close();
    }
    public boolean attached(UUID uuid, C connection) {
        Binding<C> binding = uuid == null ? null : bindings.get(uuid);
        return binding != null && binding.connection() == connection;
    }
    public void observe(UUID uuid, C connection, PacketDirection direction, long now, ClientProtocol protocol, NormalizedPacket packet) {
        Binding<C> binding = uuid == null ? null : bindings.get(uuid);
        if (binding == null || binding.connection() != connection) {
            if (metricsEnabled.getAsBoolean()) metrics.untracked();
            return;
        }
        if (packet.kind() == PacketKind.WORLD_CHANGE || packet.kind() == PacketKind.WORLD_RESET
                || packet.kind() == PacketKind.TELEPORT) binding.data().world().invalidate();
        if (metricsEnabled.getAsBoolean()) metrics.tracked(direction);
        binding.queue().submit(direction, protocol, packet, now);
    }
    public void invalid(UUID uuid, C connection) {
        metrics.decodeRejected();
        invalidate(uuid, connection);
    }
    public void invalidate(UUID uuid, C connection) {
        Binding<C> binding = uuid == null ? null : bindings.get(uuid);
        if (binding != null && binding.connection() == connection) binding.queue().invalidate();
    }
    public void forEachConnection(Consumer<C> action) { bindings.values().forEach(binding -> action.accept(binding.connection())); }
    public PipelineSettings settings() { return settings; }
    public synchronized void clear() {
        bindings.values().forEach(binding -> binding.queue().close()); bindings.clear();
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; clear();
        if (ownsExecutor) ((ExecutorService) executor).shutdownNow();
    }
    private record Binding<C>(C connection, PlayerData data, SerialPacketQueue queue) { }
}
