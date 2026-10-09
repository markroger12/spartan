package dev.aegisac.common.check;
/** Tick cadence, not MSPT. Volatile immutable publication from the owning scheduler. */
public final class ServerTickHealth {
    public record Snapshot(long observedNanos,long intervalNanos,double tps,boolean known) {
        public static Snapshot unknown() { return new Snapshot(0,0,0,false); }
    }
    private volatile Snapshot snapshot=Snapshot.unknown();
    private boolean seen;
    private long last;
    private double average=50_000_000;
    public void tick(long now) {
        if(!seen) { seen=true; last=now; return; }
        long interval=now-last; last=now;
        if(interval<=0) { snapshot=Snapshot.unknown(); return; }
        average+=.1*(interval-average);
        snapshot=new Snapshot(now,interval,Math.min(20,1_000_000_000.0/average),true);
    }
    public Snapshot snapshot() { return snapshot; }
}
