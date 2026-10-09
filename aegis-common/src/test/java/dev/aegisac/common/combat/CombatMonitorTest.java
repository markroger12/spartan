package dev.aegisac.common.combat;
import dev.aegisac.api.player.*;
import dev.aegisac.common.check.*;
import dev.aegisac.common.config.*;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class CombatMonitorTest {
    final CombatMonitor monitor=new CombatMonitor();
    ConfigSnapshot config=CheckFixtures.config(1,CheckFixtures.noGrace());
    MovementSnapshot movement=new MovementSnapshot(true,true,0,0,0,0,0,true,false,0);
    long sequence;
    Set<String> bypass=Set.of();
    void packet(long ms,PacketDirection direction,NormalizedPacket packet) {
        long now=ms*1_000_000;
        if(packet instanceof Movement m) movement=new MovementSnapshot(true,true,m.position()?m.x():movement.x(),m.position()?m.y():movement.y(),m.position()?m.z():movement.z(),m.rotation()?m.yaw():movement.yaw(),m.rotation()?m.pitch():movement.pitch(),m.onGround(),false,now);
        monitor.process(config,new PacketFrame(++sequence,0,now,direction,PipelineFixtures.PROTOCOL,packet),movement,null,null,
                new OwnerObservation(now,"world",false,false,false,false,bypass),new CombatOwnerObservation(now,1.62,3),
                new ServerTickHealth.Snapshot(now,50_000_000,20,true),BedrockStatus.UNKNOWN,7);
    }
    void in(long ms,NormalizedPacket packet) { packet(ms,PacketDirection.INBOUND,packet); }
    void out(long ms,NormalizedPacket packet) { packet(ms,PacketDirection.OUTBOUND,packet); }
    void move(long ms,double y,float yaw,boolean ground) { in(ms,new Movement(true,true,0,y,0,yaw,0,ground,false)); }
    void attack(long ms,int target) { in(ms,new Interaction(target,"ATTACK","MAIN_HAND",false,0,0,0)); }
    void swing(long ms) { in(ms,new Action(PacketKind.SWING,"MAIN_HAND")); }
    @BeforeEach void baseline() { move(0,0,0,true); }
    @Test void longReachIsDiagnosticAndCanNeverAccumulateRuntimeBuffer() {
        out(3000,new EntitySpawn(10,null,true,0,0,7));
        for(int i=0;i<10;i++) { move(3010+i*20,0,0,true); attack(3011+i*20,10); }
        var state=monitor.snapshot().checks().get("ReachA"); assertTrue(state.diagnostics()>0); assertEquals(0,state.buffer()); assertEquals(0,state.findings());
        assertTrue(monitor.snapshot().evidence().stream().allMatch(e->e.diagnostic()&&e.reasons().contains("CLIENT_ACTIONS_UNCONFIRMED")));
    }
    @Test void lateSwingMatchesAndNoSwingIsDeferredUntilObservedWindowExpires() {
        move(3000,0,0,true); attack(3001,10); swing(3150);
        in(3201,NormalizedPacket.Other.INSTANCE); assertEquals(0,monitor.snapshot().checks().get("NoSwingA").evaluated());
        in(3202,NormalizedPacket.Other.INSTANCE); assertEquals(0,monitor.snapshot().checks().get("NoSwingA").diagnostics());
        attack(4300,10); in(4501,NormalizedPacket.Other.INSTANCE);
        assertEquals(1,monitor.snapshot().checks().get("NoSwingA").diagnostics());
    }
    @Test void regularSwingTimingNeedsStatisticsAndOffhandDoesNotCount() {
        for(int i=0;i<23;i++) swing(3000+i*50);
        assertEquals(0,monitor.snapshot().checks().get("AutoClickerA").diagnostics());
        for(int i=23;i<32;i++) swing(3000+i*50);
        assertTrue(monitor.snapshot().checks().get("AutoClickerA").diagnostics()>0);
        long count=monitor.snapshot().swings(); in(4700,new Action(PacketKind.SWING,"OFF_HAND")); assertEquals(count,monitor.snapshot().swings());
        assertEquals(20,monitor.snapshot().swingRate(),1e-9);
    }
    @Test void highVariableSwingRateAloneProducesNoClickDiagnostic() {
        long time=3000;
        for(int i=0;i<80;i++) { time+=i%2==0?10:40; swing(time); }
        assertTrue(monitor.snapshot().swingRate()>30); assertEquals(0,monitor.snapshot().checks().get("AutoClickerA").diagnostics());
    }
    @Test void alternatingTargetsAndMissingSwingCombineWithGeometryMiss() {
        out(3000,new EntitySpawn(10,null,true,8,0,0)); out(3000,new EntitySpawn(11,null,true,9,0,0)); move(3000,0,0,true);
        for(int i=0;i<4;i++) attack(3010+i*10,10+i%2);
        in(3300,NormalizedPacket.Other.INSTANCE);
        for(String id:List.of("HitboxA","MultiAuraA","AttackPatternA","NoSwingA","KillAuraA")) assertTrue(monitor.snapshot().checks().get(id).diagnostics()>0,id);
    }
    @Test void ownImpulseIsObservedOtherEntityImpulseIsIgnoredAndResetDropsPendingWork() {
        move(3000,0,0,true); out(3001,new Impulse(false,8,1,0,0)); move(3500,0,0,true);
        assertEquals(0,monitor.snapshot().checks().get("VelocityA").evaluated());
        out(3501,new Impulse(false,7,1,0,0)); move(3950,0,0,true);
        assertEquals(1,monitor.snapshot().checks().get("VelocityA").diagnostics());
        attack(4000,1); monitor.reset(4001_000_000L,0,"PACKET_LOSS"); in(4300,NormalizedPacket.Other.INSTANCE);
        assertEquals(0,monitor.snapshot().checks().get("NoSwingA").evaluated()); assertTrue(monitor.snapshot().evidence().isEmpty());
    }
    @Test void permissionsAreFrozenWithPendingAttacksAndGenerationsClearHistory() {
        bypass=Set.of("NoSwingA"); attack(3000,1); bypass=Set.of(); in(3300,NormalizedPacket.Other.INSTANCE);
        assertEquals(0,monitor.snapshot().checks().get("NoSwingA").diagnostics());
        out(3400,new EntitySpawn(10,null,true,0,0,7)); config=CheckFixtures.config(2,CheckFixtures.noGrace());
        in(3500,NormalizedPacket.Other.INSTANCE); assertEquals(0,monitor.snapshot().trackedTargets()); assertEquals(2,monitor.snapshot().generation());
    }
    @Test void ordinaryBlockChangesPreserveTargetsButTeleportClearsThem() {
        out(3000,new EntitySpawn(10,null,true,0,0,7)); out(3010,new Action(PacketKind.WORLD_CHANGE,"BLOCK_CHANGE"));
        assertEquals(1,monitor.snapshot().trackedTargets()); out(3020,new RotationCorrection(0,0,false,false)); assertEquals(0,monitor.snapshot().trackedTargets());
    }
    @Test void clickAndTargetStateClearOnBackwardObservationTime() {
        swing(3000); swing(3050); out(3060,new EntitySpawn(10,null,true,0,0,3)); swing(3040);
        assertEquals(1,monitor.snapshot().swings()); assertEquals(0,monitor.snapshot().trackedTargets()); assertEquals(0,monitor.snapshot().swingRate());
    }
    @Test void aimSnapAndRepeatedRotationAreIndependentDiagnostics() {
        out(3000,new EntitySpawn(10,null,true,0,.72,3)); move(3000,0,90,true); move(3010,0,0,true); attack(3011,10);
        assertTrue(monitor.snapshot().checks().get("AimA").diagnostics()>0);
        monitor.reset(4000_000_000L,0,"TEST"); move(4000,0,0,true);
        for(int i=1;i<30;i++) { move(4000+i*20,0,i,true); attack(4001+i*20,10); }
        assertTrue(monitor.snapshot().checks().get("RotationA").diagnostics()>0);
    }
    @Test void criticalPatternRequiresDistinctAirborneMovementSamples() {
        move(3000,-.01,0,false); for(int i=0;i<30;i++) attack(3001+i,10);
        assertEquals(0,monitor.snapshot().checks().get("CriticalsA").diagnostics());
        for(int i=1;i<30;i++) { move(3100+i*20,-.01-i*.01,0,false); attack(3101+i*20,10); }
        assertTrue(monitor.snapshot().checks().get("CriticalsA").diagnostics()>0);
    }
    @Test void selfTargetAndImpossiblePitchAreSpecificAttackDiagnostics() {
        move(3000,0,0,true); attack(3001,7);
        assertEquals(1,monitor.snapshot().checks().get("InvalidAttackA").diagnostics());
        in(3020,new Movement(false,true,0,0,0,0,100,true,false)); attack(3021,10);
        assertEquals(1,monitor.snapshot().checks().get("ImpossibleInteractionA").diagnostics());
    }
    @Test void attackTimingRequiresRegularIntervalsAndMissingSwingTogether() {
        for(int i=0;i<30;i++) { attack(3000+i*50,10); swing(3001+i*50); }
        in(4700,NormalizedPacket.Other.INSTANCE); assertEquals(0,monitor.snapshot().checks().get("AttackTimingA").diagnostics());
        for(int i=0;i<30;i++) attack(6000+i*50,10);
        in(7700,NormalizedPacket.Other.INSTANCE); assertTrue(monitor.snapshot().checks().get("AttackTimingA").diagnostics()>0);
    }
    @Test void sustainedAlignedRotationProducesAimAssistDiagnostic() {
        for(int i=1;i<30;i++) {
            float angle=i;
            double radians=Math.toRadians(angle);
            out(3000+i*20,new EntitySpawn(10,null,true,-Math.sin(radians)*3,.72,Math.cos(radians)*3));
            move(3001+i*20,0,angle,true); attack(3002+i*20,10);
        }
        assertTrue(monitor.snapshot().checks().get("AimAssistA").diagnostics()>0);
    }

    @Test void extremeFiniteRotationsAndNonfinitePositionsCannotCrashWorkerAnalysis() {
        in(3000,new Movement(true,true,0,0,0,0,Float.MAX_VALUE,false,false));
        in(3020,new Movement(true,true,0,0,0,0,-Float.MAX_VALUE,false,false));
        out(3021,new EntitySpawn(10,null,true,0,0,3));
        in(3040,new Movement(true,false,Double.NaN,0,0,0,0,false,false));
        assertEquals(0,monitor.snapshot().trackedTargets()); assertTrue(monitor.snapshot().evidence().isEmpty());
    }
    @Test void displayedSwingRateExpiresWithoutAdditionalPackets() {
        swing(3000); swing(3050); assertEquals(20,monitor.snapshot(3050_000_000L).swingRate());
        assertEquals(0,monitor.snapshot(5000_000_000L).swingRate());
    }

}
