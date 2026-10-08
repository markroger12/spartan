package dev.aegisac.paper.output;
import dev.aegisac.api.player.*;
import dev.aegisac.common.bedrock.IdentityObservation;
import dev.aegisac.common.config.*;
import dev.aegisac.common.output.*;
import dev.aegisac.common.player.*;
import dev.aegisac.paper.event.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SuppressWarnings("deprecation") // Exercise the cross-platform Spigot component transport.
class OutputServiceTest {
    @TempDir Path directory; ConfigService config; PlayerRegistry registry; PlayerData data; OutputService service;
    JavaPlugin plugin; Server server; PluginManager manager; Player player; World world; UUID uuid=UUID.randomUUID();
    AtomicLong providerEpoch=new AtomicLong(); AtomicLong now=new AtomicLong(1_000_000_000L); long sequence;
    @BeforeEach void setup() throws Exception {
        config=new ConfigService(new ConfigurationLoader(directory)); config.reload();
        Files.writeString(directory.resolve("punishments.yml"),"config-version: 1\nenabled: true\nallow-experimental: true\nchecks:\n  SpeedA:\n    enabled: true\n    minimum-vl: 1.0\n    minimum-total-risk: 0.0\n    minimum-category-risk: 0.0\n    minimum-confidence: 0.0\n    minimum-findings: 1\n    commands:\n      - 'fixture %player% %check%'\n"); config.reload();
        plugin=mock(JavaPlugin.class); server=mock(Server.class); manager=mock(PluginManager.class); player=mock(Player.class); world=mock(World.class);
        when(plugin.getServer()).thenReturn(server); when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getLogger()).thenReturn(Logger.getLogger("output-test"));
        when(server.isPrimaryThread()).thenReturn(true); when(server.getPluginManager()).thenReturn(manager); when(server.getPlayer(uuid)).thenReturn(player);
        when(server.dispatchCommand(any(),anyString())).thenReturn(true); when(player.isOnline()).thenReturn(true); when(player.getName()).thenReturn("Alice"); when(player.getWorld()).thenReturn(world); when(world.getName()).thenReturn("world");
        when(player.spigot()).thenReturn(mock(Player.Spigot.class));
        registry=new PlayerRegistry(); data=registry.join(uuid,"Alice",0); data.configure(config.current().pipeline()); data.configureIdentity(providerEpoch::get); data.configureChecks(config::current,dev.aegisac.common.check.ServerTickHealth.Snapshot::unknown); identity(BedrockStatus.JAVA);
        service=new OutputService(plugin,registry,config::current,now::get); service.attach(uuid,data);
    }
    void identity(BedrockStatus status) { data.identityObservation(new IdentityObservation(config.current().generation(),providerEpoch.get(),new EditionSnapshot(status,"FIXTURE","UNKNOWN","UNKNOWN","1",now.get(),Set.of()))); }
    DetectionEnvelope event(boolean diagnostic,Set<String> reasons) {
        var snapshot=data.snapshot(now.get()); var d=new Detection("SpeedA","movement",++sequence,now.get(),config.current().generation(),2,1,4,diagnostic,true,reasons);
        return new DetectionEnvelope(uuid,"Alice",snapshot.sessionId(),data.lossEpoch(),providerEpoch.get(),snapshot.edition().analysisKey(),d,1000,"1.21.11","fixture",snapshot.edition().status(),50,1,20,"world",true,0,1,0);
    }
    void accept(boolean diagnostic,Set<String> reasons) { assertTrue(service.offer(event(diagnostic,reasons))); service.tick(); }
    @Test void explicitFreshSyntheticFindingDispatchesOnceAndCooldownPreventsRepeats() {
        var event=event(false,Set.of()); service.offer(event); service.offer(event); service.tick();
        verify(server,times(1)).dispatchCommand(any(),eq("fixture Alice SpeedA")); assertEquals(1,service.violations(uuid).findings());
        accept(false,Set.of()); verify(server,times(1)).dispatchCommand(any(),anyString());
    }
    @Test void diagnosticsAndUncertaintyNeverScoreOrPunishEvenWithEveryOptIn() {
        accept(true,Set.of()); accept(false,Set.of("UNACKNOWLEDGED_WORLD"));
        assertEquals(0,service.violations(uuid).totalRisk()); verify(server,never()).dispatchCommand(any(),anyString());
    }
    @Test void panicKeepsScoringButBlocksCommandsAndSurvivesReload() throws Exception {
        assertTrue(service.togglePanic()); accept(false,Set.of()); assertEquals(1,service.violations(uuid).findings());
        config.reload(); identity(BedrockStatus.JAVA); assertTrue(service.panic()); accept(false,Set.of()); verify(server,never()).dispatchCommand(any(),anyString());
    }
    @Test void staleGenerationLossAndReconnectCannotExecuteQueuedEvidence() throws Exception {
        var first=event(false,Set.of()); service.offer(first); data.markGap(); service.tick();
        service.offer(event(false,Set.of())); config.reload(); service.tick();
        identity(BedrockStatus.JAVA); service.offer(event(false,Set.of())); registry.quit(uuid); service.detach(uuid); service.tick();
        verify(server,never()).dispatchCommand(any(),anyString()); assertEquals(3,service.metrics().get("stale"));
    }
    @Test void rapidProviderRecoveryCannotReviveOldQueuedEvidence() {
        service.offer(event(false,Set.of())); providerEpoch.incrementAndGet(); identity(BedrockStatus.JAVA); service.tick();
        verify(server,never()).dispatchCommand(any(),anyString()); assertEquals(1,service.metrics().get("stale"));
    }
    @Test void evidenceAgeAndUnknownOrBedrockEditionCannotPunish() {
        service.offer(event(false,Set.of())); now.addAndGet(600_000_000); service.tick();
        identity(BedrockStatus.UNKNOWN); accept(false,Set.of()); identity(BedrockStatus.BEDROCK); accept(false,Set.of());
        verify(server,never()).dispatchCommand(any(),anyString());
    }
    @Test void currentPermissionsWorldAndGlobalCheckDisableAreRechecked() throws Exception {
        when(player.hasPermission("aegisac.bypass.speeda")).thenReturn(true); accept(false,Set.of());
        when(player.hasPermission("aegisac.bypass.speeda")).thenReturn(false); when(world.getName()).thenReturn("elsewhere"); accept(false,Set.of());
        when(world.getName()).thenReturn("world"); Files.writeString(directory.resolve("checks/movement.yml"),"config-version: 1\nenabled: false\n"); config.reload(); identity(BedrockStatus.JAVA); accept(false,Set.of());
        verify(server,never()).dispatchCommand(any(),anyString());
    }
    @Test void cancellableFlagAndPunishmentEventsAreRespected() {
        doAnswer(call->{ var event=call.getArgument(0); if(event instanceof PlayerFlagEvent flag) flag.setCancelled(true); return null; }).when(manager).callEvent(any());
        accept(false,Set.of()); assertEquals(0,service.violations(uuid).findings());
        doAnswer(call->{ var event=call.getArgument(0); if(event instanceof PlayerPunishEvent punish) punish.setCancelled(true); return null; }).when(manager).callEvent(any());
        accept(false,Set.of()); assertEquals(1,service.violations(uuid).findings()); verify(server,never()).dispatchCommand(any(),anyString());
    }
    @Test void panicActivatedByListenerIsRecheckedImmediatelyBeforeDispatch() {
        doAnswer(call->{ if(call.getArgument(0) instanceof PlayerPunishEvent) service.togglePanic(); return null; }).when(manager).callEvent(any());
        accept(false,Set.of()); verify(server,never()).dispatchCommand(any(),anyString());
    }
    @Test void staffMustSubscribeAndRetainPermissionAndDiagnosticRequiresVerbose() {
        when(player.hasPermission("aegisac.alerts")).thenReturn(true); service.toggle(uuid,false); accept(true,Set.of("OBSERVATION_ONLY"));
        verify(player.spigot(),never()).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
        service.toggle(uuid,true); when(player.hasPermission("aegisac.verbose")).thenReturn(true); now.addAndGet(1_100_000_000); identity(BedrockStatus.JAVA); accept(true,Set.of("OBSERVATION_ONLY"));
        verify(player.spigot(),times(1)).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
        when(player.hasPermission("aegisac.verbose")).thenReturn(false); now.addAndGet(1_100_000_000); identity(BedrockStatus.JAVA); accept(true,Set.of("OBSERVATION_ONLY"));
        verify(player.spigot(),times(1)).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
    }
    @Test void queueIsBoundedAndOwnerThreadIsRequired() {
        var event=event(true,Set.of()); for(int i=0;i<1024;i++) assertTrue(service.offer(event)); assertFalse(service.offer(event));
        service.tick(); assertEquals(960,service.metrics().get("queued"));
        when(server.isPrimaryThread()).thenReturn(false); assertThrows(IllegalStateException.class,service::tick); assertThrows(IllegalStateException.class,service::togglePanic);
    }

    @Test void resetRejectsQueuedEvidenceAndClearsOnlyCurrentHistory() {
        accept(true,Set.of("OBSERVATION_ONLY")); assertEquals(1,service.recent(uuid).size());
        service.offer(event(false,Set.of())); service.reset(uuid); service.tick();
        assertTrue(service.recent(uuid).isEmpty()); assertEquals(0,service.violations(uuid).findings());
        verify(server,never()).dispatchCommand(any(),anyString());
    }
    @Test void exemptionDuringFlagEventPreventsScoreAndPunishment() {
        doAnswer(call->{ if(call.getArgument(0) instanceof PlayerFlagEvent) data.exempt("SpeedA",now.get(),1_000_000_000L); return null; }).when(manager).callEvent(any());
        accept(false,Set.of()); assertEquals(0,service.violations(uuid).findings()); assertTrue(service.recent(uuid).isEmpty());
        verify(server,never()).dispatchCommand(any(),anyString());
    }
    @Test void historyIsBoundedAndImmutable() {
        for(int i=0;i<40;i++) accept(true,Set.of("OBSERVATION_ONLY"));
        var recent=service.recent(uuid); assertEquals(32,recent.size()); assertThrows(UnsupportedOperationException.class,recent::clear);
        service.detach(uuid); assertTrue(service.recent(uuid).isEmpty());
    }
    @AfterEach void cleanup() throws Exception { when(server.isPrimaryThread()).thenReturn(true); service.close(); registry.close(); assertTrue(service.logs().awaitClosed(5000)); }
}
