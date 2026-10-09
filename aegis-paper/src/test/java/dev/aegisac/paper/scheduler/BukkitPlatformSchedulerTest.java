package dev.aegisac.paper.scheduler;
import dev.aegisac.common.player.PlayerRegistry;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.command.BlockCommandSender;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BukkitPlatformSchedulerTest {
    ServerMock server; Plugin plugin; BukkitPlatformScheduler scheduler;
    @BeforeEach void setup() { server=MockBukkit.mock(); plugin=MockBukkit.createMockPlugin("SchedulingFixture"); scheduler=new BukkitPlatformScheduler(plugin,16); }
    @AfterEach void cleanup() { scheduler.close(); MockBukkit.unmock(); }
    @Test void globalRegionAndEntityCallbacksUsePrimaryThreadAndRequestedDelay() {
        var p=server.addPlayer(); var calls=new AtomicInteger();
        Runnable action=()->{ assertTrue(server.isPrimaryThread()); calls.incrementAndGet(); };
        scheduler.global(2,0,action); scheduler.region(p.getWorld(),-1,2,2,0,action); scheduler.entity(p,2,0,()->true,action,()->fail("unexpected retirement"));
        server.getScheduler().performTicks(1); assertEquals(0,calls.get()); server.getScheduler().performTicks(1);
        assertEquals(3,calls.get()); assertEquals(0,scheduler.pendingTasks());
    }
    @Test void repeatingTaskCanBeCancelledWithoutCancellingOtherPluginsTasks() {
        var calls=new AtomicInteger(); var unrelated=new AtomicInteger();
        var task=scheduler.global(1,1,calls::incrementAndGet);
        server.getScheduler().runTaskTimer(plugin,unrelated::incrementAndGet,1,1);
        server.getScheduler().performTicks(2); assertEquals(2,calls.get()); assertTrue(task.cancel()); scheduler.close();
        server.getScheduler().performTicks(2); assertEquals(2,calls.get()); assertEquals(4,unrelated.get());
    }
    @Test void asyncWorkUsesWallClockAndCannotClaimOwnership() throws Exception {
        var completed=new CompletableFuture<Boolean>(); var player=server.addPlayer();
        scheduler.async(0,0,()->completed.complete(!scheduler.globalThread()&&!scheduler.owns(player)));
        assertTrue(completed.get(5,TimeUnit.SECONDS));
    }
    @Test void staleSessionRetiresAndOldReplyCannotReachReplacement() {
        var registry=new PlayerRegistry(); var player=server.addPlayer();
        registry.join(player.getUniqueId(),player.getName(),0);
        var audience=SessionAudience.capture(player,registry); var replies=new AtomicInteger(); var retired=new AtomicInteger();
        audience.dispatch(scheduler,s->replies.incrementAndGet(),retired::incrementAndGet);
        registry.join(player.getUniqueId(),player.getName(),1);
        server.getScheduler().performOneTick(); assertEquals(0,replies.get()); assertEquals(1,retired.get()); assertEquals(0,scheduler.pendingTasks()); registry.close();
    }
    @Test void currentSessionReplyAndConsoleReplyRunOnCorrectOwner() {
        var registry=new PlayerRegistry(); var p=server.addPlayer(); registry.join(p.getUniqueId(),p.getName(),0);
        SessionAudience.capture(p,registry).dispatch(scheduler,s->{ assertSame(p,s); s.sendMessage("player reply"); },()->fail("retired"));
        SessionAudience.capture(server.getConsoleSender(),registry).dispatch(scheduler,s->s.sendMessage("console reply"),()->{});
        server.getScheduler().performOneTick(); assertEquals("player reply",p.nextMessage()); assertEquals("console reply",server.getConsoleSender().nextMessage()); registry.close();
    }
    @Test void nonPlayerSendersAreNotSilentlyElevatedToConsole() {
        var registry=new PlayerRegistry(); assertThrows(IllegalArgumentException.class,()->SessionAudience.capture(mock(BlockCommandSender.class),registry)); registry.close();
    }
    @Test void sessionValidatorFailureReleasesNativeRepeatingTask() {
        var p=server.addPlayer(); var task=scheduler.entity(p,1,1,()->{ throw new IllegalStateException("validator failure"); },()->fail("must not run"),()->{});
        assertThrows(IllegalStateException.class,()->server.getScheduler().performOneTick()); assertTrue(task.done()); assertEquals(0,scheduler.pendingTasks());
    }
}
