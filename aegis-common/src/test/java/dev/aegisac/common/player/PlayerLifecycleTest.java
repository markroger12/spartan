package dev.aegisac.common.player;
import dev.aegisac.api.player.BedrockStatus;
import dev.aegisac.common.packet.PacketDirection;
import dev.aegisac.common.packet.PacketMetrics;
import dev.aegisac.common.packet.*;
import static dev.aegisac.common.packet.PipelineFixtures.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PlayerLifecycleTest {
    @Test void quitClosesReferencesAndPacketsCannotResurrectState() {
        PlayerRegistry registry = new PlayerRegistry();
        UUID uuid = UUID.randomUUID();
        PlayerData data = registry.join(uuid, "Alice", 10);
        assertTrue(data.observe(PacketDirection.INBOUND, 20, 1, "test-client"));
        assertEquals(BedrockStatus.UNKNOWN, data.snapshot().client().bedrock());
        registry.quit(uuid);
        assertFalse(data.observe(PacketDirection.INBOUND, 30, 1, "test-client"));
        assertEquals(1, data.snapshot().inboundPackets());
        assertTrue(registry.snapshot(uuid).isEmpty());
        assertEquals(0, registry.size());
    }
    @Test void reconnectReplacesAndClosesTheOldSession() {
        PlayerRegistry registry = new PlayerRegistry();
        UUID uuid = UUID.randomUUID();
        PlayerData old = registry.join(uuid, "Alice", 10);
        PlayerData fresh = registry.join(uuid, "Alice", 20);
        assertNotEquals(old.snapshot().sessionId(), fresh.snapshot().sessionId());
        assertFalse(old.observe(PacketDirection.OUTBOUND, 30, 1, "test"));
        assertEquals(0, fresh.snapshot().outboundPackets());
        assertEquals(1, registry.size());
        registry.close();
        assertEquals(0, registry.size());
        assertThrows(IllegalStateException.class, () -> registry.join(uuid, "Alice", 40));
    }
    @Test void concurrentCountersRemainCoherent() throws Exception {
        PlayerRegistry registry = new PlayerRegistry();
        PlayerData data = registry.join(UUID.randomUUID(), "Alice", 1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 4; worker++) tasks.add(executor.submit(() -> {
                for (int i = 0; i < 5000; i++) data.observe(PacketDirection.INBOUND, i, 5, "client");
            }));
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertEquals(20_000, data.snapshot().inboundPackets());
        assertEquals(0, data.snapshot().outboundPackets());
    }
    @Test void oldTransportCannotFeedReconnectedPlayerAndDetachStopsObservation() {
        PlayerRegistry registry = new PlayerRegistry();
        PacketMetrics metrics = new PacketMetrics();
        var executor = new ManualExecutor();
        PacketRouter<Object> router = new PacketRouter<>(metrics, () -> true, settings(), executor, () -> 10, new PacketProcessor(() -> 10), problem -> fail(problem));
        UUID uuid = UUID.randomUUID();
        Object oldConnection = new Object(), newConnection = new Object();
        router.attach(uuid, oldConnection, registry.join(uuid, "Alice", 1));
        router.observe(uuid, oldConnection, PacketDirection.INBOUND, 2, PROTOCOL, NormalizedPacket.Other.INSTANCE);
        PlayerData fresh = registry.join(uuid, "Alice", 3);
        router.attach(uuid, newConnection, fresh);
        router.observe(uuid, oldConnection, PacketDirection.INBOUND, 4, PROTOCOL, NormalizedPacket.Other.INSTANCE);
        router.observe(uuid, newConnection, PacketDirection.OUTBOUND, 5, PROTOCOL, NormalizedPacket.Other.INSTANCE);
        executor.drain();
        assertEquals(0, fresh.snapshot().inboundPackets());
        assertEquals(1, fresh.snapshot().outboundPackets());
        router.detach(uuid);
        router.observe(uuid, newConnection, PacketDirection.OUTBOUND, 6, PROTOCOL, NormalizedPacket.Other.INSTANCE);
        assertEquals(new PacketMetrics.Snapshot(1, 1, 2), metrics.snapshot());
        assertEquals(1, fresh.snapshot().outboundPackets());
    }
    @Test void disabledMetricsDoNotDisablePlayerTracking() {
        var registry = new PlayerRegistry();
        var metrics = new PacketMetrics();
        var executor = new ManualExecutor();
        var router = new PacketRouter<Object>(metrics, () -> false, settings(), executor, () -> 10, new PacketProcessor(() -> 10), problem -> fail(problem));
        UUID uuid = UUID.randomUUID(); Object connection = new Object();
        var data = registry.join(uuid, "Alice", 1);
        router.attach(uuid, connection, data);
        router.observe(uuid, connection, PacketDirection.INBOUND, 2, PROTOCOL, NormalizedPacket.Other.INSTANCE);
        executor.drain();
        assertEquals(1, data.snapshot().inboundPackets());
        assertEquals(new PacketMetrics.Snapshot(0, 0, 0), metrics.snapshot());
        router.clear();
        router.observe(null, connection, PacketDirection.INBOUND, 3, PROTOCOL, NormalizedPacket.Other.INSTANCE);
        assertEquals(1, data.snapshot().inboundPackets());
    }
    @Test void combatSnapshotHidesPendingLossAndReloadThenClosesOnQuit() {
        var registry=new PlayerRegistry(); var id=UUID.randomUUID(); var data=registry.join(id,"Alice",0);
        data.configure(settings());
        var configuration=new java.util.concurrent.atomic.AtomicReference<>(dev.aegisac.common.check.CheckFixtures.config(1,dev.aegisac.common.check.CheckFixtures.noGrace()));
        data.configureChecks(configuration::get,dev.aegisac.common.check.ServerTickHealth.Snapshot::unknown);
        data.process(new PacketFrame(1,0,0,PacketDirection.OUTBOUND,PROTOCOL,new NormalizedPacket.EntitySpawn(10,null,true,0,0,3)),0);
        assertEquals(1,data.snapshot(0).combat().trackedTargets());
        data.markGap(); assertEquals(0,data.snapshot(1).combat().trackedTargets());
        data.process(new PacketFrame(2,1,2,PacketDirection.INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE),2);
        assertEquals(0,data.snapshot(2).combat().trackedTargets());
        data.process(new PacketFrame(3,1,3,PacketDirection.OUTBOUND,PROTOCOL,new NormalizedPacket.EntitySpawn(10,null,true,0,0,3)),3);
        configuration.set(dev.aegisac.common.check.CheckFixtures.config(2,dev.aegisac.common.check.CheckFixtures.noGrace()));
        assertEquals(0,data.snapshot(4).combat().trackedTargets());
        data.process(new PacketFrame(4,1,4,PacketDirection.INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE),4);
        assertEquals(2,data.snapshot(4).combat().generation());
        registry.quit(id); assertTrue(data.snapshot(5).combat().checks().isEmpty());
    }

    @Test void phaseSixObservesMalformedMovementBeforeStateAndHidesLossAndReload() {
        var registry=new PlayerRegistry(); var id=UUID.randomUUID(); var data=registry.join(id,"Alice",0); data.configure(settings());
        var config=new java.util.concurrent.atomic.AtomicReference<>(dev.aegisac.common.check.CheckFixtures.config(1,dev.aegisac.common.check.CheckFixtures.noGrace()));
        data.configureChecks(config::get,dev.aegisac.common.check.ServerTickHealth.Snapshot::unknown);
        data.process(new PacketFrame(1,0,0,PacketDirection.INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE),0);
        data.process(new PacketFrame(2,0,3_000_000_000L,PacketDirection.INBOUND,PROTOCOL,new NormalizedPacket.Movement(true,false,Double.NaN,0,0,0,0,false,false)),3_000_000_000L);
        assertEquals(1,data.snapshot(3_000_000_000L).guard().checks().get("BadPacketsA").diagnostics());
        assertFalse(data.snapshot(3_000_000_000L).movement().positionKnown());
        data.markGap(); assertTrue(data.snapshot(3_000_000_001L).guard().checks().isEmpty());
        data.process(new PacketFrame(3,1,3_000_000_002L,PacketDirection.INBOUND,PROTOCOL,NormalizedPacket.Other.INSTANCE),3_000_000_002L);
        assertTrue(data.snapshot(3_000_000_002L).guard().evidence().isEmpty());
        config.set(dev.aegisac.common.check.CheckFixtures.config(2,dev.aegisac.common.check.CheckFixtures.noGrace()));
        assertTrue(data.snapshot(3_000_000_003L).guard().checks().isEmpty());
        registry.quit(id); assertTrue(data.snapshot(3_000_000_004L).guard().checks().isEmpty());
    }

    @Test void timedExemptionsExpireAndNeverSurviveReconnect() {
        var registry=new PlayerRegistry(); var uuid=UUID.randomUUID(); var data=registry.join(uuid,"Alice",0);
        data.exempt("speeda",100,1000);
        assertEquals(1,data.lossEpoch()); assertTrue(data.temporarilyExempt("SpeedA",1099));
        assertFalse(data.temporarilyExempt("FlyA",100)); assertFalse(data.temporarilyExempt("SpeedA",1100));
        data.exempt("*",100,2000); assertTrue(data.temporarilyExempt("FlyA",200));
        assertThrows(IllegalArgumentException.class,()->data.exempt("bogus",100,1000));
        assertThrows(IllegalArgumentException.class,()->data.exempt("SpeedA",100,3_600_000_000_001L));
        var fresh=registry.join(uuid,"Alice",200); assertFalse(fresh.temporarilyExempt("SpeedA",200));
        assertFalse(data.temporarilyExempt("SpeedA",200)); registry.close();
    }
    @Test void freezeAndTimedExemptionsHaveIndependentLifetimes() {
        var data=new PlayerRegistry().join(UUID.randomUUID(),"Alice",0);
        data.exempt("SpeedA",0,2000); data.frozenUntil(1000,true);
        assertTrue(data.temporarilyExempt("FlyA",999)); assertFalse(data.temporarilyExempt("FlyA",1000));
        data.frozenUntil(0,false); assertTrue(data.temporarilyExempt("SpeedA",1000));
    }

}
