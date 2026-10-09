package dev.aegisac.paper.admin;
import dev.aegisac.common.player.*;
import dev.aegisac.paper.AegisPlugin;
import dev.aegisac.paper.player.PlayerDirectory;
import dev.aegisac.paper.scheduler.OwnershipHarness;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ScheduledAdminTest {
    final PlayerRegistry registry=new PlayerRegistry(); final OwnershipHarness scheduler=new OwnershipHarness();
    final PlayerDirectory directory=new PlayerDirectory(registry,scheduler);
    final AegisPlugin plugin=mock(AegisPlugin.class); AdminService admin; Player staff,target;
    @BeforeEach void setup() {
        when(plugin.players()).thenReturn(registry); when(plugin.directory()).thenReturn(directory); when(plugin.scheduler()).thenReturn(scheduler);
        doAnswer(c->{ if(c.getArgument(0) instanceof Player p) scheduler.requireEntity(p); return null; }).when(plugin).reply(any(),anyString(),anyMap());
        admin=new AdminService(plugin); staff=player("Staff"); target=player("Alice"); when(staff.hasPermission("aegisac.admin")).thenReturn(true);
    }
    Player player(String name) {
        var p=mock(Player.class); var id=UUID.randomUUID(); var world=mock(World.class);
        when(p.getUniqueId()).thenReturn(id); when(p.getName()).thenReturn(name); when(p.getWorld()).thenReturn(world); when(p.getActivePotionEffects()).thenReturn(List.of()); when(world.getName()).thenReturn("world");
        var data=registry.join(id,name,0); scheduler.at(p,()->directory.attach(p,data)); return p;
    }
    @AfterEach void close() { admin.close(); directory.close(); scheduler.close(); registry.close(); }
    @Test void remoteFreezeAndCompletionExecuteOnDifferentOwners() {
        var handle=directory.find(target.getUniqueId()); var completed=mock(Runnable.class);
        doAnswer(c->{ assertSame(staff,scheduler.owner); return null; }).when(completed).run();
        scheduler.at(staff,()->admin.control(staff,handle,"freeze",()->admin.freeze(target,60_000_000_000L),completed));
        assertFalse(handle.data().temporarilyExempt("SpeedA",System.nanoTime())); verifyNoInteractions(completed);
        scheduler.drain(target); assertTrue(handle.data().temporarilyExempt("SpeedA",System.nanoTime())); verifyNoInteractions(completed);
        scheduler.drain(staff); verify(completed).run();
    }
    @Test void expiredFreezeCannotReleaseAReplacementFreeze() {
        var handle=directory.find(target.getUniqueId()); scheduler.at(target,()->admin.freeze(target,1)); clearInvocations(target);
        admin.tick(); verifyNoInteractions(target);
        scheduler.at(target,()->admin.freeze(target,60_000_000_000L)); scheduler.drain(target);
        assertTrue(handle.data().temporarilyExempt("SpeedA",System.nanoTime()));
    }
    @Test void reconnectRejectsQueuedControlAndCloseClearsStateWithoutPlayerReads() {
        var handle=directory.find(target.getUniqueId()); var action=mock(Runnable.class);
        scheduler.at(staff,()->admin.control(staff,handle,"reset",action,()->{}));
        registry.quit(handle.getUniqueId()); registry.join(handle.getUniqueId(),"Alice",1); scheduler.drain(target); verifyNoInteractions(action);
        scheduler.at(staff,()->admin.freeze(staff,60_000_000_000L)); var data=registry.find(staff.getUniqueId()); clearInvocations(staff,target);
        admin.close(); verifyNoInteractions(staff,target); assertFalse(data.temporarilyExempt("SpeedA",System.nanoTime()));
    }
}
