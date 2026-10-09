package dev.aegisac.paper.bedrock;
import dev.aegisac.api.player.BedrockStatus;
import dev.aegisac.common.config.*;
import dev.aegisac.common.player.*;
import org.bukkit.Server;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class IdentityServiceTest {
    @TempDir Path directory;
    final AtomicLong now=new AtomicLong(); final PlayerRegistry registry=new PlayerRegistry();
    Server server; PluginManager manager; Plugin plugin; ConfigService config; IdentityService service;
    @BeforeEach void setup() throws Exception {
        config=new ConfigService(new ConfigurationLoader(directory)); config.reload();
        server=mock(Server.class); manager=mock(PluginManager.class); plugin=mock(Plugin.class);
        when(server.isPrimaryThread()).thenReturn(true); when(server.getPluginManager()).thenReturn(manager); when(plugin.isEnabled()).thenReturn(true);
        OfficialIdentityProviderTest.resetFixture();
        service=new IdentityService(server,config::current,message->{},now::get,(name,loader)->OfficialIdentityProvider.floodgate(OfficialIdentityProviderTest.Floodgate.class,OfficialIdentityProviderTest.Metadata.class));
    }
    PlayerData attach(UUID uuid) {
        var data=registry.join(uuid,".NotAnIdentity",0); data.configure(config.current().pipeline()); data.configureChecks(config::current,dev.aegisac.common.check.ServerTickHealth.Snapshot::unknown); service.attach(uuid,data); return data;
    }
    void floodgate() { when(manager.getPlugin("floodgate")).thenReturn(plugin); }
    void policy(String body) throws Exception { Files.writeString(directory.resolve("compatibility.yml"),"config-version: 1\nidentity:\n"+body); config.reload(); }
    @Test void absentProvidersRemainUnknownRegardlessOfNameOrUuid() {
        var data=attach(new UUID(0,1)); service.tick(); assertEquals(BedrockStatus.UNKNOWN,data.editionSnapshot(0).status());
    }
    @Test void budgetRotatesAcrossSessionsAndProviderQueriesOnlyRunOnOwnerThread() {
        floodgate(); for(int i=0;i<20;i++) attach(UUID.randomUUID());
        service.tick(); assertEquals(8,OfficialIdentityProviderTest.Floodgate.queried.size());
        service.tick(); service.tick(); assertEquals(20,OfficialIdentityProviderTest.Floodgate.queried.size());
        when(server.isPrimaryThread()).thenReturn(false); assertThrows(IllegalStateException.class,service::tick); assertDoesNotThrow(()->service.detach(UUID.randomUUID()));
    }
    @Test void providerDisableImmediatelyInvalidatesAndNegativeCannotErasePositiveSessionHistory() throws Exception {
        floodgate(); policy("  negative-results-authoritative: true\n  geyser: false\n"); var data=attach(UUID.randomUUID()); service.tick(); assertEquals(BedrockStatus.BEDROCK,data.editionSnapshot(0).status());
        service.providersChanged(); assertEquals(BedrockStatus.UNKNOWN,data.editionSnapshot(0).status());
        OfficialIdentityProviderTest.Floodgate.positive=false; service.tick(); assertEquals(BedrockStatus.UNKNOWN,data.editionSnapshot(0).status());
        assertTrue(data.editionSnapshot(0).reasons().contains("BEDROCK_PROVIDER_LOST"));
    }
    @Test void staleFutureAndReloadedObservationsAreUnavailableUntilRefreshed() throws Exception {
        floodgate(); var data=attach(UUID.randomUUID()); now.set(1_000_000_000); service.tick();
        assertTrue(data.editionSnapshot(0).reasons().contains("FUTURE_IDENTITY"));
        assertTrue(data.editionSnapshot(5_000_000_000L).reasons().contains("STALE_IDENTITY"));
        config.reload(); assertTrue(data.editionSnapshot(now.get()).reasons().contains("CONFIGURATION_CHANGED"));
        service.tick(); assertEquals(BedrockStatus.BEDROCK,data.editionSnapshot(now.get()).status());
        now.set(0); service.tick(); assertEquals(BedrockStatus.BEDROCK,data.editionSnapshot(0).status());
    }
    @Test void reconnectGetsNewObservationAndCloseInvalidatesAllSessions() {
        floodgate(); UUID uuid=UUID.randomUUID(); var old=attach(uuid); service.tick(); registry.quit(uuid);
        var next=attach(uuid); assertEquals(BedrockStatus.UNKNOWN,next.editionSnapshot(0).status()); service.tick();
        assertEquals(BedrockStatus.BEDROCK,next.editionSnapshot(0).status()); assertTrue(old.editionSnapshot(0).reasons().contains("CLOSED"));
        service.close(); assertEquals(BedrockStatus.UNKNOWN,next.editionSnapshot(0).status()); assertThrows(IllegalStateException.class,()->service.attach(uuid,next));
    }
    @Test void explicitJavaTopologyCanRunWithoutProvidersButCoverageRequiresAllEnabledProviders() throws Exception {
        policy("  java-only-server: true\n"); var data=attach(UUID.randomUUID()); service.tick(); assertEquals(BedrockStatus.JAVA,data.editionSnapshot(0).status());
        policy("  negative-results-authoritative: true\n"); floodgate(); OfficialIdentityProviderTest.Floodgate.positive=false;
        service.tick(); assertEquals(BedrockStatus.UNKNOWN,data.editionSnapshot(0).status());
        policy("  negative-results-authoritative: true\n  geyser: false\n"); service.tick(); assertEquals(BedrockStatus.JAVA,data.editionSnapshot(0).status());
    }
    @Test void invalidationDuringProviderQueryCannotPublishUnderNewEpoch() {
        var provider=mock(OfficialIdentityProvider.class);
        service.close(); service=new IdentityService(server,config::current,message->{},now::get,(name,loader)->provider);
        floodgate(); var data=attach(UUID.randomUUID());
        when(provider.query(any())).thenAnswer(c->{ service.providersChanged(); return dev.aegisac.common.bedrock.IdentityResult.of("FLOODGATE",dev.aegisac.common.bedrock.IdentityResult.Outcome.ABSENT); });
        service.tick(); assertEquals(BedrockStatus.UNKNOWN,data.editionSnapshot(now.get()).status());
        assertTrue(data.editionSnapshot(now.get()).reasons().contains("PROVIDER_UNAVAILABLE"));
    }
    @Test void entityLifecycleMembershipDoesNotRequireGlobalOwner() {
        when(server.isPrimaryThread()).thenReturn(false);
        UUID uuid=UUID.randomUUID(); var data=attach(uuid); service.providersChanged(); service.detach(uuid);
        when(server.isPrimaryThread()).thenReturn(true); service.tick();
        assertEquals(BedrockStatus.UNKNOWN,data.editionSnapshot(now.get()).status());
        assertTrue(OfficialIdentityProviderTest.Floodgate.queried.isEmpty());
    }
    @AfterEach void cleanup() { when(server.isPrimaryThread()).thenReturn(true); service.close(); registry.close(); }
}
