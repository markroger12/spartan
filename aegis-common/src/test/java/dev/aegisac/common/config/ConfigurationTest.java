package dev.aegisac.common.config;

import dev.aegisac.api.player.BedrockStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {
    @TempDir Path directory;
    private ConfigService service() throws Exception {
        ConfigService service = new ConfigService(new ConfigurationLoader(directory));
        service.reload();
        return service;
    }
    @Test void installsEveryDocumentAndPreservesAdministratorEdits() throws Exception {
        ConfigService service = service();
        for (String file : ConfigurationLoader.FILES) assertTrue(Files.exists(directory.resolve(file)), file);
        Path root = directory.resolve("config.yml");
        String edited = Files.readString(root).replace("balanced", "strict") + "# Administrator comment\n";
        Files.writeString(root, edited);
        assertEquals("strict", service.reload().defaultProfile());
        assertEquals(edited, Files.readString(root));
        assertEquals(2, service.current().generation());
    }
    @Test void returnedDocumentsAreDeeplyImmutable() throws Exception {
        var config = service().current();
        assertThrows(UnsupportedOperationException.class, () -> config.documents().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.documents().get("config.yml").clear());
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>)config.documents().get("messages.yml").get("messages")).clear());
        assertThrows(UnsupportedOperationException.class, () -> config.worldProfiles().put("world", "strict"));
    }
    @Test void incompleteCurrentFilesUseDefaultsWithoutOverwritingThem() throws Exception {
        ConfigService service = service();
        Path root = directory.resolve("config.yml");
        Files.writeString(root, "config-version: 1\ndefault-profile: lenient\n");
        assertEquals("lenient", service.reload().defaultProfile());
        assertEquals(Map.of(), service.current().worldProfiles());
        assertFalse(Files.readString(root).contains("world-profiles"));
    }
    @Test void migrationRenamesAndBacksUpWithIdempotentSecondLoad() throws Exception {
        ConfigService service = service();
        String old = "# Keep me in backup\nconfig-version: 0\nprofile: strict\nworld-profiles: {arena: lenient}\n";
        Files.writeString(directory.resolve("config.yml"), old);
        assertEquals("strict", service.reload().defaultProfile());
        assertEquals("lenient", service.current().worldProfiles().get("arena"));
        try (var paths = Files.list(directory)) {
            var backups = paths.filter(path -> path.toString().endsWith(".bak")).toList();
            assertEquals(1, backups.size());
            assertEquals(old, Files.readString(backups.getFirst()));
        }
        String migrated = Files.readString(directory.resolve("config.yml"));
        assertTrue(migrated.contains("config-version: 1"));
        assertTrue(migrated.contains("default-profile: strict"));
        service.reload();
        assertEquals(migrated, Files.readString(directory.resolve("config.yml")));
    }
    @ParameterizedTest
    @ValueSource(strings = {
        "config-version: 1\ndefault-profile: [strict]",
        "config-version: 1\ndefault-profile: bogus",
        "config-version: 1\ndefault-profile: null",
        "config-version: 1\ndefault-profile: strict\ndefault-profile: lenient",
        "config-version: 99",
        "config-version: 1.0",
        "config-version: 1\nworld-profiles: {world: 5}",
        "config-version: 1\nmisspelled: true",
        "config-version: 0\nprofile: strict\ndefault-profile: balanced",
        "config-version: 1\nworld-profiles: &worlds {loop: *worlds}",
        "!!java.net.URL ['https://example.com']"
    })
    void badReloadRetainsExactPreviousSnapshot(String yaml) throws Exception {
        ConfigService service = service();
        ConfigSnapshot before = service.current();
        Files.writeString(directory.resolve("config.yml"), yaml);
        assertThrows(ConfigurationException.class, service::reload);
        assertSame(before, service.current());
    }
    @Test void errorsReportFilePathWithoutEchoingSecretValues() throws Exception {
        ConfigService service = service();
        Files.writeString(directory.resolve("config.yml"), "config-version: 1\ndefault-profile: secret-token-value\n");
        var error = assertThrows(ConfigurationException.class, service::reload);
        assertEquals("config.yml", error.file());
        assertEquals("default-profile", error.path());
        assertFalse(error.getMessage().contains("secret-token-value"));
    }
    @Test void malformedYamlReportsLine() throws Exception {
        ConfigService service = service();
        Files.writeString(directory.resolve("config.yml"), "config-version: 1\nworld-profiles: [\n");
        assertTrue(assertThrows(ConfigurationException.class, service::reload).line() > 0);
    }
    @Test void validatesAllDocumentsBeforeWritingMigrations() throws Exception {
        ConfigService service = service();
        String old = "config-version: 0\nprofile: strict\n";
        Files.writeString(directory.resolve("config.yml"), old);
        Files.writeString(directory.resolve("gui.yml"), "config-version: 1\nrows: 9\n");
        assertThrows(ConfigurationException.class, service::reload);
        assertEquals(old, Files.readString(directory.resolve("config.yml")));
        try (var paths = Files.list(directory)) { assertFalse(paths.anyMatch(path -> path.toString().endsWith(".bak"))); }
    }
    @Test void rejectsInvalidInventorySize() throws Exception {
        ConfigService service = service();
        Files.writeString(directory.resolve("gui.yml"), "config-version: 1\nrows: 9\n");
        var error = assertThrows(ConfigurationException.class, service::reload);
        assertEquals("rows", error.path());
        assertEquals("gui.yml", error.file());
    }
    @Test void largeFilesAreRejected() throws Exception {
        ConfigService service = service();
        Files.writeString(directory.resolve("config.yml"), "#".repeat(262_145));
        assertThrows(ConfigurationException.class, service::reload);
    }
    @Test void doesNotFollowConfigurationSymlinks() throws Exception {
        ConfigService service = service();
        Path outside = directory.resolveSibling(directory.getFileName() + "-external.yml");
        Files.writeString(outside, "config-version: 1");
        try {
            Files.delete(directory.resolve("config.yml"));
            Files.createSymbolicLink(directory.resolve("config.yml"), outside);
            assertThrows(java.io.IOException.class, service::reload);
            assertEquals("config-version: 1", Files.readString(outside));
        } finally { Files.deleteIfExists(outside); }
    }
    @Test void editionSelectionCannotBeOverriddenByDifficulty() throws Exception {
        ConfigService service = service();
        Files.writeString(directory.resolve("config.yml"), "config-version: 1\nworld-profiles: {arena: strict}\n");
        var snapshot = service.reload();
        assertEquals(new ConfigSnapshot.ProfileSelection("strict", "bedrock"), snapshot.profileFor("arena", BedrockStatus.BEDROCK));
        assertEquals(new ConfigSnapshot.ProfileSelection("strict", "bedrock"), snapshot.profileFor("arena", BedrockStatus.UNKNOWN));
        assertEquals(new ConfigSnapshot.ProfileSelection("balanced", "java"), snapshot.profileFor("world", BedrockStatus.JAVA));
    }
    @Test void readersNeverSeePartiallyPublishedReloads() throws Exception {
        ConfigService service = service();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var running = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread reader = Thread.ofPlatform().start(() -> {
            while (running.get()) {
                var snapshot = service.current();
                try {
                    assertEquals(ConfigurationLoader.FILES.size(), snapshot.documents().size());
                    assertEquals(snapshot.defaultProfile(), snapshot.documents().get("config.yml").get("default-profile"));
                } catch (Throwable error) { failure.set(error); break; }
            }
        });
        try {
            for (int i = 0; i < 8; i++) {
                Files.writeString(directory.resolve("config.yml"), "config-version: 1\ndefault-profile: " + (i % 2 == 0 ? "strict" : "lenient") + "\n");
                service.reload();
            }
        } finally { running.set(false); reader.join(5000); }
        assertFalse(reader.isAlive());
        assertNull(failure.get());
        assertEquals(9, service.current().generation());
    }
}
