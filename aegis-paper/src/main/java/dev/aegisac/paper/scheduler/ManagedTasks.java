package dev.aegisac.paper.scheduler;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
/** Admission is bounded. Cancellation before native handle publication still cancels that handle. */
final class ManagedTasks implements AutoCloseable {
    private final Set<Entry> tasks=new HashSet<>(); private final int capacity; private boolean closed;
    ManagedTasks(int capacity) { if(capacity<1) throw new IllegalArgumentException("Positive task capacity required"); this.capacity=capacity; }
    synchronized Entry reserve(boolean repeat,Runnable action,Runnable retired) {
        if(closed) throw new RejectedExecutionException("Scheduler is closed");
        if(tasks.size()>=capacity) throw new RejectedExecutionException("Scheduler task capacity reached");
        var entry=new Entry(repeat,Objects.requireNonNull(action),Objects.requireNonNull(retired)); tasks.add(entry); return entry;
    }
    synchronized int size() { return tasks.size(); }
    private synchronized void remove(Entry entry) { tasks.remove(entry); }
    @Override public void close() {
        List<Entry> copy; synchronized(this) { if(closed) return; closed=true; copy=List.copyOf(tasks); }
        copy.forEach(Entry::cancel);
    }
    final class Entry implements PlatformScheduler.Task {
        private enum State { WAITING,RUNNING,FINISHED,CANCELLED,RETIRED }
        private final boolean repeat; private final Runnable action,retired;
        private State state=State.WAITING; private Runnable cancellation;
        Entry(boolean repeat,Runnable action,Runnable retired) { this.repeat=repeat; this.action=action; this.retired=retired; }
        void bind(Runnable cancel) {
            boolean terminal; synchronized(this) { cancellation=Objects.requireNonNull(cancel); terminal=done(); }
            if(terminal) cancel.run();
        }
        void run() { runIf(()->true); }
        void runIf(java.util.function.BooleanSupplier current) {
            synchronized(ManagedTasks.this) { synchronized(this) { if(closed||state!=State.WAITING) return; state=State.RUNNING; } }
            boolean success=false;
            try { if(!current.getAsBoolean()) { retire(); success=true; return; } action.run(); success=true; }
            finally {
                boolean terminal;
                synchronized(this) { if(state==State.RUNNING) state=success&&repeat?State.WAITING:State.FINISHED; terminal=done(); }
                if(terminal) { remove(this); if(!success) cancelNative(); }
            }
        }
        void retire() {
            synchronized(this) { if(done()) return; state=State.RETIRED; }
            remove(this); cancelNative(); retired.run();
        }
        @Override public boolean cancel() {
            synchronized(this) { if(done()) return false; state=State.CANCELLED; }
            remove(this); cancelNative(); return true;
        }
        private void cancelNative() { Runnable value; synchronized(this) { value=cancellation; } if(value!=null) value.run(); }
        @Override public synchronized boolean done() { return state!=State.WAITING&&state!=State.RUNNING; }
    }
    static void ticks(long delay,long period) { if(delay<1||period<0) throw new IllegalArgumentException("Delay must be >=1 tick; period must be >=0"); }
    static void millis(long delay,long period) { if(delay<0||period<0) throw new IllegalArgumentException("Delay/period must be >=0 milliseconds"); }
}
