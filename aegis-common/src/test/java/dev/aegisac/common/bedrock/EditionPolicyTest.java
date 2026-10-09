package dev.aegisac.common.bedrock;
import dev.aegisac.api.player.BedrockStatus;
import dev.aegisac.common.config.*;
import dev.aegisac.common.check.CheckId;
import dev.aegisac.common.guard.GuardId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class EditionPolicyTest {
    @TempDir Path directory;
    ConfigService config() throws Exception { var s=new ConfigService(new ConfigurationLoader(directory)); s.reload(); return s; }
    void write(String file,String body) throws Exception { Files.writeString(directory.resolve(file),"config-version: 1\n"+body+"\n"); }
    @Test void allChecksHaveIndependentPoliciesAndUnknownUsesConservativeProfile() throws Exception {
        var base=config().current(); assertEquals(72,base.edition().bedrockChecks().size());
        var java=EditionPolicy.apply(base,BedrockStatus.JAVA);
        assertTrue(java.movement().rules().get(CheckId.SpeedA).enabled());
        for(var edition:new BedrockStatus[]{BedrockStatus.BEDROCK,BedrockStatus.UNKNOWN}) {
            var policy=EditionPolicy.apply(base,edition);
            assertFalse(policy.movement().rules().get(CheckId.SpeedA).enabled());
            assertEquals(2*java.guard().rules().get(GuardId.PacketSpamA).limit(),policy.guard().rules().get(GuardId.PacketSpamA).limit());
            assertTrue(policy.guard().rules().get(GuardId.InvalidPositionA).enabled());
            assertEquals(base.defaultProfile(),policy.defaultProfile()); assertEquals(base.worldProfiles(),policy.worldProfiles());
        }
    }
    @Test void overridesRemainIndependentAndGlobalDisableAlwaysWins() throws Exception {
        var service=config();
        write("profiles/java.yml","checks:\n  InvalidPositionA: false");
        write("profiles/bedrock.yml","checks:\n  PacketSpamA:\n    mode: BEDROCK_ADJUSTED\n    sensitivity-multiplier: 3\n    extra-tolerance: 7");
        var base=service.reload(); var java=EditionPolicy.apply(base,BedrockStatus.JAVA); var bedrock=EditionPolicy.apply(base,BedrockStatus.BEDROCK);
        assertFalse(java.guard().rules().get(GuardId.InvalidPositionA).enabled()); assertTrue(bedrock.guard().rules().get(GuardId.InvalidPositionA).enabled());
        assertEquals(java.guard().rules().get(GuardId.PacketSpamA).limit()/3+7,bedrock.guard().rules().get(GuardId.PacketSpamA).limit());
        write("checks/exploit.yml","checks:\n  PacketSpamA:\n    enabled: false");
        write("checks/protocol.yml","checks:\n  InvalidPositionA:\n    bedrock-mode: disabled");
        bedrock=EditionPolicy.apply(service.reload(),BedrockStatus.BEDROCK);
        assertFalse(bedrock.guard().rules().get(GuardId.PacketSpamA).enabled()); assertFalse(bedrock.guard().rules().get(GuardId.InvalidPositionA).enabled());
    }
    @Test void identicalMovementBurstHasDifferentJavaAndBedrockDiagnosticLimits() throws Exception {
        var base=config().current();
        for(var edition:BedrockStatus.values()) {
            var policy=EditionPolicy.apply(base,edition); var monitor=new dev.aegisac.common.guard.GuardMonitor();
            for(int i=0;i<61;i++) {
                long now=i==0?0:(3000L+i)*1_000_000;
                monitor.process(policy,new dev.aegisac.common.packet.PacketFrame(i+1,0,now,
                        dev.aegisac.common.packet.PacketDirection.INBOUND,dev.aegisac.common.packet.PipelineFixtures.PROTOCOL,
                        new dev.aegisac.common.packet.NormalizedPacket.Movement(true,true,0,1,0,0,0,true,false)),
                        new dev.aegisac.api.player.MovementSnapshot(false,false,0,0,0,0,0,false,false,0),new dev.aegisac.common.world.WorldView.State(0,null,false),
                        new dev.aegisac.common.guard.GuardOwnerObservation(now,1.62,4.5),
                        new dev.aegisac.common.check.OwnerObservation(now,"world",false,false,false,false,java.util.Set.of()),
                        null,new dev.aegisac.common.check.ServerTickHealth.Snapshot(now,50_000_000,20,true),edition,null,7);
            }
            var result=monitor.snapshot().checks().get("MovementSpamA");
            if(edition==BedrockStatus.JAVA) assertTrue(result.diagnostics()>0); else assertEquals(0,result.diagnostics());
            assertEquals(0,result.findings()); assertEquals(0,result.buffer());
        }
    }
    @Test void oldMinimalProfilesMergeWithoutOverwritingUserFiles() throws Exception {
        var service=config(); write("profiles/bedrock.yml","mode: observe-only"); String before=Files.readString(directory.resolve("profiles/bedrock.yml"));
        assertEquals(72,service.reload().edition().bedrockChecks().size()); assertEquals(before,Files.readString(directory.resolve("profiles/bedrock.yml")));
    }
    @ParameterizedTest @ValueSource(strings={"history-size: 65","history-max-age-ms: 0","checks:\n  FlyA:\n    mode: pretend","checks:\n  VehicleA:\n    mode: BEDROCK_SUPPORTED","checks:\n  PacketSpamA:\n    sensitivity-multiplier: .NaN","checks:\n  InvalidPositionA:\n    extra-tolerance: 2"})
    void invalidBedrockPolicyRejectsWholeReload(String body) throws Exception {
        var service=config(); var before=service.current(); write("profiles/bedrock.yml",body);
        assertThrows(ConfigurationException.class,service::reload); assertSame(before,service.current());
    }
    @ParameterizedTest @ValueSource(strings={"queries-per-tick: 0","poll-interval-ms: 10001","maximum-age-ms: 100"})
    void invalidIdentityBudgetsRejectReload(String body) throws Exception {
        var service=config(); write("compatibility.yml","identity:\n  "+body); assertThrows(ConfigurationException.class,service::reload);
    }
}
