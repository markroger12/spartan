package dev.aegisac.common.player;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static dev.aegisac.common.packet.PacketDirection.*;
import static dev.aegisac.common.packet.PipelineFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PacketStateTest {
    private final PlayerRegistry registry = new PlayerRegistry();
    private final PlayerData data = registry.join(UUID.randomUUID(), "Alice", 0);
    private long sequence;
    PacketStateTest() { data.configure(settings()); data.bindEntity(42); }
    private void accept(PacketDirection direction, NormalizedPacket packet, long millis) {
        data.process(new PacketFrame(++sequence,data.lossEpoch(),millis*1_000_000,direction,PROTOCOL,packet),millis*1_000_000);
    }
    @Test void positionAndRotationOnlyPacketsPreserveAbsentFields() {
        accept(INBOUND,new Movement(true,true,1,64,2,90,20,true,false),10);
        accept(INBOUND,new Movement(false,true,0,0,0,100,30,false,true),20);
        var state=data.snapshot(20_000_000).movement();
        assertTrue(state.positionKnown()); assertTrue(state.rotationKnown());
        assertEquals(1,state.x());assertEquals(64,state.y());assertEquals(100,state.yaw());assertTrue(state.horizontalCollision());
        accept(INBOUND,new Movement(true,false,3,65,4,0,0,false,false),30);
        assertEquals(100,data.snapshot().movement().yaw());assertEquals(3,data.snapshot().movement().x());
    }
    @Test void malformedNumbersNeverPoisonPositionAndOnlyMarkUncertainty() {
        accept(INBOUND,new Movement(true,true,1,64,2,90,20,true,false),10);
        accept(INBOUND,new Movement(true,false,Double.NaN,64,2,0,0,true,false),20);
        var state=data.snapshot(20_000_000);
        assertFalse(state.movement().positionKnown());assertEquals(1,state.activity().malformedObservations());
        assertTrue(state.connection().uncertain());
        accept(INBOUND,new HeldItem(10),30);assertEquals(-1,data.snapshot().activity().heldSlot());
    }
    @Test void serverInventoryAndFlightAuthorityStaySeparateFromClientClaims() {
        accept(OUTBOUND,new Inventory(InventoryAction.OPEN,5,-1,-1,-1,""),1);
        accept(INBOUND,new Inventory(InventoryAction.CLICK,9,99,4,0,"PICKUP"),2);
        accept(INBOUND,new Abilities(true,false,true,true,1,1),3);
        assertEquals(5,data.snapshot().activity().openWindow());assertEquals(-1,data.snapshot().activity().inventoryStateId());
        accept(OUTBOUND,new Inventory(InventoryAction.SLOT,-1,2,-1,-1,""),2);
        assertEquals(5,data.snapshot().activity().openWindow());
        assertTrue(data.snapshot().activity().clientFlying());assertFalse(data.snapshot().activity().serverAllowsFlight());
        accept(OUTBOUND,new Abilities(false,true,true,false,0.05f,0.1f),4);
        assertTrue(data.snapshot().activity().serverAllowsFlight());
        accept(OUTBOUND,new Inventory(InventoryAction.CLOSE,5,-1,-1,-1,""),5);
        assertEquals(-1,data.snapshot().activity().openWindow());
    }
    @Test void teleportConfirmationRequiresMatchingTokenAndPreservesRelativeFlags() {
        accept(INBOUND,new Movement(true,true,1,64,2,90,20,true,false),1);
        accept(OUTBOUND,new Teleport(4,5,0,5,0,0,0x101,1,2,3),2);
        assertFalse(data.snapshot().movement().positionKnown());
        assertEquals(0x101,((Teleport)data.recentPackets().getLast().packet()).relativeFlags());
        accept(INBOUND,new Timing(TimingKind.TELEPORT,99,0,true),3);
        assertEquals(-1,data.snapshot().activity().lastConfirmedTeleportId());
        accept(INBOUND,new Timing(TimingKind.TELEPORT,4,0,true),4);
        assertEquals(4,data.snapshot().activity().lastConfirmedTeleportId());
        assertEquals(-1,data.snapshot().connection().timing().transactionRttMillis());
    }
    @Test void onlyOwnVelocityAffectsLocalStateAndHistoryIsBounded() {
        accept(OUTBOUND,new Impulse(false,99,1,1,1),1);
        assertEquals(0,data.snapshot().activity().lastVelocityNanos());
        accept(OUTBOUND,new Impulse(false,42,1,1,1),2);
        assertEquals(2_000_000,data.snapshot().activity().lastVelocityNanos());
        for(int i=3;i<20;i++) accept(INBOUND,Other.INSTANCE,i);
        assertEquals(settings().historyCapacity(),data.recentPackets().size());
        assertThrows(UnsupportedOperationException.class,()->data.recentPackets().clear());
    }
    @Test void gapsClearPendingTimingAndStaleStateBeforeResuming() {
        accept(INBOUND,new Movement(true,true,1,64,2,90,20,true,false),1);
        accept(OUTBOUND,new Timing(TimingKind.PING,7,0,true),2);
        data.markGap();
        assertTrue(data.snapshot(3_000_000).connection().uncertain());
        accept(INBOUND,new Timing(TimingKind.PING,7,0,true),3);
        var snapshot=data.snapshot(3_000_000);
        assertFalse(snapshot.movement().positionKnown());assertEquals(0,snapshot.connection().timing().samples());
        assertEquals(snapshot.connection().lossEpoch(),snapshot.connection().processedEpoch());
        assertEquals(1,data.recentPackets().size());
    }
    @Test void worldTransitionsResetStateAndClosedSessionsIgnoreQueuedFrames() {
        accept(INBOUND,new HeldItem(4),1);
        accept(INBOUND,new EntityAction(42,"START_SPRINTING",0),2);
        accept(OUTBOUND,new Action(PacketKind.WORLD_RESET,"RESPAWN"),3);
        assertEquals(-1,data.snapshot().activity().heldSlot());assertFalse(data.snapshot().activity().sprinting());
        assertEquals(1,data.recentPackets().size());
        registry.close();accept(INBOUND,new HeldItem(6),4);
        assertEquals(-1,data.snapshot().activity().heldSlot());assertEquals(0,data.recentPackets().size());
    }
    @Test void snapshotReadsCannotExpireAnAlreadyArrivedReplyInTheWorkerQueue() {
        accept(OUTBOUND,new Timing(TimingKind.PING,1,0,true),1);
        data.snapshot(2_000_000_000L);
        data.process(new PacketFrame(++sequence,0,51_000_000,INBOUND,PROTOCOL,new Timing(TimingKind.PING,1,0,true)),2_000_000_000L);
        assertEquals(50,data.snapshot(2_000_000_000L).connection().timing().transactionRttMillis());
    }
}
