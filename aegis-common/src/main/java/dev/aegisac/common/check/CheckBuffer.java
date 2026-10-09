package dev.aegisac.common.check;
import dev.aegisac.common.config.MovementSettings.Rule;
/** Bounded sample buffer with time-based decay. Suppressed data never accumulates violation state. */
public final class CheckBuffer {
    private double value;
    private int streak;
    private long lastTime,lastFinding;
    private boolean seen,findingSeen;
    public double value() { return value; }
    public void reset() { value=0; streak=0; seen=false; findingSeen=false; }
    public boolean accept(long now,boolean mismatch,Rule rule) {
        if(seen) {
            long elapsed=now-lastTime;
            if(elapsed<0) { reset(); }
            else value=Math.max(0,value-rule.decay()*elapsed/1_000_000_000.0);
        }
        lastTime=now; seen=true;
        if(!mismatch) { streak=0; return false; }
        streak=Math.min(rule.minimumSamples(),streak+1);
        value=Math.min(rule.threshold()*2,value+rule.increment());
        if(streak>=rule.minimumSamples() && value>=rule.threshold()
                && (!findingSeen || now-lastFinding>=rule.cooldownMillis()*1_000_000L)) {
            lastFinding=now; findingSeen=true; return true;
        }
        return false;
    }
}
