package dev.aegisac.common.config;
import dev.aegisac.common.guard.GuardId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
class GuardConfigurationTest {
    @TempDir Path directory;
    ConfigService service() throws Exception { var s=new ConfigService(new ConfigurationLoader(directory)); s.reload(); return s; }
    void write(String category,String text) throws Exception { Files.writeString(directory.resolve("checks/"+category+".yml"),"config-version: 1\n"+text+"\n"); }
    @ParameterizedTest @ValueSource(strings={"world","player","inventory","protocol","exploit"})
    void oldDisabledFilesRemainDisabledWithoutRewriting(String category) throws Exception {
        var s=service(); write(category,"enabled: false"); var value=s.reload();
        assertFalse(value.guard().categories().get(category).enabled());
        assertEquals("config-version: 1\nenabled: false\n",Files.readString(directory.resolve("checks/"+category+".yml")));
        assertEquals(GuardId.values().length,value.guard().rules().size());
    }
    @Test void validChangesPublishOneGenerationWithIndependentCategoryRules() throws Exception {
        var s=service(); write("world","grace-ms: 0\nevidence-capacity: 4\nchecks:\n  BlockReachA:\n    limit: 0.2\n  DirectionA:\n    enabled: false");
        write("protocol","checks:\n  BadPacketsA:\n    bedrock-mode: disabled"); var value=s.reload();
        assertEquals(2,value.generation()); assertEquals(.2,value.guard().rules().get(GuardId.BlockReachA).limit());
        assertFalse(value.guard().rules().get(GuardId.DirectionA).enabled()); assertTrue(value.guard().rules().get(GuardId.RotationPlacementA).enabled());
        assertEquals(4,value.guard().categories().get("world").evidenceCapacity());
        assertThrows(UnsupportedOperationException.class,()->value.guard().rules().clear());
    }
    @ParameterizedTest @ValueSource(strings={"checks:\n  FastBreakA:\n    enabled: true","checks:\n  BlockReachA:\n    limit: .NaN","checks:\n  BlockReachA:\n    cooldown-ms: 0","checks:\n  BlockReachA:\n    bedrock-mode: enforce","checks:\n  InventedA:\n    enabled: true","evidence-capacity: 257","grace-ms: -1","maximum-delay-ms: 0"})
    void badPolicyRejectsWholeGeneration(String body) throws Exception {
        var s=service(); var before=s.current(); write("world",body); write("protocol","enabled: false");
        assertThrows(ConfigurationException.class,s::reload); assertSame(before,s.current());
        assertTrue(s.current().guard().categories().get("protocol").enabled());
    }
    @Test void unavailableModelsCannotBeEnabledEvenInsideDisabledCategory() throws Exception {
        var s=service(); write("player","enabled: false\nchecks:\n  FastEatA:\n    enabled: true");
        assertThrows(ConfigurationException.class,s::reload);
    }
}
