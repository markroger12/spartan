package dev.aegisac.paper.world;
import dev.aegisac.common.player.*;
import dev.aegisac.common.config.PhysicsSettings;
import dev.aegisac.paper.scheduler.PlatformScheduler;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ScheduledCaptureTest {
    record Callback(BooleanSupplier current,Runnable action,Runnable retired) {
        void run() { if(current.getAsBoolean()) action.run(); else retired.run(); }
    }
    Server server; PlatformScheduler scheduler; WorldCaptureService captures; PlayerRegistry registry;
    List<Callback> callbacks;
    @BeforeEach void setup() {
        server=mock(Server.class); scheduler=mock(PlatformScheduler.class); callbacks=new ArrayList<>(); registry=new PlayerRegistry();
        when(scheduler.mode()).thenReturn(PlatformScheduler.Mode.FOLIA); when(scheduler.globalThread()).thenReturn(true);
        when(scheduler.owns(any(Player.class))).thenReturn(true);
        when(scheduler.entity(any(),eq(1L),eq(0L),any(),any(),any())).thenAnswer(c->{ callbacks.add(new Callback(c.getArgument(3),c.getArgument(4),c.getArgument(5))); return mock(PlatformScheduler.Task.class); });
        captures=new WorldCaptureService(server,new PhysicsSettings(false,2,2,512,512,16,250),e->fail(e),scheduler);
    }
    @AfterEach void cleanup() { captures.close(); registry.close(); }
    Player player(UUID uuid) {
        var player=mock(Player.class); var world=mock(World.class); when(world.getUID()).thenReturn(new UUID(0,1)); when(world.getName()).thenReturn("world");
        when(player.getUniqueId()).thenReturn(uuid); when(player.getWorld()).thenReturn(world); when(player.isOnline()).thenReturn(true); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        return player;
    }
    @Test void globalCoordinatorDoesNotReadPlayersAndAllowsAtMostOnePendingCapturePerSession() {
        var players=new ArrayList<Player>();
        for(int i=0;i<3;i++) { var id=UUID.randomUUID(); var p=player(id); players.add(p); captures.attach(p,registry.join(id,"P"+i,0)); clearInvocations(p); }
        captures.tick(); assertEquals(2,callbacks.size()); captures.tick(); captures.tick(); assertEquals(3,callbacks.size());
        players.forEach(p->verifyNoInteractions(p)); verify(server,never()).getPlayer(any(UUID.class));
        callbacks.forEach(Callback::run); players.forEach(p->verify(p,atLeastOnce()).getGameMode());
        assertFalse(captures.health().known(),"global cadence is not a Folia entity-region TPS sample");
    }
    @Test void reconnectRetiresOldCaptureWithoutReadingOldEntity() {
        var id=UUID.randomUUID(); var old=player(id); captures.attach(old,registry.join(id,"Alice",0)); captures.tick();
        captures.detach(id); var fresh=player(id); captures.attach(fresh,registry.join(id,"Alice",1)); clearInvocations(old,fresh);
        callbacks.getFirst().run(); verifyNoInteractions(old); captures.tick(); assertEquals(2,callbacks.size()); callbacks.getLast().run(); verify(fresh,atLeastOnce()).getGameMode();
    }
    @Test void shutdownRejectsNewEntriesAndQueuedCallbacksCannotReadOrPublish() {
        var id=UUID.randomUUID(); var p=player(id); var data=registry.join(id,"Alice",0); captures.attach(p,data); captures.tick(); clearInvocations(p);
        long revision=data.world().read().revision(); captures.close(); callbacks.getFirst().run(); verifyNoInteractions(p);
        assertTrue(data.world().read().revision()>revision); assertThrows(IllegalStateException.class,()->captures.attach(p,data));
    }
    @Test void rejectedTaskAdmissionInvalidatesSnapshotWithoutLeavingPendingCapture() {
        var id=UUID.randomUUID(); var p=player(id); var data=registry.join(id,"Alice",0); captures.attach(p,data);
        when(scheduler.entity(any(),anyLong(),anyLong(),any(),any(),any())).thenThrow(new java.util.concurrent.RejectedExecutionException("full"));
        captures.tick(); captures.tick(); assertEquals(2,captures.failed()); assertEquals(2,data.world().read().revision());
    }
}
