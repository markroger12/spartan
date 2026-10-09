package dev.aegisac.common.config;

import dev.aegisac.common.trace.AnalysisFingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeCompatibilityTest {
    @TempDir Path directory;
    private Map<String,byte[]> installHistoricalDefaults()throws Exception {
        var originals=new LinkedHashMap<String,byte[]>();
        try(var zip=new ZipInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/upgrades/phase11-defaults.zip")))) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                assertTrue(ConfigurationLoader.FILES.contains(entry.getName()));
                byte[] bytes=zip.readAllBytes();Path path=directory.resolve(entry.getName());Files.createDirectories(path.getParent());Files.write(path,bytes);originals.put(entry.getName(),bytes);
            }
        }
        assertEquals(24,originals.size());return originals;
    }
    @Test void phase11DefaultsLoadWithoutRewritingAndRetainSafeActions()throws Exception {
        var originals=installHistoricalDefaults();var first=new ConfigurationLoader(directory).load(1);
        assertFalse(first.output().punishments().enabled());assertFalse(first.output().setbacks().enabled());
        assertFalse(first.output().webhook().enabled());assertFalse(first.output().logging().enabled());
        var second=new ConfigurationLoader(directory).load(2);
        assertEquals(AnalysisFingerprint.of(first),AnalysisFingerprint.of(second));
        for(var entry:originals.entrySet())assertArrayEquals(entry.getValue(),Files.readAllBytes(directory.resolve(entry.getKey())));
    }
    @Test void operatorDisabledCategoriesMessagesAndTuningSurviveUpgrade()throws Exception {
        installHistoricalDefaults();
        for(String file:List.of("checks/movement.yml","checks/combat.yml","checks/world.yml","gui.yml")) {
            Path path=directory.resolve(file);Files.writeString(path,Files.readString(path).replaceFirst("enabled: true","enabled: false"));
        }
        Path messages=directory.resolve("messages.yml");String old="config-version: 1\nmessages:\n  performance: 'Operator custom %players%'\n";Files.writeString(messages,old);
        var config=new ConfigurationLoader(directory).load(1);
        assertFalse(config.movement().enabled());assertFalse(config.combat().enabled());
        assertEquals(false,config.documents().get("checks/world.yml").get("enabled"));assertEquals(false,config.documents().get("gui.yml").get("enabled"));
        assertEquals("Operator custom %players%",config.message("performance"));assertEquals(old,Files.readString(messages));
    }
    @Test void legacyUpgradeCreatesExactBackupAndIsIdempotent()throws Exception {
        installHistoricalDefaults();String legacy="# Legacy operator settings\nconfig-version: 0\nprofile: lenient\nworld-profiles: {arena: strict}\n";
        Files.writeString(directory.resolve("config.yml"),legacy);
        var first=new ConfigurationLoader(directory).load(1);assertEquals("lenient",first.defaultProfile());assertEquals("strict",first.worldProfiles().get("arena"));
        try(var files=Files.list(directory)) { var backups=files.filter(p->p.toString().endsWith(".bak")).toList();assertEquals(1,backups.size());assertEquals(legacy,Files.readString(backups.getFirst())); }
        byte[] upgraded=Files.readAllBytes(directory.resolve("config.yml"));new ConfigurationLoader(directory).load(2);assertArrayEquals(upgraded,Files.readAllBytes(directory.resolve("config.yml")));
    }
    @Test void futureSchemaAndRestartOnlyEditsRetainActiveGeneration()throws Exception {
        installHistoricalDefaults();var service=new ConfigService(new ConfigurationLoader(directory));var before=service.reload();
        Path root=directory.resolve("config.yml");byte[] original=Files.readAllBytes(root);Files.writeString(root,"config-version: 99\n");
        assertThrows(ConfigurationException.class,service::reload);assertSame(before,service.current());
        Files.write(root,original);Path performance=directory.resolve("performance.yml");Files.writeString(performance,Files.readString(performance).replace("workers: 2","workers: 3"));
        assertThrows(ConfigurationException.class,service::reload);assertSame(before,service.current());
        assertEquals(3,new ConfigurationLoader(directory).load(1).pipeline().workers());
    }
}
