package dev.aegisac.tools;

import dev.aegisac.tools.release.ConfigurationPreflight;
import dev.aegisac.common.config.ConfigurationLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationPreflightTest {
    @TempDir Path directory;
    @Test void missingDefaultsAndLegacyMigrationOnlyTouchTemporaryCopy()throws Exception {
        Path file=directory.resolve("config.yml");String legacy="# Kept\nconfig-version: 0\nprofile: strict\n";Files.writeString(file,legacy);
        var report=ConfigurationPreflight.check(directory,true);assertEquals(24,report.documents());assertEquals(23,report.defaultedFiles().size());assertEquals(java.util.List.of("config.yml"),report.migratedFiles());
        assertEquals(legacy,Files.readString(file));try(var files=Files.list(directory)) { assertEquals(1,files.count()); }
    }
    @Test void enabledActionsFailAlertOnlyPreflightWithoutEditingThem()throws Exception {
        new ConfigurationLoader(directory).load(1);Path file=directory.resolve("punishments.yml");String configured=Files.readString(file).replaceFirst("enabled: false","enabled: true");Files.writeString(file,configured);
        assertThrows(IOException.class,()->ConfigurationPreflight.check(directory,true));assertFalse(ConfigurationPreflight.check(directory,false).alertOnly());assertEquals(configured,Files.readString(file));
    }
    @Test void invalidFilesAreNotMigratedAndSymlinksAreRejected()throws Exception {
        Path root=directory.resolve("config.yml");Files.writeString(root,"config-version: 0\nprofile: strict\n");Path gui=directory.resolve("gui.yml");Files.writeString(gui,"config-version: 1\nrows: 9\n");
        assertThrows(dev.aegisac.common.config.ConfigurationException.class,()->ConfigurationPreflight.check(directory,true));assertTrue(Files.readString(root).contains("config-version: 0"));
        Files.delete(gui);Files.createSymbolicLink(gui,root);assertThrows(IOException.class,()->ConfigurationPreflight.check(directory,true));
    }
}
