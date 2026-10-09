package dev.aegisac.common.config;
import dev.aegisac.common.check.CheckId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class MovementConfigurationTest {
    @TempDir Path directory;
    @Test void existingDisabledMovementIsPreservedWhileNewSettingsInheritDefaults() throws Exception {
        Files.createDirectories(directory.resolve("checks"));
        var file=directory.resolve("checks/movement.yml"); String old="config-version: 1\nenabled: false\n"; Files.writeString(file,old);
        var config=new ConfigurationLoader(directory).load(1); assertFalse(config.movement().enabled());
        assertEquals(old,Files.readString(file)); assertEquals(18,config.movement().rules().size());
    }
    @Test void liveReloadPublishesCompleteCheckPolicyAndFailureRetainsPreviousGeneration() throws Exception {
        var service=new ConfigService(new ConfigurationLoader(directory)); var first=service.reload();
        var file=directory.resolve("checks/movement.yml");
        Files.writeString(file,"config-version: 1\nchecks:\n  SpeedA:\n    tolerance: 0.2\n");
        var second=service.reload(); assertEquals(first.generation()+1,second.generation());
        assertEquals(.2,second.movement().rules().get(CheckId.SpeedA).tolerance());
        Files.writeString(file,"config-version: 1\nchecks:\n  SpeedA:\n    tolerance: .nan\n");
        assertThrows(ConfigurationException.class,service::reload); assertSame(second,service.current());
    }
    @ParameterizedTest @ValueSource(strings={"tolerance: .nan","tolerance: .inf","increment: 0.0","buffer-threshold: -1.0",
            "minimum-samples: 0","cooldown-ms: 0","bedrock-mode: supported","enabled: maybe"})
    void rejectsInvalidPerCheckSettings(String setting) throws Exception {
        Files.createDirectories(directory.resolve("checks"));
        Files.writeString(directory.resolve("checks/movement.yml"),"config-version: 1\nchecks:\n  SpeedA:\n    "+setting+"\n");
        assertThrows(ConfigurationException.class,()->new ConfigurationLoader(directory).load(1));
    }
    @ParameterizedTest @ValueSource(strings={"NoSlowA","ElytraA","VehicleA"})
    void cannotEnableMissingModels(String id) throws Exception {
        Files.createDirectories(directory.resolve("checks"));
        Files.writeString(directory.resolve("checks/movement.yml"),"config-version: 1\nchecks:\n  "+id+":\n    enabled: true\n");
        var error=assertThrows(ConfigurationException.class,()->new ConfigurationLoader(directory).load(1));
        assertEquals("checks."+id+".enabled",error.path());
    }
    @Test void exactWorldExemptionsRequireBooleansAndDoNotEraseOtherSettings() throws Exception {
        Files.writeString(directory.resolve("exemptions.yml"),"config-version: 1\nworlds: {lobby: true, arena: false}\n");
        var config=new ConfigurationLoader(directory).load(1); assertTrue(config.exemptions().worlds().contains("lobby"));
        assertFalse(config.exemptions().worlds().contains("arena")); assertTrue(config.exemptions().permissions());
        Files.writeString(directory.resolve("exemptions.yml"),"config-version: 1\nworlds: {lobby: pretend}\n");
        assertThrows(ConfigurationException.class,()->new ConfigurationLoader(directory).load(2));
    }
}
