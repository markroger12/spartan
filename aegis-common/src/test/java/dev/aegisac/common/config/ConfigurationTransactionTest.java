package dev.aegisac.common.config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ConfigurationTransactionTest {
    @TempDir Path directory;
    ConfigService service;
    @BeforeEach void setup() throws Exception { service=new ConfigService(new ConfigurationLoader(directory)); service.reload(); }
    @Test void checkToggleIsDurableAndKeepsExactBackupWithComments() throws Exception {
        Path path=directory.resolve("checks/movement.yml"); String original=Files.readString(path)+"# staff comment\n"; Files.writeString(path,original);
        var next=service.edit(1,ConfigEdit.check("SpeedA",false)); assertEquals(2,next.generation());
        assertFalse(CheckCatalog.enabled(next,CheckCatalog.find("SpeedA")));
        assertEquals(original,Files.readString(path.resolveSibling("movement.yml.last-admin-edit.bak")));
        var restarted=new ConfigService(new ConfigurationLoader(directory)); assertFalse(CheckCatalog.enabled(restarted.reload(),CheckCatalog.find("SpeedA")));
    }
    @Test void profilesAndPunishmentAreIndependentAndPersist() throws Exception {
        service.edit(1,ConfigEdit.profile("arena.with.dots","strict"));
        service.edit(2,ConfigEdit.profile(null,"lenient"));
        service.edit(3,ConfigEdit.punishment(null,true));
        var s=service.reload(); assertEquals("strict",s.worldProfiles().get("arena.with.dots")); assertEquals("lenient",s.defaultProfile());
        assertTrue(s.output().punishments().enabled()); assertFalse(s.output().punishments().rules().get("SpeedA").enabled());
        assertEquals("bedrock",s.profileFor("arena.with.dots",dev.aegisac.api.player.BedrockStatus.UNKNOWN).edition());
    }
    @Test void unavailableModelAndArbitraryPathsCannotWrite() throws Exception {
        var before=service.current(); String original=Files.readString(directory.resolve("checks/movement.yml"));
        assertThrows(ConfigurationException.class,()->service.edit(1,ConfigEdit.check("NoSlowA",true)));
        assertThrows(IllegalArgumentException.class,()->service.edit(1,new ConfigEdit("../outside",List.of("enabled"),true)));
        assertThrows(IllegalArgumentException.class,()->service.edit(1,new ConfigEdit("performance.yml",List.of("packet-metrics"),false)));
        assertSame(before,service.current()); assertEquals(original,Files.readString(directory.resolve("checks/movement.yml")));
        assertFalse(Files.exists(directory.resolve("checks/movement.yml.last-admin-edit.bak")));
    }
    @Test void staleGenerationAndExternalChangesAreRejected() throws Exception {
        service.edit(1,ConfigEdit.profile(null,"strict"));
        assertThrows(IOException.class,()->service.edit(1,ConfigEdit.profile(null,"lenient")));
        Files.writeString(directory.resolve("config.yml"),"config-version: 1\ndefault-profile: lenient\n");
        assertThrows(IOException.class,()->service.edit(2,ConfigEdit.check("SpeedA",false)));
        assertEquals("strict",service.current().defaultProfile());
    }
    @Test void revokedAuthorizationAndEditsDuringAuthorizationCannotCommit() throws Exception {
        String original=Files.readString(directory.resolve("config.yml"));
        assertThrows(IOException.class,()->service.edit(1,ConfigEdit.profile(null,"strict"),()->false));
        assertEquals(original,Files.readString(directory.resolve("config.yml")));
        assertThrows(IOException.class,()->service.edit(1,ConfigEdit.profile(null,"strict"),()->{
            try { Files.writeString(directory.resolve("config.yml"),original+"# concurrent edit\n"); } catch(IOException e) { throw new RuntimeException(e); } return true;
        }));
        assertEquals(1,service.current().generation()); assertTrue(Files.readString(directory.resolve("config.yml")).endsWith("# concurrent edit\n"));
    }
    @Test void symbolicBackupIsNotFollowedOrReplaced() throws Exception {
        Path external=directory.resolve("external.txt"); Files.writeString(external,"preserve");
        Files.createSymbolicLink(directory.resolve("config.yml.last-admin-edit.bak"),external);
        assertThrows(IOException.class,()->service.edit(1,ConfigEdit.profile(null,"strict")));
        assertEquals("preserve",Files.readString(external)); assertEquals(1,service.current().generation());
    }
    @Test void extraAliasesRequireRestartAndCannotClaimBaseNames() throws Exception {
        Files.writeString(directory.resolve("config.yml"),"config-version: 1\ncommand-aliases: {staffac: true}\n");
        assertThrows(ConfigurationException.class,service::reload);
        assertEquals(Map.of("staffac",true),new ConfigurationLoader(directory).load(1).documents().get("config.yml").get("command-aliases"));
        Files.writeString(directory.resolve("config.yml"),"config-version: 1\ncommand-aliases: {ac: true}\n");
        assertThrows(ConfigurationException.class,service::reload);
    }
    @ParameterizedTest @ValueSource(strings={"rows: 1","content-slots: [0, 0]","content-slots: [54]","content-slots: [slot]","content-slots: []","controls: {close: 0}","titles: {dashboard: ''}","items: {check: {material: 'not a material'}}","items: {check: {lore: [1]}}","dashboard-links: [profile]","dashboard-links: [checks, checks]"})
    void rejectsUnsafeGuiLayouts(String yaml) throws Exception {
        Files.writeString(directory.resolve("gui.yml"),"config-version: 1\n"+yaml+"\n");
        assertThrows(ConfigurationException.class,service::reload); assertEquals(1,service.current().generation());
    }
    @Test void platformMaterialValidationRunsBeforePublication() {
        assertThrows(ConfigurationException.class,()->new ConfigurationLoader(directory,m->!m.equals("CHEST")).load(2));
    }
    @Test void legacyDisabledGuiIsPreservedWhileMissingSettingsAreFilledInMemory() throws Exception {
        String original="config-version: 1\nenabled: false\n"; Files.writeString(directory.resolve("gui.yml"),original);
        var gui=GuiSettings.load(service.reload().documents().get("gui.yml"),m->true);
        assertFalse(gui.enabled()); assertEquals(36,gui.content().size()); assertEquals(original,Files.readString(directory.resolve("gui.yml")));
    }
}
