package dev.aegisac.common.output;
import dev.aegisac.common.config.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OutputPolicyTest {
    @TempDir Path directory; ConfigService config;
    @BeforeEach void setup() throws Exception { config=new ConfigService(new ConfigurationLoader(directory)); config.reload(); }
    void write(String file,String body) throws Exception { Files.writeString(directory.resolve(file),"config-version: 1\n"+body+"\n"); }
    @Test void defaultsDoNotPunishAndOldDisabledDocumentsStayDisabledWithoutRewrite() throws Exception {
        assertFalse(config.current().output().punishments().enabled()); assertEquals(72,config.current().output().punishments().rules().size());
        assertTrue(config.current().output().punishments().rules().values().stream().noneMatch(OutputSettings.Rule::enabled));
        write("alerts.yml","enabled: false"); String before=Files.readString(directory.resolve("alerts.yml"));
        assertFalse(config.reload().output().alerts().enabled()); assertEquals(before,Files.readString(directory.resolve("alerts.yml")));
    }
    @ParameterizedTest @ValueSource(strings={"window-ms: 0","maximum-evidence-age-ms: 10000","checks:\n  VehicleA:\n    enabled: true","checks:\n  SpeedA:\n    confidence: .NaN","checks:\n  SpeedA:\n    minimum-findings: 257","checks:\n  SpeedA:\n    commands: []","checks:\n  SpeedA:\n    commands:\n      - 'kick %client_brand%'","checks:\n  SpeedA:\n    commands:\n      - '/kick %player%'","checks:\n  SpeedA:\n    commands:\n      - 123"})
    void invalidPunishmentRejectsWholeGeneration(String body) throws Exception {
        var before=config.current(); write("punishments.yml",body); assertThrows(ConfigurationException.class,config::reload); assertSame(before,config.current());
    }
    @Test void commandsAndDocumentsAreImmutable() {
        var rule=config.current().output().punishments().rules().get("SpeedA"); assertThrows(UnsupportedOperationException.class,()->rule.commands().clear());
        Map<?,?> raw=(Map<?,?>)config.current().documents().get("punishments.yml").get("checks");
        List<?> commands=(List<?>)((Map<?,?>)raw.get("SpeedA")).get("commands"); assertThrows(UnsupportedOperationException.class,commands::clear);
    }
    @ParameterizedTest @ValueSource(strings={"http://discord.com/api/webhooks/1/secret","https://evil.example/api/webhooks/1/secret","https://discord.com@evil.example/api/webhooks/1/secret","https://discord.com/api/webhooks/1/secret?x=1"})
    void endpointValidationNeverEchoesSecret(String url) throws Exception {
        write("webhooks.yml","url: '"+url+"'"); var failure=assertThrows(ConfigurationException.class,config::reload);
        assertFalse(failure.getMessage().contains("secret")); assertFalse(failure.getMessage().contains(url));
    }
    @Test void webhookToStringRedactsEndpoint() throws Exception {
        String url="https://discord.com/api/webhooks/123/"+"a".repeat(25); write("webhooks.yml","url: '"+url+"'");
        assertFalse(config.reload().output().webhook().toString().contains(url));
    }
    Detection detection(String id,long sequence,long time,boolean diagnostic,Set<String> reasons) { return new Detection(id,"movement",sequence,time,1,2,1,4,diagnostic,true,reasons); }
    @Test void diagnosticsUncertaintyAndNonfiniteEvidenceNeverEnterRisk() {
        var ledger=new ViolationLedger(); var policy=config.current().output().punishments();
        assertFalse(ledger.accept(detection("SpeedA",1,0,true,Set.of()),policy));
        assertFalse(ledger.accept(detection("SpeedA",2,0,false,Set.of("UNACKNOWLEDGED_WORLD")),policy));
        assertFalse(ledger.accept(new Detection("SpeedA","movement",3,0,1,Double.NaN,1,0,false,true,Set.of()),policy));
        assertEquals(0,ledger.snapshot(0,policy).totalRisk()); assertEquals(0,ledger.snapshot(0,policy).findings());
    }
    @Test void scoresDecayExpireDeduplicateAndGainOnlyBoundedIndependentCheckConfidence() {
        var ledger=new ViolationLedger(); var p=config.current().output().punishments();
        assertTrue(ledger.accept(detection("SpeedA",1,0,false,Set.of()),p)); assertFalse(ledger.accept(detection("SpeedA",1,1,false,Set.of()),p));
        assertEquals(2,ledger.snapshot(0,p).totalRisk()); assertEquals(1,ledger.snapshot(30_000_000_000L,p).totalRisk());
        assertTrue(ledger.accept(detection("FlyA",2,30_000_000_000L,false,Set.of()),p));
        assertEquals(52.5,ledger.snapshot(30_000_000_000L,p).confidence());
        assertEquals(0,ledger.snapshot(100_000_000_000L,p).findings());
    }
    @Test void boundedLedgerAndBackwardsTimeFailClosed() {
        var ledger=new ViolationLedger(); var p=config.current().output().punishments();
        for(int i=0;i<1000;i++) ledger.accept(detection("SpeedA",i,i,false,Set.of()),p);
        assertEquals(256,ledger.snapshot(1000,p).findings());
        assertFalse(ledger.accept(detection("SpeedA",1001,1,false,Set.of()),p)); assertEquals(0,ledger.snapshot(1,p).findings());
    }
    @Test void everyThresholdAndExperimentalOptInIsRequired() throws Exception {
        write("punishments.yml","enabled: true\nchecks:\n  SpeedA:\n    enabled: true\n    minimum-vl: 1.0\n    minimum-category-risk: 2.0\n    minimum-total-risk: 2.0\n    minimum-confidence: 50.0\n    minimum-findings: 1");
        var p=config.reload().output().punishments(); var d=detection("SpeedA",1,0,false,Set.of()); var ledger=new ViolationLedger(); ledger.accept(d,p);
        assertFalse(ViolationLedger.qualifies(d,ledger.snapshot(0,p),p));
        p=new OutputSettings.Punishments(true,true,p.windowMillis(),p.decayMillis(),p.cooldownMillis(),p.maximumAgeMillis(),p.rules());
        assertTrue(ViolationLedger.qualifies(d,ledger.snapshot(0,p),p)); assertFalse(ViolationLedger.qualifies(d,ledger.snapshot(1,p),p));
        assertFalse(ViolationLedger.qualifies(detection("SpeedA",2,0,true,Set.of()),ledger.snapshot(0,p),p));
    }
    @ParameterizedTest @ValueSource(strings={"POSITION","VELOCITY_CANCEL","ACTION_CANCEL","BLOCK_CANCEL","ATTACK_CANCEL","FAKE"})
    void unsupportedCorrectionModelsCannotBeEnabled(String mode) throws Exception {
        write("setbacks.yml","enabled: true\nmode: "+mode); assertThrows(ConfigurationException.class,config::reload);
    }
    @Test void setbackPolicyIsIndependentOfPunishmentsAndCannotEnableMissingMovement() throws Exception {
        write("setbacks.yml","enabled: true\nmode: LAST_VALID_POSITION\nchecks:\n  SpeedA: true");
        var policy=config.reload().output(); assertTrue(policy.setbacks().enabled()); assertFalse(policy.punishments().enabled());
        write("setbacks.yml","checks:\n  VehicleA: true"); assertThrows(ConfigurationException.class,config::reload);
    }
    @Test void rendererIsSinglePassAndJsonEscapesUntrustedValues() {
        assertEquals("%uuid%",OutputRecord.render("%player%",Map.of("player","%uuid%","uuid","secret")));
        assertEquals("\"a\\\"\\n\\\\b\"",OutputRecord.quote("a\"\n\\b")); assertEquals("ared",OutputRecord.clean("a\n§red",32));
    }
}
