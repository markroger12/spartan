package dev.aegisac.common.packet;
import dev.aegisac.common.config.PipelineSettings;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
/** Explicit synthetic settings; production settings always come from validated YAML. */
public final class PipelineFixtures {
    public static final ClientProtocol PROTOCOL = new ClientProtocol(100, "fixture", true, true, true, true);
    public static PipelineSettings settings() {
        return new PipelineSettings(2, 64, 8, 2, 8192, 8, 4, 1000, 250, 1000, 0.5, false, 20);
    }
    public static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { tasks.addLast(task); }
        public void next() { tasks.removeFirst().run(); }
        public void drain() { while (!tasks.isEmpty()) next(); }
        public int pending() { return tasks.size(); }
    }
}
