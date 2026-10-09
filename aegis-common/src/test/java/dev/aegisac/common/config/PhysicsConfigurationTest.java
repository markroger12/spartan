package dev.aegisac.common.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class PhysicsConfigurationTest {
    @TempDir Path directory;
    @Test void oldFilesInheritDefaultsAndPhysicsReloadRetainsPreviousGeneration() throws Exception {
        Path file=directory.resolve("performance.yml"); String old="config-version: 1\npacket-metrics: false\n"; Files.writeString(file,old);
        var service=new ConfigService(new ConfigurationLoader(directory)); var before=service.reload();
        assertEquals(PhysicsSettings.DEFAULT,before.physics()); assertEquals(old,Files.readString(file));
        Files.writeString(file,old+"physics:\n  enabled: false\n");
        var failure=assertThrows(ConfigurationException.class,service::reload);
        assertEquals("physics",failure.path()); assertSame(before,service.current());
        assertFalse(new ConfigurationLoader(directory).load(1).physics().enabled());
    }
    @ParameterizedTest @ValueSource(strings={"captures-per-tick: 0","radius: 5","maximum-blocks: 0","maximum-boxes: 999999",
        "maximum-entities: 65","maximum-age-ms: 0","maximum-age-ms: .nan","enabled: maybe"})
    void rejectsUnboundedOrMalformedCaptureSettings(String setting) throws Exception {
        Files.writeString(directory.resolve("performance.yml"),"config-version: 1\nphysics:\n  "+setting+"\n");
        assertThrows(ConfigurationException.class,()->new ConfigurationLoader(directory).load(1));
    }
}
