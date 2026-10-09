package dev.aegisac.common.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class PipelineConfigurationTest {
    @TempDir Path directory;
    @Test void oldPhaseOneFilesInheritPipelineWithoutBeingOverwritten() throws Exception {
        Files.writeString(directory.resolve("performance.yml"),"config-version: 1\npacket-metrics: false\n");
        var service=new ConfigService(new ConfigurationLoader(directory));
        assertEquals(256,service.reload().pipeline().sessionCapacity());assertFalse(service.current().packetMetrics());
        assertFalse(Files.readString(directory.resolve("performance.yml")).contains("pipeline"));
    }
    @Test void startupOnlySettingsRejectReloadAndPreserveWholeGeneration() throws Exception {
        var service=new ConfigService(new ConfigurationLoader(directory));service.reload();
        var before=service.current();
        Path file=directory.resolve("performance.yml");Files.writeString(file,Files.readString(file).replace("workers: 2","workers: 3"));
        var error=assertThrows(ConfigurationException.class,service::reload);
        assertEquals("pipeline",error.path());assertTrue(error.getMessage().contains("restart"));assertSame(before,service.current());
        assertEquals(3,new ConfigService(new ConfigurationLoader(directory)).reload().pipeline().workers());
    }
    @ParameterizedTest @ValueSource(strings={"workers: 0","executor-capacity: 0","session-capacity: 999999","smoothing: .nan",
        "smoothing: .inf","smoothing: 0.0","smoothing: 1.1","pending-transactions: -1","history-capacity: 99999","active-probes: yesplease"})
    void invalidResourceAndTimingBoundsFailBeforePublication(String setting) throws Exception {
        Files.writeString(directory.resolve("performance.yml"),"config-version: 1\npipeline:\n  "+setting+"\n");
        assertThrows(ConfigurationException.class,()->new ConfigurationLoader(directory).load(1));
    }
}
