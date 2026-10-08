package dev.aegisac.common.bedrock;
import dev.aegisac.api.player.*;
import dev.aegisac.common.config.EditionSettings;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.aegisac.common.packet.PipelineFixtures.PROTOCOL;
import static org.junit.jupiter.api.Assertions.*;
class BedrockMonitorTest {
    final BedrockMonitor monitor=new BedrockMonitor(); final EditionSettings settings=EditionSettings.defaults();
    EditionSnapshot identity(BedrockStatus status) { return new EditionSnapshot(status,"FIXTURE","TOUCH","UNKNOWN","1",0,Set.of()); }
    void packet(long generation,long time,NormalizedPacket packet,BedrockStatus status) { monitor.process(generation,settings,identity(status),new PacketFrame(time+1,0,time,PacketDirection.INBOUND,PROTOCOL,packet)); }
    Movement movement(double x) { return new Movement(true,true,x,1,0,20,0,true,false); }
    @Test void identicalJavaAndUnknownStreamsDoNotProduceBedrockHistory() {
        for(var status:List.of(BedrockStatus.JAVA,BedrockStatus.UNKNOWN)) {
            packet(1,0,movement(1),status); assertTrue(monitor.snapshot(0,settings,identity(status)).histories().isEmpty());
        }
        packet(1,1,movement(1),BedrockStatus.BEDROCK); assertEquals(1,monitor.snapshot(1,settings,identity(BedrockStatus.BEDROCK)).histories().get("movement").size());
    }
    @Test void eachLaneIsIndependentBoundedAndExpires() {
        for(int i=0;i<100;i++) packet(1,i,movement(i),BedrockStatus.BEDROCK);
        packet(1,101,new Input(1,0,true,false,false,false,1),BedrockStatus.BEDROCK);
        packet(1,102,new HeldItem(1),BedrockStatus.BEDROCK);
        packet(1,103,new Interaction(2,"ATTACK","MAIN_HAND",false,0,0,0),BedrockStatus.BEDROCK);
        packet(1,104,new UseItem("MAIN_HAND",1,0,0),BedrockStatus.BEDROCK);
        var view=monitor.snapshot(104,settings,identity(BedrockStatus.BEDROCK));
        assertEquals(6,view.histories().size()); assertEquals(16,view.histories().get("movement").size()); assertEquals(16,view.histories().get("rotation").size());
        for(String lane:List.of("input","inventory","combat","placement")) assertEquals(1,view.histories().get(lane).size());
        assertThrows(UnsupportedOperationException.class,()->view.histories().get("movement").clear());
        assertTrue(monitor.snapshot(6_000_000_000L,settings,identity(BedrockStatus.BEDROCK)).histories().values().stream().allMatch(List::isEmpty));
    }
    @Test void generationTeleportTimeReversalAndIdentityLossResetHistories() {
        packet(1,100,movement(1),BedrockStatus.BEDROCK); packet(2,101,movement(2),BedrockStatus.BEDROCK);
        assertEquals(1,monitor.snapshot(101,settings,identity(BedrockStatus.BEDROCK)).histories().get("movement").size());
        packet(2,99,new HeldItem(0),BedrockStatus.BEDROCK);
        assertTrue(monitor.snapshot(99,settings,identity(BedrockStatus.BEDROCK)).histories().get("movement").isEmpty());
        packet(2,102,new RotationCorrection(0,0,false,false),BedrockStatus.BEDROCK);
        assertTrue(monitor.snapshot(102,settings,identity(BedrockStatus.BEDROCK)).histories().values().stream().allMatch(List::isEmpty));
        packet(2,103,movement(1),BedrockStatus.UNKNOWN);
        assertTrue(monitor.snapshot(103,settings,identity(BedrockStatus.BEDROCK)).histories().values().stream().allMatch(List::isEmpty));
    }
    @Test void nonFiniteCoordinatesCannotLeakIntoApiHistory() {
        packet(1,0,movement(Double.NaN),BedrockStatus.BEDROCK);
        var values=monitor.snapshot(0,settings,identity(BedrockStatus.BEDROCK)).histories().get("movement").getFirst().values();
        assertEquals(Map.of("finite",0.0),values);
    }
}
