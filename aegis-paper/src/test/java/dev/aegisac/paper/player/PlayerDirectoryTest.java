package dev.aegisac.paper.player;
import dev.aegisac.common.player.*;
import dev.aegisac.paper.scheduler.OwnershipHarness;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PlayerDirectoryTest {
    final PlayerRegistry registry=new PlayerRegistry(); final OwnershipHarness scheduler=new OwnershipHarness();
    final PlayerDirectory directory=new PlayerDirectory(registry,scheduler);
    Player player(UUID id,String name) {
        var p=mock(Player.class); var world=mock(World.class);
        when(p.getUniqueId()).thenReturn(id); when(p.getName()).thenReturn(name); when(p.getWorld()).thenReturn(world);
        when(p.getActivePotionEffects()).thenReturn(List.of()); when(world.getUID()).thenReturn(new UUID(0,1)); when(world.getName()).thenReturn("world");
        return p;
    }
    PlayerDirectory.Handle attach(Player p) { var data=registry.join(p.getUniqueId(),p.getName(),0); scheduler.at(p,()->directory.attach(p,data)); return directory.find(p.getUniqueId()); }
    @AfterEach void close() { directory.close(); scheduler.close(); registry.close(); }
    @Test void globalReadersUseImmutableObservationsAndLossImmediatelyInvalidatesThem() {
        var p=player(UUID.randomUUID(),"Alice"); var handle=attach(p); clearInvocations(p);
        assertEquals("world",handle.observation().world()); assertEquals("Alice",handle.getName()); assertFalse(handle.health().known()); verifyNoInteractions(p);
        handle.data().markGap(); assertNull(handle.observation()); verifyNoInteractions(p);
        assertThrows(IllegalStateException.class,handle::capture);
        scheduler.at(p,handle::capture); assertNotNull(handle.observation());
    }
    @Test void oldQueuedActionCannotRunOnReplacementSession() {
        UUID id=UUID.randomUUID(); var old=player(id,"Alice"); var first=attach(old); var action=mock(Runnable.class); var retired=mock(Runnable.class);
        first.execute(action,retired); var fresh=player(id,"Alice"); var second=attach(fresh); clearInvocations(old,fresh);
        scheduler.drain(old); verifyNoInteractions(action,old,fresh); verify(retired).run(); assertFalse(first.current()); assertTrue(second.current()); assertNull(first.observation());
    }
    @Test void visibilityChecksBelongToViewerAndNeverInspectTargetLiveState() {
        var target=player(UUID.randomUUID(),"Alice"); var handle=attach(target); var viewer=player(UUID.randomUUID(),"Staff"); attach(viewer);
        when(viewer.canSee(target)).thenReturn(true); clearInvocations(target);
        assertThrows(IllegalStateException.class,()->directory.visible(viewer,handle));
        scheduler.at(viewer,()->assertTrue(directory.visible(viewer,handle))); verifyNoInteractions(target);
    }
    @Test void closeCancelsRecurringTasksAndQueuedActionsRetire() {
        var p=player(UUID.randomUUID(),"Alice"); var handle=attach(p); var action=mock(Runnable.class); var retired=mock(Runnable.class);
        handle.execute(action,retired); directory.close(); clearInvocations(p); scheduler.drain(p);
        verifyNoInteractions(action,p); verify(retired).run(); assertNull(directory.find(handle.getUniqueId()));
        assertEquals(0,scheduler.pendingTasks());
    }
}
