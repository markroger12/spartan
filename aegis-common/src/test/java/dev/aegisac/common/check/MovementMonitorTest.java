package dev.aegisac.common.check;
import dev.aegisac.api.player.*;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static dev.aegisac.common.check.CheckFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
class MovementMonitorTest {
    private final ClientProtocol protocol=new ClientProtocol(774,"1.21.11",true,true,true,true);
    private ConnectionSnapshot connection(boolean uncertain,long delay) {
        return new ConnectionSnapshot(new TimingSnapshot(900,900,0,1,0,0,0,0,0,0,0),1,1,50_000_000,20,0,0,uncertain,0,delay);
    }
    private PacketFrame movement(long sequence,long now) {
        return new PacketFrame(sequence,0,now,PacketDirection.INBOUND,protocol,new Movement(true,true,0,1,0,0,0,true,false));
    }
    @Test void unknownIdentityCanOnlyProduceDiagnosticsDespiteManyMismatches() {
        var monitor=new MovementMonitor(); var config=config(1,noGrace());
        for(int i=1;i<=20;i++) {
            long now=i*50_000_000L;
            monitor.process(config,movement(i,now),frame(i,now,true,Set.of()),connection(false,0),
                    new OwnerObservation(now,"world",false,false,false,false,Set.of()),
                    new ServerTickHealth.Snapshot(now,50_000_000,20,true),BedrockStatus.UNKNOWN,12,42);
        }
        var result=monitor.snapshot(1_000_000_000,0); assertEquals(0,result.checks().get("SpeedA").findings());
        assertEquals(0,result.checks().get("SpeedA").buffer()); assertTrue(result.checks().get("SpeedA").diagnostics()>0);
    }
    @Test void highConstantPingDoesNotUniversallyDisableOtherwiseTrustedGeometry() {
        var monitor=new MovementMonitor(); var config=config(1,noGrace());
        for(int i=1;i<=20;i++) {
            long now=i*50_000_000L;
            monitor.process(config,movement(i,now),frame(i,now,true,Set.of()),connection(false,0),
                    new OwnerObservation(now,"world",false,false,false,false,Set.of()),
                    new ServerTickHealth.Snapshot(now,50_000_000,20,true),BedrockStatus.JAVA,12,42);
        }
        assertTrue(monitor.snapshot(1_000_000_000,0).checks().get("SpeedA").findings()>0);
    }
    @Test void lagAndExplicitWorldExemptionsCannotBuildTrustedFindings() {
        for(boolean worldExempt:new boolean[]{false,true}) {
            var monitor=new MovementMonitor(); var config=config(1,noGrace());
            for(int i=1;i<=20;i++) {
                long now=i*50_000_000L;
                monitor.process(config,movement(i,now),frame(i,now,true,Set.of()),connection(!worldExempt,worldExempt?0:500_000_000),
                        new OwnerObservation(now,worldExempt?"lobby":"world",false,false,false,false,Set.of()),
                        new ServerTickHealth.Snapshot(now,50_000_000,20,true),BedrockStatus.JAVA,12,42);
            }
            var snapshot=monitor.snapshot(1_000_000_000,0); assertEquals(0,snapshot.checks().get("SpeedA").findings());
            if(worldExempt) assertTrue(snapshot.evidence().isEmpty());
        }
    }
    @Test void ownVelocityAndTeleportClearEvidenceButOtherEntityVelocityDoesNot() {
        var monitor=new MovementMonitor(); var config=config(1,noGrace());
        for(int i=1;i<=20;i++) {
            long now=i*50_000_000L;
            monitor.process(config,movement(i,now),frame(i,now,true,Set.of()),connection(false,0),
                    new OwnerObservation(now,"world",false,false,false,false,Set.of()),new ServerTickHealth.Snapshot(now,50_000_000,20,true),BedrockStatus.JAVA,12,42);
        }
        assertFalse(monitor.snapshot(1_000_000_000,0).evidence().isEmpty());
        monitor.process(config,new PacketFrame(21,0,1_050_000_000,PacketDirection.OUTBOUND,protocol,new Impulse(false,99,1,1,0)),null,
                connection(false,0),null,ServerTickHealth.Snapshot.unknown(),BedrockStatus.JAVA,12,42);
        assertFalse(monitor.snapshot(1_050_000_000,0).evidence().isEmpty());
        monitor.process(config,new PacketFrame(22,0,1_100_000_000,PacketDirection.OUTBOUND,protocol,new Impulse(false,42,1,1,0)),null,
                connection(false,0),null,ServerTickHealth.Snapshot.unknown(),BedrockStatus.JAVA,12,42);
        assertTrue(monitor.snapshot(1_100_000_000,0).evidence().isEmpty());
        monitor.process(config,new PacketFrame(23,0,1_150_000_000,PacketDirection.OUTBOUND,protocol,new Teleport(1,0,1,0,0,0,0,0,0,0)),null,
                connection(false,0),null,ServerTickHealth.Snapshot.unknown(),BedrockStatus.JAVA,12,42);
        assertEquals(Set.of("TELEPORT_GRACE"),monitor.snapshot(1_150_000_000,0).checks().get("SpeedA").reasons());
    }
    @Test void tickCadenceIsMeasuredSeparatelyFromPacketFrequency() {
        var clock=new ServerTickHealth(); clock.tick(0); assertFalse(clock.snapshot().known());
        clock.tick(50_000_000); assertEquals(20,clock.snapshot().tps());
        clock.tick(550_000_000); assertEquals(500_000_000,clock.snapshot().intervalNanos()); assertTrue(clock.snapshot().tps()<20);
        clock.tick(1); assertFalse(clock.snapshot().known());
    }
    @Test void manualExemptionSuppressesFindingsWithoutPermissionBypass() {
        var monitor=new MovementMonitor(); monitor.manualBypasses(Set.of("SpeedA")); var config=config(1,noGrace());
        for(int i=1;i<=20;i++) {
            long now=i*50_000_000L;
            monitor.process(config,movement(i,now),frame(i,now,true,Set.of()),connection(false,0),
                    new OwnerObservation(now,"world",false,false,false,false,Set.of()),
                    new ServerTickHealth.Snapshot(now,50_000_000,20,true),BedrockStatus.JAVA,12,42);
        }
        var speed=monitor.snapshot(1_000_000_000,0).checks().get("SpeedA");
        assertEquals(0,speed.findings()); assertEquals(0,speed.buffer());
        assertTrue(speed.reasons().contains("PERMISSION_BYPASS"));
    }

}
