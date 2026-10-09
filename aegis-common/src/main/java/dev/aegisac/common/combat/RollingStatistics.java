package dev.aegisac.common.combat;
/** Primitive ring; O(capacity) analysis only when a check needs a window, no unbounded interval list. */
public final class RollingStatistics {
    public record Summary(int samples,double mean,double deviation,double variation,double repeatRatio) { }
    private final double[] values;
    private int size,index;
    public RollingStatistics(int capacity) { if(capacity<2||capacity>256) throw new IllegalArgumentException("Statistics budget"); values=new double[capacity]; }
    public void clear() { size=index=0; java.util.Arrays.fill(values,0); }
    public void add(double value) {
        if(!Double.isFinite(value)||value<0) throw new IllegalArgumentException("Invalid statistic");
        values[index]=value; index=(index+1)%values.length; size=Math.min(values.length,size+1);
    }
    public Summary summary(double repeatResolution) {
        double mean=0,m2=0; int repeated=0;
        for(int i=0;i<size;i++) { double delta=values[i]-mean; mean+=delta/(i+1); m2+=delta*(values[i]-mean); }
        // Count values near the mean; paired repetition must agree with independently low variation.
        for(int i=0;i<size;i++) if(Math.abs(values[i]-mean)<=repeatResolution) repeated++;
        double deviation=size<2?0:Math.sqrt(m2/(size-1));
        return new Summary(size,mean,deviation,mean>0?deviation/mean:0,size==0?0:repeated/(double)size);
    }
}
