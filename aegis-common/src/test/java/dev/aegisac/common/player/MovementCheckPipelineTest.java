package dev.aegisac.common.player;
import dev.aegisac.common.check.*;
import dev.aegisac.common.config.*;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.common.physics.*;
import dev.aegisac.common.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static dev.aegisac.common.check.CheckFixtures.*;
import static dev.aegisac.common.packet.PipelineFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
class MovementCheckPipelineTest {
    private final UUID uuid=UUID.randomUUID();
    private final PlayerRegistry registry=new PlayerRegistry();
    private final PlayerData data=registry.join(uuid,"Alice",0);
    private final AtomicReference<ConfigSnapshot> config=new AtomicReference<>(config(1,noGrace()));
    private long sequence,now;
    private final ClientProtocol protocol=new ClientProtocol(774,"1.21.11",true,true,true,true);
    MovementCheckPipelineTest() {
        data.configure(settings()); data.configureChecks(config::get,()->new ServerTickHealth.Snapshot(now,50_000_000,20,true));
    }
    private void capture() {
        data.identityObservation(new dev.aegisac.common.bedrock.IdentityObservation(config.get().generation(),0,new dev.aegisac.api.player.EditionSnapshot(dev.aegisac.api.player.BedrockStatus.JAVA,"FIXTURE","UNKNOWN","UNKNOWN","unknown",now,Set.of())));
        var view=data.world().read(); var world=world(view.revision());
        assertTrue(data.world().publish(view,new WorldSnapshot(WORLD,view.revision(),now,world.coverage(),world.blocks(),world.entities(),MotionContext.NORMAL,Set.of())));
        data.ownerObservation(new OwnerObservation(now,"world",false,false,false,false,Set.of()));
    }
    private void move(double x,double y,double z) {
        data.process(new PacketFrame(++sequence,data.lossEpoch(),now,PacketDirection.INBOUND,protocol,
                new Movement(true,true,x,y,z,0,0,true,false)),now);
    }
    @Test void runtimeMismatchCreatesBoundedDiagnosticsButNeverPretendsWorldIsAcknowledged() {
        for(int tick=0;tick<20;tick++) { now=tick*50_000_000L; capture(); move(tick%2,1,0); }
        var snapshot=data.snapshot(now).movementChecks();
        assertTrue(snapshot.checks().get("SpeedA").diagnostics()>0); assertEquals(0,snapshot.checks().get("SpeedA").findings());
        assertTrue(snapshot.evidence().size()<=4);
        assertTrue(snapshot.evidence().stream().allMatch(e->e.diagnostic() && e.reasons().contains("UNACKNOWLEDGED_WORLD")));
    }
    @Test void legitimateSyntheticJumpReplayProducesNoTrustedFlagsOrVerifiedClientSafePositions() {
        var engine=new PhysicsEngine(); var world=world(0);
        var state=new PhysicsEngine.State(new dev.aegisac.common.collision.Vec3(0,1,0),dev.aegisac.common.collision.Vec3.ZERO,true,1.8);
        for(int tick=0;tick<40;tick++) {
            now=tick*50_000_000L; capture(); move(state.feet().x(),state.feet().y(),state.feet().z());
            var step=engine.step(PhysicsProfile.JAVA_1_21_11,state,new PhysicsEngine.Input(0,0,0,false,false,tick==3),world);
            state=step.state();
        }
        var checks=data.snapshot(now).movementChecks();
        assertTrue(checks.checks().values().stream().allMatch(c->c.findings()==0 && c.buffer()==0));
        if(checks.safePosition()!=null) assertFalse(checks.safePosition().verified());
    }
    @Test void lossReloadAndDisconnectHideObsoleteStateBeforeNextMovement() {
        for(int tick=0;tick<10;tick++) { now=tick*50_000_000L; capture(); move(tick%2,1,0); }
        assertFalse(data.snapshot(now).movementChecks().evidence().isEmpty());
        data.markGap(); assertTrue(data.snapshot(now).movementChecks().evidence().isEmpty());
        now+=50_000_000; capture(); move(0,1,0);
        config.set(config(2,noGrace()));
        assertTrue(data.snapshot(now).movementChecks().checks().values().stream().allMatch(c->c.reasons().contains("CONFIGURATION_CHANGED")));
        now+=50_000_000; capture(); move(.1,1,0); assertEquals(2,data.snapshot(now).movementChecks().generation());
        registry.quit(uuid); assertTrue(data.snapshot(now).movementChecks().evidence().isEmpty()); assertNull(data.snapshot(now).movementChecks().safePosition());
        var next=registry.join(uuid,"Alice",now); assertTrue(next.snapshot(now).movementChecks().checks().isEmpty());
    }
    @Test void rotationOnlyPacketsContinueTimingButCannotReuseAnOldMovementFrame() {
        for(int tick=0;tick<4;tick++) { now=tick*50_000_000L; capture(); move(tick%2,1,0); }
        now+=50_000_000;
        data.process(new PacketFrame(++sequence,0,now,PacketDirection.INBOUND,protocol,new Movement(false,true,0,0,0,5,0,true,false)),now);
        assertEquals("SUPPRESSED",data.snapshot(now).movementChecks().checks().get("SpeedA").status());
        assertNull(data.snapshot(now).movementChecks().safePosition());
    }
}
