package dev.aegisac.common.combat;
import java.util.*;
/** Main-hand swings can precede or follow attacks; each observation is consumed at most once. */
public final class SwingMatcher {
    public record Attack(long sequence,long time,int target) { }
    public record Expired(Attack attack,boolean matched) { }
    private static final class Pending { final Attack attack; boolean matched; Pending(Attack attack) { this.attack=attack; } }
    private final ArrayDeque<Pending> pending=new ArrayDeque<>();
    private final ArrayDeque<Long> swings=new ArrayDeque<>();
    private final int capacity;
    private boolean lost;
    public SwingMatcher(int capacity) { this.capacity=capacity; }
    public void clear() { pending.clear(); swings.clear(); lost=false; }
    public boolean consumeLoss() { boolean result=lost; lost=false; return result; }
    public void attack(Attack attack,long window) {
        if(pending.size()>=capacity) { clear(); lost=true; }
        while(!swings.isEmpty() && attack.time()-swings.peekFirst()>window) swings.removeFirst();
        Pending value=new Pending(attack);
        if(!swings.isEmpty() && attack.time()-swings.peekFirst()>=0) { swings.removeFirst(); value.matched=true; }
        pending.addLast(value);
    }
    public void swing(long now,long window) {
        for(Pending value:pending) if(!value.matched && now-value.attack.time()>=0 && now-value.attack.time()<=window) {
            value.matched=true; return;
        }
        if(swings.size()>=capacity) swings.removeFirst();
        swings.addLast(now);
    }
    public List<Expired> expire(long observedNow,long window) {
        List<Expired> result=new ArrayList<>();
        while(!pending.isEmpty() && observedNow-pending.peekFirst().attack.time()>window) {
            Pending p=pending.removeFirst(); result.add(new Expired(p.attack,p.matched));
        }
        while(!swings.isEmpty() && observedNow-swings.peekFirst()>window) swings.removeFirst();
        return result;
    }
}
