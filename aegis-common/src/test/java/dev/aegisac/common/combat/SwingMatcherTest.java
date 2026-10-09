package dev.aegisac.common.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SwingMatcherTest {
    @Test void swingsOnEitherSideMatchOnceAndExactBoundaryIsIncluded() {
        var m=new SwingMatcher(4); m.swing(10,10); m.attack(new SwingMatcher.Attack(1,20,1),10);
        m.attack(new SwingMatcher.Attack(2,21,1),10); m.swing(31,10);
        m.attack(new SwingMatcher.Attack(3,32,1),10);
        var result=m.expire(43,10); assertEquals(3,result.size());
        assertTrue(result.get(0).matched()); assertTrue(result.get(1).matched()); assertFalse(result.get(2).matched());
    }
    @Test void expirationNeedsObservedProgressAndLateSwingCannotRepairHistory() {
        var m=new SwingMatcher(4); m.attack(new SwingMatcher.Attack(1,0,1),10);
        assertTrue(m.expire(10,10).isEmpty()); m.swing(11,10);
        assertFalse(m.expire(11,10).getFirst().matched());
    }
    @Test void overflowAndClearDiscardAmbiguousSequences() {
        var m=new SwingMatcher(1); m.attack(new SwingMatcher.Attack(1,0,1),10); m.attack(new SwingMatcher.Attack(2,1,2),10);
        assertTrue(m.consumeLoss()); assertFalse(m.consumeLoss());
        assertEquals(2,m.expire(20,10).getFirst().attack().sequence()); m.swing(30,10); m.clear();
        m.attack(new SwingMatcher.Attack(3,31,3),10); assertFalse(m.expire(42,10).getFirst().matched());
    }
}
