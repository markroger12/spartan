package dev.aegisac.paper.output;
import dev.aegisac.common.player.*;
import dev.aegisac.paper.player.PlayerDirectory;
import dev.aegisac.paper.scheduler.OwnershipHarness;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SuppressWarnings("deprecation")
class ScheduledOutputTest {
    @TempDir Path path;
    OutputServiceTest fixture; OwnershipHarness scheduler; PlayerDirectory directory; Player staff; UUID staffId;
    @BeforeEach void setup() throws Exception {
        fixture=new OutputServiceTest(); fixture.directory=path; fixture.setup(); fixture.service.close(); assertTrue(fixture.service.logs().awaitClosed(5000));
        scheduler=new OwnershipHarness(); directory=new PlayerDirectory(fixture.registry,scheduler);
        when(fixture.player.getUniqueId()).thenReturn(fixture.uuid); when(fixture.player.getActivePotionEffects()).thenReturn(List.of()); when(fixture.world.getUID()).thenReturn(new UUID(0,1));
        scheduler.at(fixture.player,()->directory.attach(fixture.player,fixture.data));
        fixture.service=new OutputService(fixture.plugin,fixture.registry,fixture.config::current,fixture.now::get,scheduler,directory);
        scheduler.at(fixture.player,()->fixture.service.attach(fixture.uuid,fixture.data));
        staff=mock(Player.class); staffId=UUID.randomUUID(); when(staff.getUniqueId()).thenReturn(staffId); when(staff.getName()).thenReturn("Staff"); when(staff.getWorld()).thenReturn(fixture.world); when(staff.getActivePotionEffects()).thenReturn(List.of());
        when(staff.spigot()).thenReturn(mock(Player.Spigot.class));
        var data=fixture.registry.join(staffId,"Staff",0);
        scheduler.at(staff,()->{ directory.attach(staff,data); fixture.service.attach(staffId,data); fixture.service.toggle(staffId,true); });
        when(staff.hasPermission(anyString())).thenAnswer(c->{ assertSame(staff,scheduler.owner,"permissions must be checked on recipient owner"); return true; });
        doAnswer(c->{ assertSame(fixture.player,scheduler.owner,"events must run on source owner"); return null; }).when(fixture.manager).callEvent(any());
        clearInvocations(fixture.player,staff);
    }
    @AfterEach void close() throws Exception { directory.close(); scheduler.close(); fixture.cleanup(); }
    void queue() { assertTrue(fixture.service.offer(fixture.event(true,Set.of("OBSERVATION_ONLY")))); fixture.service.tick(); }
    @Test void coordinatorReadsNeitherSourceNorRecipientAndDeliveryUsesDistinctOwner() {
        queue(); verifyNoInteractions(fixture.player,staff,fixture.manager); scheduler.drain(fixture.player);
        assertEquals(1,fixture.service.recent(fixture.uuid).size()); verifyNoInteractions(staff);
        fixture.service.tick(); verifyNoInteractions(staff); scheduler.drain(staff);
        verify(staff).hasPermission("aegisac.verbose"); verify(staff.spigot()).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
        verify(fixture.server,never()).getPlayer(any(UUID.class));
    }
    @Test void recipientReconnectCannotReceiveAlreadyScheduledDelivery() {
        queue(); scheduler.drain(fixture.player); fixture.service.tick();
        fixture.registry.quit(staffId); fixture.registry.join(staffId,"Staff",1); clearInvocations(staff);
        scheduler.drain(staff); verifyNoInteractions(staff); assertEquals(1,fixture.service.metrics().get("stale"));
    }
    @Test void sourceLossBetweenSchedulingAndCallbackRejectsEvidence() {
        queue(); fixture.data.markGap(); scheduler.drain(fixture.player);
        verifyNoInteractions(fixture.player,fixture.manager); assertTrue(fixture.service.recent(fixture.uuid).isEmpty()); assertEquals(1,fixture.service.metrics().get("stale"));
    }
    @Test void permissionRevocationOnRecipientIsHonoredAfterHandoff() {
        queue(); scheduler.drain(fixture.player); fixture.service.tick();
        doReturn(false).when(staff).hasPermission("aegisac.verbose"); scheduler.drain(staff);
        verify(staff.spigot(),never()).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
    }
    @Test void nativePunishmentAuthorizesOnSourceAndDispatchesOnGlobalWithoutForeignReads() {
        doAnswer(c->{ assertTrue(scheduler.globalThread()); return true; }).when(fixture.server).dispatchCommand(any(),anyString());
        assertTrue(fixture.service.offer(fixture.event(false,Set.of()))); fixture.service.tick();
        scheduler.drain(fixture.player); verify(fixture.server,never()).dispatchCommand(any(),anyString()); clearInvocations(fixture.player);
        scheduler.drain(null); verifyNoInteractions(fixture.player); verify(fixture.server).dispatchCommand(any(),eq("fixture Alice SpeedA"));
    }
    @Test void panicOrEvidenceInvalidationAfterOwnerAuthorizationCancelsNativeCommand() {
        assertTrue(fixture.service.offer(fixture.event(false,Set.of()))); fixture.service.tick(); scheduler.drain(fixture.player);
        fixture.service.togglePanic(); scheduler.drain(null); verify(fixture.server,never()).dispatchCommand(any(),anyString());
    }
    @Test void delayedGlobalDispatchCannotUseExpiredOwnerAuthorization() {
        assertTrue(fixture.service.offer(fixture.event(false,Set.of()))); fixture.service.tick(); scheduler.drain(fixture.player);
        fixture.now.addAndGet(100_000_001L); scheduler.drain(null); verify(fixture.server,never()).dispatchCommand(any(),anyString());
    }
    @Test void closeDropsQueuedOwnerWorkWithoutEntityAccess() {
        queue(); fixture.service.close(); scheduler.drain(fixture.player); verifyNoInteractions(fixture.player,staff,fixture.manager);
    }
}
