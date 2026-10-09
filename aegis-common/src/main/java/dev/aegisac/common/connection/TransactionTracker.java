package dev.aegisac.common.connection;

import dev.aegisac.common.packet.NormalizedPacket.TimingKind;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Worker-owned bounded correlation. Streams are independent (ping, keepalive, window, teleport).
 * Duplicate/reused IDs are ambiguous and never produce RTT samples. Reordered responses retire
 * their exact token but produce no sample/fence; unrelated pending tokens are not acknowledged.
 */
public final class TransactionTracker {
    public enum Outcome { MATCHED, UNKNOWN, DUPLICATE, OUT_OF_ORDER, AMBIGUOUS, INVALID_TIME }
    public record Acknowledgement(Outcome outcome, long rttNanos) {
        public boolean sampled() { return outcome == Outcome.MATCHED; }
    }
    private record Key(TimingKind kind, long id, int window) { }
    private record Pending(long sent, long sequence, boolean ambiguous) { }
    private record Retired(Key key, long time) { }
    private final LinkedHashMap<Key, Pending> pending = new LinkedHashMap<>();
    private final ArrayDeque<Retired> retired = new ArrayDeque<>();
    private final int capacity;
    private final long timeout;
    private long serial, matched, unknown, duplicates, outOfOrder, expired, evicted;
    public TransactionTracker(int capacity, long timeoutNanos) {
        if (capacity < 1 || timeoutNanos <= 0) throw new IllegalArgumentException("Positive timing bounds required");
        this.capacity = capacity; this.timeout = timeoutNanos;
    }
    public void sent(TimingKind kind, long id, int window, long now) {
        expire(now);
        Key key = new Key(kind, id, window);
        Pending old = pending.get(key);
        if (old != null) {
            pending.put(key, new Pending(old.sent(), old.sequence(), true));
            duplicates++;
            return;
        }
        boolean reused = retired.stream().anyMatch(item -> item.key().equals(key));
        if (pending.size() == capacity) {
            var first = pending.entrySet().iterator();
            var entry = first.next(); first.remove(); retire(entry.getKey(), now); evicted++;
        }
        pending.put(key, new Pending(now, ++serial, reused));
    }
    public Acknowledgement acknowledge(TimingKind kind, long id, int window, long now) {
        expire(now);
        Key key = new Key(kind, id, window);
        Pending entry = pending.remove(key);
        if (entry == null) {
            for (Retired item : retired) if (item.key().equals(key)) {
                duplicates++; return new Acknowledgement(Outcome.DUPLICATE, -1);
            }
            unknown++; return new Acknowledgement(Outcome.UNKNOWN, -1);
        }
        retire(key, now);
        if (entry.ambiguous()) { duplicates++; return new Acknowledgement(Outcome.AMBIGUOUS, -1); }
        long rtt = now - entry.sent();
        if (rtt < 0) { unknown++; return new Acknowledgement(Outcome.INVALID_TIME, -1); }
        for (Map.Entry<Key, Pending> other : pending.entrySet()) {
            if (other.getKey().kind() == kind && other.getValue().sequence() < entry.sequence()) {
                outOfOrder++; return new Acknowledgement(Outcome.OUT_OF_ORDER, -1);
            }
        }
        matched++;
        return new Acknowledgement(Outcome.MATCHED, rtt);
    }
    public void expire(long now) {
        Iterator<Map.Entry<Key, Pending>> entries = pending.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (now - entry.getValue().sent() >= timeout) {
                entries.remove(); retire(entry.getKey(), now); expired++;
            }
        }
        while (!retired.isEmpty() && now - retired.peekFirst().time() >= timeout) retired.removeFirst();
    }
    private void retire(Key key, long now) {
        if (retired.size() == capacity) retired.removeFirst();
        retired.addLast(new Retired(key, now));
    }
    /** Discard uncertain pending correlation on a queue gap/world transition; retain replay history. */
    public void invalidate(long now) {
        for (Key key : pending.keySet()) retire(key, now);
        pending.clear();
    }
    public Stats stats() { return new Stats(pending.size(), matched, unknown, duplicates, outOfOrder, expired, evicted); }
    public record Stats(int pending, long matched, long unknown, long duplicates, long outOfOrder, long expired, long evicted) { }
}
