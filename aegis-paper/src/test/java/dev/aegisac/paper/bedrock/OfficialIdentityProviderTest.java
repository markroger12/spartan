package dev.aegisac.paper.bedrock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.aegisac.common.bedrock.IdentityResult.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
class OfficialIdentityProviderTest {
    public enum Device { ANDROID }
    public enum Input { TOUCH }
    public static class Metadata {
        public Device getDeviceOs() { return Device.ANDROID; }
        public Input getInputMode() { return Input.TOUCH; }
        public String getVersion() { return version; }
    }
    static String version;
    public static class Floodgate {
        static Floodgate instance; static boolean positive,fail,metadataFailure; static Set<UUID> queried=new HashSet<>();
        public static Floodgate getInstance() { return instance; }
        public boolean isFloodgatePlayer(UUID uuid) { queried.add(uuid); if(fail) throw new IllegalStateException(); return positive; }
        public Metadata getPlayer(UUID uuid) { if(metadataFailure) throw new IllegalStateException(); return new Metadata(); }
    }
    public static class Connection { }
    public static class Geyser {
        static Connection connection;
        public static Geyser api() { return new Geyser(); }
        public Connection connectionByUuid(UUID uuid) { return connection; }
    }
    public static class WrongGeyser {
        public static WrongGeyser api() { return new WrongGeyser(); }
        public String connectionByUuid(UUID uuid) { return "not a connection"; }
    }
    @BeforeEach void reset() { resetFixture(); }
    static void resetFixture() {
        Floodgate.instance=new Floodgate(); Floodgate.positive=true; Floodgate.fail=false; Floodgate.metadataFailure=false; Floodgate.queried.clear(); version="1.21.100"; Geyser.connection=null;
    }
    static OfficialIdentityProvider floodgate() throws Exception { return OfficialIdentityProvider.floodgate(Floodgate.class,Metadata.class); }
    @Test void officialPositiveQueryAndOptionalMetadataAreReadWithoutHeuristics() throws Exception {
        UUID uuid=UUID.randomUUID(); var value=floodgate().query(uuid);
        assertEquals(POSITIVE,value.outcome()); assertEquals("ANDROID",value.device()); assertEquals("TOUCH",value.input()); assertEquals("1.21.100",value.version()); assertEquals(Set.of(uuid),Floodgate.queried);
    }
    @Test void singletonIsFetchedPerQueryAndFailuresAreNotNegatives() throws Exception {
        var reader=floodgate(); Floodgate.positive=false; assertEquals(NEGATIVE,reader.query(UUID.randomUUID()).outcome());
        Floodgate.fail=true; assertEquals(FAILURE,reader.query(UUID.randomUUID()).outcome());
        Floodgate.instance=null; assertEquals(FAILURE,reader.query(UUID.randomUUID()).outcome());
    }
    @Test void metadataFailurePreservesPositiveIdentityAndVersionIsBounded() throws Exception {
        var reader=floodgate(); version="malicious\n"; assertEquals("unknown",reader.query(UUID.randomUUID()).version());
        version="a".repeat(33); assertEquals("unknown",reader.query(UUID.randomUUID()).version());
        Floodgate.metadataFailure=true; var result=reader.query(UUID.randomUUID()); assertEquals(POSITIVE,result.outcome()); assertTrue(result.reasons().contains("METADATA_UNAVAILABLE"));
    }
    @Test void geyserRequiresRealConnectionReturnTypeAndHandlesNullableResult() throws Exception {
        var reader=OfficialIdentityProvider.geyser(Geyser.class,Connection.class);
        assertEquals(NEGATIVE,reader.query(UUID.randomUUID()).outcome()); Geyser.connection=new Connection(); assertEquals(POSITIVE,reader.query(UUID.randomUUID()).outcome());
        assertThrows(NoSuchMethodException.class,()->OfficialIdentityProvider.geyser(WrongGeyser.class,Connection.class));
        assertThrows(NoSuchMethodException.class,()->OfficialIdentityProvider.floodgate(Geyser.class,Metadata.class));
    }
    @Test void absentOptionalApisDoNotRequireServerLibraries() {
        assertThrows(ClassNotFoundException.class,()->OfficialIdentityProvider.bind("FLOODGATE",new ClassLoader(null){}));
    }
}
