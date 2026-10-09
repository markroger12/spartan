package dev.aegisac.common.player;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.common.physics.*;
import dev.aegisac.common.world.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static dev.aegisac.common.packet.PipelineFixtures.*;
import static dev.aegisac.common.physics.PhysicsFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
class PhysicsPipelineTest {
    private final PlayerRegistry registry=new PlayerRegistry();
    private final UUID uuid=UUID.randomUUID();
    private final PlayerData data=registry.join(uuid,"Alice",0);
    private final ClientProtocol protocol=new ClientProtocol(774,"1.21.11",true,true,true,true);
    private long sequence;
    PhysicsPipelineTest() { data.configure(settings()); }
    private void capture(long time) {
        var view=data.world().read(); var w=world(BlockSample.Surface.SOLID,MotionContext.NORMAL);
        assertTrue(data.world().publish(view,new WorldSnapshot(WORLD,view.revision(),time,w.coverage(),w.blocks(),w.entities(),w.context(),w.uncertainty())));
    }
    private void move(long time,double z) { packet(time,new Movement(true,true,0,1,z,0,0,true,false)); }
    private void packet(long time,NormalizedPacket value) {
        data.process(new PacketFrame(++sequence,data.lossEpoch(),time,PacketDirection.INBOUND,protocol,value),time);
    }
    @Test void packetPipelinePublishesBoundedDiagnosticCandidatesNeverVerifiedState() {
        capture(0); move(50_000_000,0); move(100_000_000,.02);
        var result=data.snapshot(100_000_000).physics(); assertEquals(18,result.candidates());
        assertTrue(result.uncertainty().contains("UNACKNOWLEDGED_WORLD"));
        assertTrue(result.uncertainty().contains("UNKNOWN_EDITION")); assertTrue(result.uncertainty().contains("INPUT_INFERRED"));
        assertTrue(result.residual()>=0); assertThrows(UnsupportedOperationException.class,()->result.uncertainty().clear());
    }
    @Test void lossAndWorldInvalidationHideOldPredictionImmediately() {
        capture(0); move(50_000_000,0); move(100_000_000,.02);
        data.world().invalidate(); assertTrue(data.snapshot(100_000_000).physics().uncertainty().contains("INVALIDATED_WORLD"));
        capture(100_000_000); move(150_000_000,.04); assertEquals(0,data.snapshot(150_000_000).physics().candidates());
        data.markGap(); assertTrue(data.snapshot(150_000_000).physics().uncertainty().contains("PACKET_GAP"));
        move(200_000_000,.06); assertNull(data.world().read().snapshot());
    }
    @Test void futureStaleAndBurstSnapshotsNeverProduceCandidates() {
        capture(150_000_000); move(50_000_000,0); move(100_000_000,.02);
        assertEquals(0,data.snapshot(100_000_000).physics().candidates());
        assertTrue(data.snapshot(100_000_000).physics().uncertainty().contains("FUTURE_WORLD"));
        assertTrue(data.snapshot(500_000_000).physics().uncertainty().contains("STALE_WORLD"));
        capture(500_000_000); move(550_000_000,.04); move(551_000_000,.06);
        assertEquals(0,data.snapshot(551_000_000).physics().candidates());
        assertTrue(data.snapshot(551_000_000).physics().uncertainty().contains("PACKET_GAP"));
    }
    @Test void teleportVelocityRotationOnlyAndDisconnectResetSimulation() {
        capture(0); move(50_000_000,0); move(100_000_000,.02);
        packet(110_000_000,new Impulse(true,-1,.3,.4,0));
        assertTrue(data.snapshot(110_000_000).physics().uncertainty().contains("VELOCITY_TIMING"));
        packet(120_000_000,new Teleport(1,10,1,10,0,0,0,0,0,0));
        assertTrue(data.snapshot(120_000_000).physics().uncertainty().contains("TELEPORT"));
        capture(120_000_000); move(150_000_000,0);
        packet(200_000_000,new Movement(false,true,0,0,0,45,0,true,false));
        assertTrue(data.snapshot(200_000_000).physics().uncertainty().contains("PACKET_GAP"));
        registry.quit(uuid); assertTrue(data.world().read().closed());
        assertNull(data.world().read().snapshot());
        var replacement=registry.join(uuid,"Alice",250_000_000); assertNotSame(data.world(),replacement.world());
    }
    @Test void malformedCoordinatesRemainUncertainWithoutWorkerFailure() {
        capture(0); move(50_000_000,Double.NaN);
        assertTrue(data.snapshot(50_000_000).physics().uncertainty().contains("INVALID_MOVEMENT"));
        move(100_000_000,Double.POSITIVE_INFINITY);
        assertTrue(data.snapshot(100_000_000).physics().uncertainty().contains("INVALID_MOVEMENT"));
    }
}
