package dev.aegisac.common.config;
import dev.aegisac.common.combat.CombatId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
class CombatConfigurationTest {
    @TempDir Path directory;
    ConfigService service() throws Exception { var service=new ConfigService(new ConfigurationLoader(directory)); service.reload(); return service; }
    void write(String body) throws Exception { Files.writeString(directory.resolve("checks/combat.yml"),"config-version: 1\n"+body+"\n"); }
    @Test void legacyDisabledFileRemainsDisabledAndDefaultsDoNotRewriteIt() throws Exception {
        var service=service(); write("enabled: false"); var snapshot=service.reload();
        assertFalse(snapshot.combat().enabled()); assertEquals(CombatId.values().length,snapshot.combat().rules().size());
        assertEquals("config-version: 1\nenabled: false\n",Files.readString(directory.resolve("checks/combat.yml")));
    }
    @Test void thresholdsAndIndependentRulesReloadTogether() throws Exception {
        var service=service(); write("box-expansion: 0.2\nchecks:\n  ReachA:\n    enabled: false\n  AutoClickerA:\n    bedrock-mode: disabled");
        var s=service.reload(); assertEquals(.2,s.combat().boxExpansion()); assertFalse(s.combat().rules().get(CombatId.ReachA).enabled());
        assertTrue(s.combat().rules().get(CombatId.HitboxA).enabled()); assertEquals("disabled",s.combat().rules().get(CombatId.AutoClickerA).bedrockMode());
    }
    @ParameterizedTest @ValueSource(strings={"maximum-targets: 65","history-size: 9","pending-attacks: 33","statistics-size: 41"})
    void resourceChangesRequireRestartAndRetainOldGeneration(String body) throws Exception {
        var service=service(); var before=service.current(); write(body);
        var error=assertThrows(ConfigurationException.class,service::reload); assertTrue(error.getMessage().contains("restart")); assertSame(before,service.current());
    }
    @ParameterizedTest @ValueSource(strings={"box-expansion: .NaN","history-size: 500","minimum-statistics: 41","click-variation: -1.0","swing-window-ms: 0","checks:\n  ReachA:\n    bedrock-mode: enforce","checks:\n  MadeUpA:\n    enabled: true"})
    void invalidCombatPolicyRejectsWholeReload(String body) throws Exception {
        var service=service(); var before=service.current(); write(body);
        assertThrows(ConfigurationException.class,service::reload); assertSame(before,service.current());
    }
}
