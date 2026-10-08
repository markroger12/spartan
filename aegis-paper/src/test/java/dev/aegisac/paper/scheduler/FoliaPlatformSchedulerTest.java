package dev.aegisac.paper.scheduler;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
/** Native Folia API fixtures, independent of MockBukkit and its conventional scheduler. Not live-server certification. */
class FoliaPlatformSchedulerTest {
    Plugin plugin; Server server; Entity entity; World world;
    GlobalRegionScheduler global; RegionScheduler region; EntityScheduler entities; AsyncScheduler async;
    FoliaPlatformScheduler scheduler;
    final List<Consumer<ScheduledTask>> callbacks=new ArrayList<>(); final List<Runnable> retirements=new ArrayList<>(); final List<ScheduledTask> handles=new ArrayList<>();
    @BeforeEach void setup() {
        plugin=mock(Plugin.class); server=mock(Server.class); entity=mock(Entity.class); world=mock(World.class);
        global=mock(GlobalRegionScheduler.class); region=mock(RegionScheduler.class); entities=mock(EntityScheduler.class); async=mock(AsyncScheduler.class);
        when(plugin.getServer()).thenReturn(server); when(server.getGlobalRegionScheduler()).thenReturn(global); when(server.getRegionScheduler()).thenReturn(region); when(server.getAsyncScheduler()).thenReturn(async); when(entity.getScheduler()).thenReturn(entities);
        when(server.isGlobalTickThread()).thenReturn(true); when(server.isOwnedByCurrentRegion(entity)).thenReturn(true);
        when(server.isOwnedByCurrentRegion(eq(world),anyInt(),anyInt(),anyInt(),anyInt())).thenReturn(true);
        when(global.runDelayed(eq(plugin),any(),anyLong())).thenAnswer(c->capture(c.getArgument(1)));
        when(global.runAtFixedRate(eq(plugin),any(),anyLong(),anyLong())).thenAnswer(c->capture(c.getArgument(1)));
        when(region.runDelayed(eq(plugin),eq(world),anyInt(),anyInt(),any(),anyLong())).thenAnswer(c->capture(c.getArgument(4)));
        when(region.runAtFixedRate(eq(plugin),eq(world),anyInt(),anyInt(),any(),anyLong(),anyLong())).thenAnswer(c->capture(c.getArgument(4)));
        when(entities.runDelayed(eq(plugin),any(),any(),anyLong())).thenAnswer(c->{ retirements.add(c.getArgument(2)); return capture(c.getArgument(1)); });
        when(entities.runAtFixedRate(eq(plugin),any(),any(),anyLong(),anyLong())).thenAnswer(c->{ retirements.add(c.getArgument(2)); return capture(c.getArgument(1)); });
        when(async.runNow(eq(plugin),any())).thenAnswer(c->capture(c.getArgument(1)));
        when(async.runDelayed(eq(plugin),any(),anyLong(),eq(TimeUnit.MILLISECONDS))).thenAnswer(c->capture(c.getArgument(1)));
        when(async.runAtFixedRate(eq(plugin),any(),anyLong(),anyLong(),eq(TimeUnit.MILLISECONDS))).thenAnswer(c->capture(c.getArgument(1)));
        scheduler=new FoliaPlatformScheduler(plugin,8);
    }
    ScheduledTask capture(Consumer<ScheduledTask> callback) { var h=mock(ScheduledTask.class); callbacks.add(callback); handles.add(h); return h; }
    void run(int index) { callbacks.get(index).accept(handles.get(index)); }
    @AfterEach void cleanup() { scheduler.close(); verify(server,never()).getScheduler(); }
    @Test void domainsDispatchToTheirNativeSchedulersWithCorrectUnitsAndChunkCoordinates() {
        var calls=new AtomicInteger(); scheduler.global(2,0,calls::incrementAndGet);
        scheduler.region(world,-2,3,4,0,calls::incrementAndGet);
        scheduler.entity(entity,5,0,()->true,calls::incrementAndGet,()->fail("unexpected retirement"));
        scheduler.async(250,0,calls::incrementAndGet);
        verify(global).runDelayed(eq(plugin),any(),eq(2L)); verify(region).runDelayed(eq(plugin),eq(world),eq(-2),eq(3),any(),eq(4L));
        verify(entities).runDelayed(eq(plugin),any(),any(),eq(5L)); verify(async).runDelayed(eq(plugin),any(),eq(250L),eq(TimeUnit.MILLISECONDS));
        for(int i=0;i<4;i++) run(i); assertEquals(4,calls.get()); assertEquals(0,scheduler.pendingTasks());
    }
    @Test void recurringDomainsPreservePeriodsAndExplicitCancellation() {
        var calls=new AtomicInteger(); var tasks=List.of(scheduler.global(1,2,calls::incrementAndGet),scheduler.region(world,0,0,1,3,calls::incrementAndGet),scheduler.entity(entity,1,4,()->true,calls::incrementAndGet,()->{}),scheduler.async(0,5,calls::incrementAndGet));
        for(int i=0;i<4;i++) { run(i); run(i); assertTrue(tasks.get(i).cancel()); run(i); verify(handles.get(i)).cancel(); }
        verify(async).runAtFixedRate(eq(plugin),any(),eq(0L),eq(5L),eq(TimeUnit.MILLISECONDS)); assertEquals(8,calls.get()); assertEquals(0,scheduler.pendingTasks());
    }
    @Test void globalOwnershipDoesNotAuthorizeEntityOrRegionReads() {
        when(server.isOwnedByCurrentRegion(entity)).thenReturn(false);
        when(server.isOwnedByCurrentRegion(eq(world),anyInt(),anyInt(),anyInt(),anyInt())).thenReturn(false);
        assertTrue(scheduler.globalThread()); assertThrows(IllegalStateException.class,()->scheduler.requireEntity(entity));
        assertThrows(IllegalStateException.class,()->scheduler.requireRegion(world,0,0,1,1));
        var task=scheduler.entity(entity,1,0,()->{ fail("validator must not read off-owner"); return true; },()->fail("off-owner action"),()->{});
        assertThrows(IllegalStateException.class,()->run(0)); assertTrue(task.done()); verify(handles.getFirst()).cancel();
    }
    @Test void entityRetirementOrRejectedScheduleIsExactlyOnce() {
        var retired=new AtomicInteger(); var task=scheduler.entity(entity,1,1,()->true,()->fail("retired entity"),retired::incrementAndGet);
        retirements.getFirst().run(); retirements.getFirst().run(); run(0); assertEquals(1,retired.get()); assertTrue(task.done());
        when(entities.runDelayed(eq(plugin),any(),any(),anyLong())).thenReturn(null);
        assertTrue(scheduler.entity(entity,1,0,()->true,()->fail("unregistered"),retired::incrementAndGet).done()); assertEquals(2,retired.get());
    }
    @Test void replacedSessionCannotExecuteOrRescheduleRepeatingWork() {
        var current=new AtomicBoolean(true); var retired=new AtomicInteger(); var calls=new AtomicInteger();
        var task=scheduler.entity(entity,1,1,current::get,calls::incrementAndGet,retired::incrementAndGet);
        run(0); current.set(false); run(0); current.set(true); run(0);
        assertEquals(1,calls.get()); assertEquals(1,retired.get()); assertTrue(task.done()); verify(handles.getFirst()).cancel();
    }
    @Test void schedulerRejectionReleasesCapacityAndCloseCancelsOwnedTasksOnly() {
        when(global.runDelayed(eq(plugin),any(),anyLong())).thenThrow(new RejectedExecutionException("disabled"));
        assertThrows(RejectedExecutionException.class,()->scheduler.global(1,0,()->{})); assertEquals(0,scheduler.pendingTasks());
        scheduler.async(0,0,()->fail("closed")); scheduler.close(); run(0); verify(handles.getFirst()).cancel();
        verify(global,never()).cancelTasks(any()); verify(async,never()).cancelTasks(any());
        assertThrows(RejectedExecutionException.class,()->scheduler.async(0,0,()->{}));
    }
    @Test void invalidDelaysRejectWithoutAllocatingOrCallingNativeSchedulers() {
        assertThrows(IllegalArgumentException.class,()->scheduler.global(0,0,()->{}));
        assertThrows(IllegalArgumentException.class,()->scheduler.entity(entity,1,-1,()->true,()->{},()->{}));
        assertThrows(IllegalArgumentException.class,()->scheduler.async(-1,0,()->{})); assertEquals(0,scheduler.pendingTasks()); assertTrue(callbacks.isEmpty());
    }
    @Test void noPrimaryThreadFallbackIsUsedForOwnership() {
        when(server.isPrimaryThread()).thenReturn(true); when(server.isGlobalTickThread()).thenReturn(false);
        scheduler.global(1,0,()->fail("not global")); assertThrows(IllegalStateException.class,()->run(0));
        verify(server,never()).isPrimaryThread(); assertEquals(0,scheduler.pendingTasks());
    }
}
