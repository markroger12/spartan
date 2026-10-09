package dev.aegisac.paper.scheduler;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class ManagedTasksTest {
    @Test void cancelBeforeNativePublicationCancelsLateHandleAndDropsCallback() {
        var tasks=new ManagedTasks(1); var calls=new AtomicInteger(); var cancelled=new AtomicInteger();
        var entry=tasks.reserve(false,calls::incrementAndGet,()->{}); assertTrue(entry.cancel());
        entry.bind(cancelled::incrementAndGet); entry.run(); assertEquals(0,calls.get()); assertEquals(1,cancelled.get()); assertEquals(0,tasks.size());
    }
    @Test void immediateCompletionBeforeHandleBindingStillCleansUp() {
        var tasks=new ManagedTasks(1); var cancelled=new AtomicInteger(); var entry=tasks.reserve(false,()->{},()->{});
        entry.run(); entry.bind(cancelled::incrementAndGet); assertTrue(entry.done()); assertEquals(1,cancelled.get()); assertEquals(0,tasks.size());
    }
    @Test void capacityAndCloseAreBoundedAndNeverResurrectTasks() {
        var tasks=new ManagedTasks(1); var calls=new AtomicInteger(); var entry=tasks.reserve(true,calls::incrementAndGet,()->{});
        assertThrows(RejectedExecutionException.class,()->tasks.reserve(false,()->{},()->{}));
        tasks.close(); entry.run(); assertEquals(0,calls.get()); assertTrue(entry.done()); assertEquals(0,tasks.size());
        assertThrows(RejectedExecutionException.class,()->tasks.reserve(false,()->{},()->{})); tasks.close();
    }
    @Test void retirementIsExactlyOnceAndDistinctFromCancellation() {
        var tasks=new ManagedTasks(2); var retired=new AtomicInteger(); var calls=new AtomicInteger();
        var first=tasks.reserve(true,calls::incrementAndGet,retired::incrementAndGet);
        first.runIf(()->false); first.retire(); first.run(); assertEquals(1,retired.get()); assertEquals(0,calls.get()); assertTrue(first.done());
        var second=tasks.reserve(true,calls::incrementAndGet,retired::incrementAndGet); second.cancel(); second.retire(); assertEquals(1,retired.get());
    }
    @Test void repeatingActionOrSessionValidatorFailureStopsNativeTaskAndReleasesSlot() {
        for(boolean validation:new boolean[]{false,true}) {
            var tasks=new ManagedTasks(1); var cancelled=new AtomicInteger();
            var entry=tasks.reserve(true,()->{ throw new IllegalStateException("action"); },()->{}); entry.bind(cancelled::incrementAndGet);
            assertThrows(IllegalStateException.class,()->entry.runIf(()->{ if(validation) throw new IllegalStateException("validator"); return true; }));
            assertTrue(entry.done()); assertEquals(0,tasks.size()); assertEquals(1,cancelled.get());
        }
    }
    @Test void overlappingCallbackCannotRunSameRepeatingActionTwice() throws Exception {
        var tasks=new ManagedTasks(1); var entered=new CountDownLatch(1); var release=new CountDownLatch(1); var calls=new AtomicInteger();
        var entry=tasks.reserve(true,()->{ calls.incrementAndGet(); entered.countDown(); try { assertTrue(release.await(5,TimeUnit.SECONDS)); } catch(InterruptedException e) { throw new AssertionError(e); } },()->{});
        var thread=Thread.ofPlatform().start(entry::run);
        try { assertTrue(entered.await(5,TimeUnit.SECONDS)); entry.run(); assertEquals(1,calls.get()); tasks.close(); }
        finally { release.countDown(); thread.join(5000); }
        assertFalse(thread.isAlive()); entry.run(); assertEquals(1,calls.get()); assertEquals(0,tasks.size());
    }
}
