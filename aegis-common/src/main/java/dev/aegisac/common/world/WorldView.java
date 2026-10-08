package dev.aegisac.common.world;
import java.util.concurrent.atomic.AtomicReference;
/** CAS publication fences a capture against invalidation; no lock shared with packet analysis. */
public final class WorldView {
    public record State(long revision, WorldSnapshot snapshot, boolean closed) { }
    private final AtomicReference<State> state = new AtomicReference<>(new State(0,null,false));
    public State read() { return state.get(); }
    public void invalidate() { state.updateAndGet(s->new State(s.revision()+1,null,s.closed())); }
    public boolean publish(State expected, WorldSnapshot snapshot) {
        if (expected.closed() || snapshot.revision()!=expected.revision()) return false;
        return state.compareAndSet(expected,new State(expected.revision(),snapshot,false));
    }
    public void close() { state.updateAndGet(s->new State(s.revision()+1,null,true)); }
}
