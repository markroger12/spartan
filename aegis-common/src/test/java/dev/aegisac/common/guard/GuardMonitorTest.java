package dev.aegisac.common.guard;
import dev.aegisac.api.player.*;
import dev.aegisac.api.check.*;
import dev.aegisac.common.check.*;
import dev.aegisac.common.config.*;
import dev.aegisac.common.collision.*;
import dev.aegisac.common.physics.MotionContext;
import dev.aegisac.common.connection.TransactionTracker;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.common.world.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class GuardMonitorTest {
    final GuardMonitor monitor=new GuardMonitor();
    ConfigSnapshot config=CheckFixtures.config(1,CheckFixtures.noGrace());
    ClientProtocol protocol=new ClientProtocol(767,"1.21",true,true,true,true);
    MovementSnapshot movement=new MovementSnapshot(true,true,.5,0,0,0,0,true,false,0);
    WorldView.State world=new WorldView.State(0,null,false);
    Set<String> bypasses=Set.of(); boolean creative;
    long sequence;
    @BeforeEach void baseline() { packet(0,PacketDirection.INBOUND,Other.INSTANCE,null); }
    void packet(long ms,PacketDirection direction,NormalizedPacket p,TransactionTracker.Acknowledgement ack) {
        long now=ms*1_000_000L;
        monitor.process(config,new PacketFrame(++sequence,0,now,direction,protocol,p),movement,world,
                new GuardOwnerObservation(now,1.62,4.5),new OwnerObservation(now,"world",creative,false,false,false,bypasses),
                null,new ServerTickHealth.Snapshot(now,50_000_000,20,true),BedrockStatus.UNKNOWN,ack,7);
        if(p instanceof Movement m) movement=new MovementSnapshot(true,true,m.position()?m.x():movement.x(),m.position()?m.y():movement.y(),m.position()?m.z():movement.z(),m.rotation()?m.yaw():movement.yaw(),m.rotation()?m.pitch():movement.pitch(),m.onGround(),false,now);
    }
    void in(long ms,NormalizedPacket p) { packet(ms,PacketDirection.INBOUND,p,null); }
    void out(long ms,NormalizedPacket p) { packet(ms,PacketDirection.OUTBOUND,p,null); }
    void move(long ms,double x,double y,float yaw) { in(ms,new Movement(true,true,x,y,0,yaw,0,true,false)); }
    BlockAction place(int x,int y,int z,int face,int seq) { return new BlockAction(true,x,y,z,face,seq,"PLACE","MAIN_HAND",.5f,.5f,.5f); }
    BlockAction dig(int x,int y,int z,String action,int seq) { return new BlockAction(false,x,y,z,2,seq,action,"",0,0,0); }
    CheckSnapshot state(GuardId id) { return monitor.snapshot().checks().get(id.name()); }
    void diagnostic(GuardId id) { assertTrue(state(id).diagnostics()>0,id.name()+": "+state(id)); assertEquals(0,state(id).buffer()); assertEquals(0,state(id).findings()); }
    @Test void blockRangeFaceAndRotationUseFreshOwnerGeometry() {
        move(3000,.5,0,180); in(3001,place(0,1,8,3,1));
        diagnostic(GuardId.BlockReachA); diagnostic(GuardId.DirectionA); diagnostic(GuardId.RotationPlacementA);
        assertTrue(monitor.snapshot().evidence().stream().allMatch(e->e.reasons().contains("OBSERVATION_ONLY")));
    }
    @Test void plausiblePlacementDoesNotCreateGeometryDiagnostics() {
        move(3000,.5,0,0); in(3001,place(0,1,3,2,1));
        for(var id:List.of(GuardId.BlockReachA,GuardId.DirectionA,GuardId.RotationPlacementA,GuardId.ImpossiblePlaceA)) assertEquals(0,state(id).diagnostics(),id.name());
    }
    @Test void unavailableAttackerGeometrySuppressesReachInsteadOfUsingOrigin() {
        in(3000,place(0,1,8,3,1)); assertEquals(0,state(GuardId.BlockReachA).evaluated());
    }
    void capture(long ms,List<BlockSample> blocks) {
        world=new WorldView.State(0,new WorldSnapshot(new UUID(0,1),0,ms*1_000_000L,new Aabb(-5,-5,-5,10,10,10),blocks,List.of(),MotionContext.NORMAL,Set.of()),false);
    }
    @Test void capturedOcclusionAndLiquidRequireCoverageAndFreshness() {
        var wall=new Aabb(0,1,1,1,2,2); var target=new Aabb(0,1,3,1,2,4);
        var blocks=List.of(new BlockSample(wall,List.of(wall),Set.of(BlockSample.Surface.SOLID)),new BlockSample(target,List.of(),Set.of(BlockSample.Surface.WATER)));
        capture(3000,blocks); move(3000,.5,0,0); in(3001,dig(0,1,3,"START_DIGGING",1)); in(3002,dig(0,1,3,"FINISHED_DIGGING",2));
        diagnostic(GuardId.GhostHandA); diagnostic(GuardId.LiquidInteractionA);
        long evaluated=state(GuardId.GhostHandA).evaluated(); move(5000,.5,0,0); in(5001,place(0,1,3,2,3));
        assertEquals(evaluated,state(GuardId.GhostHandA).evaluated());
    }
    @Test void scaffoldCombinesRapidBelowFeetPlacementAndRayMiss() {
        move(3000,.5,2,180); in(3001,place(0,1,0,1,1)); in(3100,place(0,1,0,1,2)); diagnostic(GuardId.ScaffoldA);
    }
    @Test void towerRequiresRepeatedVerticalProgression() {
        for(int i=0;i<4;i++) { move(3000+i*100,.5,2+i,0); in(3001+i*100,place(0,1+i,0,1,i)); }
        diagnostic(GuardId.TowerA);
    }
    @Test void digStateMatchesTargetAndNonBlockActionsDoNotContaminateIt() {
        in(3000,dig(1,1,1,"START_DIGGING",1)); in(3001,dig(0,0,0,"DROP_ITEM",0)); in(3002,dig(1,1,1,"FINISHED_DIGGING",2));
        assertEquals(0,state(GuardId.InvalidDigA).diagnostics());
        in(4100,dig(2,1,1,"FINISHED_DIGGING",3)); diagnostic(GuardId.InvalidDigA);
    }
    @Test void distinctDigTargetsAndPlacementRatesUseBoundedWindows() {
        in(3000,place(0,1,0,1,1));
        for(int i=0;i<40;i++) { in(4000+i,place(0,1,0,1,2+i)); in(4000+i,dig(i,1,0,"FINISHED_DIGGING",100+i)); }
        diagnostic(GuardId.FastPlaceA); diagnostic(GuardId.NukerA);
    }
    @Test void invalidCursorIsDiagnosedButLegacyAirUseSentinelIsAccepted() {
        in(3000,new BlockAction(true,0,0,0,6,0,"PLACE","MAIN_HAND",2,0,0)); diagnostic(GuardId.ImpossiblePlaceA);
        monitor.reset(4000_000_000L,"TEST"); protocol=new ClientProtocol(47,"1.8",true,false,false,false);
        in(7000,place(-1,-1,-1,255,0)); assertEquals(0,state(GuardId.ImpossiblePlaceA).evaluated());
        in(7001,dig(0,0,0,"RELEASE_USE_ITEM",0)); assertEquals(0,state(GuardId.UseOrderA).diagnostics());
    }
    @Test void useReleaseAndUseRequestRateAreDistinctFromConsumption() {
        in(3000,dig(0,0,0,"RELEASE_USE_ITEM",0)); diagnostic(GuardId.UseOrderA);
        in(4000,new UseItem("MAIN_HAND",1,0,0)); in(4001,dig(0,0,0,"RELEASE_USE_ITEM",0));
        assertEquals(1,state(GuardId.UseOrderA).diagnostics());
        for(int i=0;i<40;i++) in(5100+i,new UseItem("MAIN_HAND",i+2,0,0)); diagnostic(GuardId.FastUseA);
        assertEquals("UNAVAILABLE",state(GuardId.FastEatA).status());
    }
    @Test void observedMenuControlsInventoryDiagnosticsAndUnrelatedCloseDoesNotClearIt() {
        out(3000,new Inventory(InventoryAction.OPEN,4,0,0,0,""));
        move(3000,.5,0,0); move(3001,1,0,0); diagnostic(GuardId.InventoryMoveA);
        in(3002,new EntityAction(7,"START_SPRINTING",0)); diagnostic(GuardId.InventorySprintA);
        in(3003,new Inventory(InventoryAction.CLOSE,8,0,0,0,""));
        in(3004,new Inventory(InventoryAction.CLICK,8,0,0,0,"PICKUP")); diagnostic(GuardId.ImpossibleInventoryA);
        in(3005,new Inventory(InventoryAction.CLICK,4,0,-999,0,"PICKUP")); assertEquals(0,state(GuardId.SlotSpoofA).diagnostics());
        in(3006,new Inventory(InventoryAction.CLICK,4,0,-2,0,"PICKUP")); diagnostic(GuardId.SlotSpoofA);
        in(3007,new HeldItem(9)); diagnostic(GuardId.InvalidSlotA);
        in(3008,new Inventory(InventoryAction.CLOSE,4,0,0,0,"")); long evaluated=state(GuardId.InventoryMoveA).evaluated();
        move(3009,2,0,0); assertEquals(evaluated,state(GuardId.InventoryMoveA).evaluated());
    }
    @Test void malformedCoordinatesAreFiniteEvidenceAndProtocolBoundsAreIndependent() {
        in(3000,new Movement(true,false,Double.NaN,0,0,0,0,false,false)); diagnostic(GuardId.BadPacketsA);
        in(3001,new Movement(true,true,30_000_001,0,0,0,91,false,false)); diagnostic(GuardId.InvalidPositionA); diagnostic(GuardId.InvalidPitchA);
        assertTrue(monitor.snapshot().evidence().stream().allMatch(e->Double.isFinite(e.observed())));
    }
    @Test void sequenceUsesModernCapabilityAndAcceptsSignedWrap() {
        in(3000,new UseItem("MAIN_HAND",Integer.MAX_VALUE,0,0)); in(3001,new UseItem("MAIN_HAND",Integer.MIN_VALUE,0,0));
        assertEquals(0,state(GuardId.InvalidSequenceA).diagnostics());
        in(3002,new UseItem("MAIN_HAND",Integer.MIN_VALUE,0,0)); diagnostic(GuardId.InvalidSequenceA);
        monitor.reset(4000_000_000L,"TEST"); protocol=ClientProtocol.UNKNOWN;
        in(7000,new UseItem("MAIN_HAND",0,0,0)); in(7001,new UseItem("MAIN_HAND",0,0,0)); assertEquals(0,state(GuardId.InvalidSequenceA).evaluated());
    }
    @Test void timingDiagnosticsDoNotInventAcknowledgementOrIgnoreServerAbilities() {
        out(3000,new Abilities(false,true,false,false,0,0)); in(3001,new Abilities(true,false,false,false,0,0)); diagnostic(GuardId.ImpossibleClientStateA);
        in(3002,new Interaction(7,"ATTACK","MAIN_HAND",false,0,0,0)); diagnostic(GuardId.InvalidEntityInteractionA);
        packet(3003,PacketDirection.INBOUND,new Timing(TimingKind.PING,9,0,true),new TransactionTracker.Acknowledgement(TransactionTracker.Outcome.UNKNOWN,-1)); diagnostic(GuardId.TransactionA);
        out(4000,new Teleport(4,0,0,0,0,0,0,0,0,0)); move(7000,.5,0,0); diagnostic(GuardId.PacketOrderA);
        packet(7001,PacketDirection.INBOUND,new Timing(TimingKind.TELEPORT,4,0,true),new TransactionTracker.Acknowledgement(TransactionTracker.Outcome.MATCHED,1));
        long evaluated=state(GuardId.PacketOrderA).evaluated(); move(7002,.5,0,0); assertEquals(evaluated,state(GuardId.PacketOrderA).evaluated());
    }
    @Test void eachRateFamilyRequiresItsOwnFullWindow() {
        move(3000,.5,0,0); in(3000,new Interaction(8,"ATTACK","MAIN_HAND",false,0,0,0)); in(3000,new Payload(1,"fixture",""));
        for(int i=0;i<320;i++) {
            in(4100,Other.INSTANCE);
            if(i<90) in(4100,new Interaction(8,"ATTACK","MAIN_HAND",false,0,0,0));
            if(i<50) move(4100,.5,0,0);
            if(i<25) in(4100,new Payload(5000,"fixture",""));
        }
        for(var id:List.of(GuardId.PacketSpamA,GuardId.MovementSpamA,GuardId.InteractionSpamA,GuardId.PayloadSpamA,GuardId.PayloadSizeA)) diagnostic(id);
    }
    @Test void bypassGraceAndGamemodeSuppressDiagnosticsAndReloadClearsHistory() {
        in(1,new HeldItem(9)); assertEquals(0,state(GuardId.InvalidSlotA).diagnostics());
        bypasses=Set.of("InvalidSlotA"); in(3000,new HeldItem(9)); assertEquals(0,state(GuardId.InvalidSlotA).diagnostics());
        bypasses=Set.of(); creative=true; in(4000,new HeldItem(9)); assertEquals(0,state(GuardId.InvalidSlotA).diagnostics());
        creative=false; in(5000,new HeldItem(9)); diagnostic(GuardId.InvalidSlotA);
        config=CheckFixtures.config(2,CheckFixtures.noGrace()); in(6000,Other.INSTANCE); assertTrue(monitor.snapshot().evidence().isEmpty()); assertEquals(2,monitor.snapshot().generation());
    }
    @Test void backwardsTimeAndResetDiscardMenuAndDigState() {
        out(3000,new Inventory(InventoryAction.OPEN,4,0,0,0,"")); in(3001,dig(1,1,1,"START_DIGGING",1)); in(2999,Other.INSTANCE);
        in(6000,new Inventory(InventoryAction.CLICK,8,0,0,0,"PICKUP")); assertEquals(0,state(GuardId.ImpossibleInventoryA).evaluated());
        in(6001,dig(1,1,1,"FINISHED_DIGGING",2)); diagnostic(GuardId.InvalidDigA);
        monitor.reset(6002_000_000L,"CLOSED"); assertTrue(monitor.snapshot().evidence().isEmpty());
    }
}
