package dev.aegisac.common.player;
import dev.aegisac.api.player.*;
import dev.aegisac.common.bedrock.IdentityObservation;
import dev.aegisac.common.config.*;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.common.check.ServerTickHealth;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static dev.aegisac.common.packet.PipelineFixtures.PROTOCOL;
import static org.junit.jupiter.api.Assertions.*;
class EditionPipelineTest {
    @TempDir Path directory;
    ConfigService config; PlayerRegistry registry; PlayerData data; long sequence;
    AtomicLong epoch=new AtomicLong(); UUID uuid=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        config=new ConfigService(new ConfigurationLoader(directory)); config.reload(); registry=new PlayerRegistry();
        data=registry.join(uuid,"Alice",0); data.configure(config.current().pipeline());
        data.configureChecks(config::current,ServerTickHealth.Snapshot::unknown); data.configureIdentity(epoch::get);
    }
    void identity(BedrockStatus edition,long time) { data.identityObservation(new IdentityObservation(config.current().generation(),epoch.get(),new EditionSnapshot(edition,"FIXTURE","UNKNOWN","UNKNOWN","1",time,Set.of()))); }
    void move(long time) { data.process(new PacketFrame(++sequence,data.lossEpoch(),time,PacketDirection.INBOUND,PROTOCOL,new Movement(true,true,1,1,0,0,0,true,false)),time); }
    @Test void unknownUsesDisabledJavaChecksButConfirmedJavaUsesJavaProfile() {
        move(0); assertEquals("DISABLED",data.snapshot(0).movementChecks().checks().get("SpeedA").status());
        identity(BedrockStatus.JAVA,1); move(1);
        assertNotEquals("DISABLED",data.snapshot(1).movementChecks().checks().get("SpeedA").status());
        assertEquals(BedrockStatus.JAVA,data.snapshot(1).client().bedrock());
    }
    @Test void bedrockNeverRunsJavaPhysicsAndRefreshDoesNotEraseTranslatedHistory() {
        identity(BedrockStatus.BEDROCK,0); move(0); identity(BedrockStatus.BEDROCK,1); move(1);
        var snapshot=data.snapshot(1); assertEquals(2,snapshot.bedrockAnalysis().histories().get("movement").size());
        assertEquals(0,snapshot.physics().candidates()); assertTrue(snapshot.physics().uncertainty().contains("BEDROCK_PHYSICS_UNAVAILABLE"));
        assertEquals("DISABLED",snapshot.movementChecks().checks().get("SpeedA").status());
    }
    @Test void identityTransitionsClearStateAndSnapshotsHideObsoleteAnalysisBeforeNextPacket() {
        identity(BedrockStatus.BEDROCK,0); move(0); long revision=data.world().read().revision();
        identity(BedrockStatus.JAVA,1); assertTrue(data.snapshot(1).bedrockAnalysis().histories().isEmpty());
        assertTrue(data.snapshot(1).guard().checks().isEmpty()); move(1);
        assertTrue(data.world().read().revision()>revision); assertTrue(data.snapshot(1).bedrockAnalysis().histories().isEmpty());
        identity(BedrockStatus.BEDROCK,2); move(2); assertEquals(1,data.snapshot(2).bedrockAnalysis().histories().get("movement").size());
    }
    @Test void providerEpochExpiryPacketLossAndReloadImmediatelyInvalidateViews() throws Exception {
        identity(BedrockStatus.BEDROCK,0); move(0); epoch.incrementAndGet();
        assertEquals(BedrockStatus.UNKNOWN,data.snapshot(0).edition().status()); assertTrue(data.snapshot(0).bedrockAnalysis().histories().isEmpty());
        identity(BedrockStatus.BEDROCK,1); move(1); assertTrue(data.snapshot(4_000_000_000L).bedrockAnalysis().histories().isEmpty());
        data.markGap(); assertTrue(data.snapshot(1).bedrockAnalysis().histories().isEmpty()); move(2);
        config.reload(); assertEquals(BedrockStatus.UNKNOWN,data.snapshot(2).edition().status()); assertTrue(data.snapshot(2).bedrockAnalysis().histories().isEmpty());
    }
    @Test void closedSessionRejectsLateIdentityPublicationAndReconnectStartsUnknown() {
        identity(BedrockStatus.BEDROCK,0); move(0); registry.quit(uuid); identity(BedrockStatus.BEDROCK,1);
        assertTrue(data.snapshot(1).edition().reasons().contains("CLOSED"));
        var next=registry.join(uuid,"Alice",1); assertEquals(BedrockStatus.UNKNOWN,next.snapshot(1).client().bedrock());
    }
    @Test void actualGuardEvidenceIsDeliveredOnceAndReloadRewiresNewMonitors() throws Exception {
        var delivered=new java.util.ArrayList<dev.aegisac.common.output.DetectionEnvelope>(); data.configureOutput(delivered::add);
        move(0);
        data.process(new PacketFrame(++sequence,0,3_000_000_000L,PacketDirection.INBOUND,PROTOCOL,new HeldItem(9)),3_000_000_000L);
        assertEquals(1,delivered.size()); assertEquals("InvalidSlotA",delivered.getFirst().detection().check());
        assertTrue(delivered.getFirst().detection().diagnostic()); assertFalse(delivered.getFirst().detection().scoreEligible());
        config.reload(); move(4_000_000_000L);
        data.process(new PacketFrame(++sequence,0,7_000_000_000L,PacketDirection.INBOUND,PROTOCOL,new HeldItem(9)),7_000_000_000L);
        assertEquals(2,delivered.size()); assertEquals(config.current().generation(),delivered.getLast().detection().generation());
    }
    @Test void recoveredProviderWithSameLabelsStillInvalidatesPreviousAnalysis() {
        identity(BedrockStatus.BEDROCK,0); move(0); epoch.incrementAndGet(); identity(BedrockStatus.BEDROCK,1);
        assertTrue(data.snapshot(1).bedrockAnalysis().histories().isEmpty()); move(1);
        assertEquals(1,data.snapshot(1).bedrockAnalysis().histories().get("movement").size());
    }
    @Test void lossDuringAnEmissionDoesNotStampOldAnalysisWithNewLossEpoch() {
        var delivered=new java.util.ArrayList<dev.aegisac.common.output.DetectionEnvelope>();
        data.configureOutput(e->{ delivered.add(e); if(delivered.size()==1) data.markGap(); }); move(0);
        data.process(new PacketFrame(++sequence,0,3_000_000_000L,PacketDirection.INBOUND,PROTOCOL,new Movement(true,true,30_000_001,1,0,0,91,false,false)),3_000_000_000L);
        assertTrue(delivered.size()>=2); assertEquals(1,data.lossEpoch());
        for(var e:delivered) {
            assertEquals(0,e.lossEpoch());
            assertFalse(data.currentOutput(e.session(),e.lossEpoch(),e.providerEpoch(),e.detection().generation(),e.identityKey(),3_000_000_000L));
        }
    }
    @AfterEach void cleanup() { registry.close(); }
}
