package dev.aegisac.common.combat;
import dev.aegisac.common.config.CombatSettings;
import dev.aegisac.common.connection.TransactionTracker;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.common.collision.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class TargetHistoryTest {
    final CombatSettings settings=CombatSettings.defaults();
    final TargetHistory history=new TargetHistory(settings);
    long sequence;
    void send(long ms,NormalizedPacket packet) { history.observe(new PacketFrame(++sequence,0,ms*1_000_000,PacketDirection.OUTBOUND,PipelineFixtures.PROTOCOL,packet),null); }
    void ack(long ms,long id,TransactionTracker.Outcome outcome) { history.observe(new PacketFrame(++sequence,0,ms*1_000_000,PacketDirection.INBOUND,PipelineFixtures.PROTOCOL,new Timing(TimingKind.PING,id,0,true)),new TransactionTracker.Acknowledgement(outcome,1)); }
    void spawn(int id,double x) { send(0,new EntitySpawn(id,new UUID(0,id),true,x,0,3)); }
    @Test void relativeMovesAndSweptInterpolationRetainConservativeAlternatives() {
        spawn(1,-2); send(50,new EntityMove(1,true,false,0,4,0,0));
        var view=history.view(1,60_000_000,settings); assertEquals(2,view.boxes().size());
        assertTrue(CombatGeometry.evaluate(new Vec3(0,1,0),0,0,view.boxes()).hit());
        assertTrue(view.reasons().contains("UNCONFIRMED_DELIVERY"));
    }
    @Test void onlyExactSampledPingAdvancesObservationFenceAndNeverConfirmsDelivery() {
        spawn(1,0); send(1,new Timing(TimingKind.PING,11,0,true));
        ack(2,99,TransactionTracker.Outcome.UNKNOWN); assertEquals(0,history.view(1,3_000_000,settings).acknowledgedSequence());
        ack(3,11,TransactionTracker.Outcome.OUT_OF_ORDER); assertEquals(0,history.view(1,4_000_000,settings).acknowledgedSequence());
        send(4,new Timing(TimingKind.PING,12,0,true)); long fence=sequence; ack(5,12,TransactionTracker.Outcome.MATCHED);
        var view=history.view(1,6_000_000,settings); assertEquals(fence,view.acknowledgedSequence());
        assertFalse(view.reasons().contains("NO_ACKNOWLEDGED_TARGET_BASELINE")); assertTrue(view.reasons().contains("UNCONFIRMED_DELIVERY"));
        send(6,new EntityMove(1,true,false,0,1,0,0)); assertTrue(history.view(1,7_000_000,settings).reasons().contains("TARGET_UPDATES_UNACKNOWLEDGED"));
    }
    @Test void teleportDoesNotSweepAcrossDiscontinuityAndRemovalPreventsIdReuse() {
        spawn(1,-10); send(50,new EntityMove(1,false,true,0,10,0,3));
        var view=history.view(1,60_000_000,settings); assertEquals(1,view.boxes().size());
        assertFalse(CombatGeometry.evaluate(new Vec3(0,1,0),0,0,view.boxes()).hit());
        send(60,new EntityRemove(List.of(1))); send(61,new EntityMove(1,true,false,0,1,0,0));
        assertFalse(history.view(1,62_000_000,settings).known());
        send(63,new EntitySpawn(1,null,false,0,0,0)); assertTrue(history.view(1,64_000_000,settings).reasons().contains("UNSUPPORTED_TARGET_TYPE"));
    }
    @Test void unknownRelativeFlagsAndInvalidCoordinatesDiscardGeometry() {
        spawn(1,0); send(1,new EntityMove(1,false,true,1,3,0,0)); assertFalse(history.view(1,2_000_000,settings).known());
        send(2,new EntitySpawn(1,null,true,Double.NaN,0,0)); assertEquals(0,history.size());
    }
    @Test void expiryFutureTimeAndMetadataAreExplicit() {
        spawn(1,0); send(1,new EntityDimensionsUnknown(1));
        assertTrue(history.view(1,2_000_000,settings).reasons().contains("TARGET_METADATA_CHANGED"));
        assertTrue(history.view(1,-1,settings).boxes().isEmpty());
        assertTrue(history.view(1,3_000_000_000L,settings).reasons().contains("TARGET_HISTORY_EXPIRED"));
    }
    @Test void targetAndHistoryBudgetsEvictInsteadOfGrowing() {
        for(int i=0;i<100;i++) spawn(i,0); assertEquals(settings.maximumTargets(),history.size()); assertFalse(history.view(0,0,settings).known());
        for(int i=1;i<30;i++) send(i,new EntityMove(99,true,false,0,.1,0,0));
        var view=history.view(99,30_000_000,settings); assertEquals(settings.historySize(),view.boxes().size()); assertTrue(view.reasons().contains("TARGET_HISTORY_TRIMMED"));
        history.clear(); assertEquals(0,history.size());
    }
}
