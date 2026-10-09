package dev.aegisac.common.check;
/** Movement-only arrival timing. Two-second windows and a bounded time bank absorb ordinary bunching. */
public final class MovementTiming {
    public record Sample(Check.Evaluation timer,Check.Evaluation blink) { }
    private long start,last,bank,burstUntil;
    private int burstCount;
    private boolean seen,burst;
    public void clearDebt() { bank=0; start=last; }
    public void reset() { seen=false; burst=false; bank=0; }
    public Sample observe(long now,int windowMillis,int nominalTickMillis,int bankMillis,int silenceMillis,int burstMillis,int burstPackets) {
        var absent=Check.Evaluation.absent();
        if(!seen) { seen=true; start=last=now; return new Sample(absent,absent); }
        long gap=now-last;
        if(gap<0) { reset(); return new Sample(absent,absent); }
        last=now;
        if(gap>=silenceMillis*1_000_000L) { burst=true; burstCount=1; burstUntil=now+burstMillis*1_000_000L; }
        else if(burst && now-burstUntil<=0) burstCount++;
        Check.Evaluation blink=absent;
        if(burst && burstCount>=burstPackets) {
            blink=Check.Evaluation.above(burstCount,burstPackets-1); burst=false;
        } else if(burst && now-burstUntil>0) burst=false;
        bank=Math.max(-bankMillis*1_000_000L,Math.min(10_000_000_000L,bank+nominalTickMillis*1_000_000L-gap));
        Check.Evaluation timer=absent;
        if(now-start>=windowMillis*1_000_000L) {
            // Positive bank persists across windows; absence creates bounded credit, never negative evidence.
            timer=Check.Evaluation.above(bank/1_000_000.0,0); start=now;
        }
        return new Sample(timer,blink);
    }
}
