package dev.aegisac.common.guard;
/** Twenty fixed 50ms buckets: conservatively drops the oldest partial bucket. */
public final class RateWindow {
    private final long[] ticks=new long[20];
    private final int[] counts=new int[20];
    private long first,last;
    private boolean seen;
    public void clear() { java.util.Arrays.fill(counts,0); seen=false; }
    public int add(long now) {
        if(seen&&now-last<0) clear();
        if(!seen) { first=now; seen=true; }
        last=now; long tick=Math.floorDiv(now,50_000_000L); int index=Math.floorMod(tick,20);
        if(counts[index]==0||ticks[index]!=tick) { ticks[index]=tick; counts[index]=0; }
        counts[index]=Math.min(1_000_000,counts[index]+1); return count(now);
    }
    public boolean ready(long now) { return seen&&now-first>=1_000_000_000L; }
    public int count(long now) {
        long tick=Math.floorDiv(now,50_000_000L); int sum=0;
        for(int i=0;i<20;i++) if(tick-ticks[i]>=0&&tick-ticks[i]<20) sum+=counts[i];
        return sum;
    }
}
