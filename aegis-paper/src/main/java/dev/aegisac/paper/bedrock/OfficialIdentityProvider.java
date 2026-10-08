package dev.aegisac.paper.bedrock;
import dev.aegisac.common.bedrock.IdentityResult;
import java.lang.reflect.*;
import java.util.*;
/** Optional public API linkage. Never infer identity from names, UUID layout, brand or protocol. */
public final class OfficialIdentityProvider {
    private final String name;
    private final Method instance,query,player,device,input,version;
    private OfficialIdentityProvider(String name,Method instance,Method query,Method player,Method device,Method input,Method version) {
        this.name=name; this.instance=instance; this.query=query; this.player=player; this.device=device; this.input=input; this.version=version;
    }
    public static OfficialIdentityProvider bind(String name,ClassLoader loader) throws ReflectiveOperationException {
        if(name.equals("FLOODGATE")) return floodgate(Class.forName("org.geysermc.floodgate.api.FloodgateApi",false,loader),
                Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer",false,loader));
        if(name.equals("GEYSER")) return geyser(Class.forName("org.geysermc.geyser.api.GeyserApi",false,loader),Class.forName("org.geysermc.geyser.api.connection.GeyserConnection",false,loader));
        throw new IllegalArgumentException("Unknown provider");
    }
    static OfficialIdentityProvider floodgate(Class<?> api,Class<?> metadata) throws NoSuchMethodException {
        Method factory=api.getMethod("getInstance"),query=api.getMethod("isFloodgatePlayer",UUID.class);
        requireFactory(factory,api); if(query.getReturnType()!=boolean.class) throw new NoSuchMethodException("Expected boolean Floodgate query");
        return new OfficialIdentityProvider("FLOODGATE",factory,query,optional(api,"getPlayer",UUID.class),optional(metadata,"getDeviceOs"),optional(metadata,"getInputMode"),optional(metadata,"getVersion"));
    }
    static OfficialIdentityProvider geyser(Class<?> api,Class<?> connection) throws NoSuchMethodException {
        Method factory=api.getMethod("api"),query=api.getMethod("connectionByUuid",UUID.class); requireFactory(factory,api);
        if(!connection.isAssignableFrom(query.getReturnType())) throw new NoSuchMethodException("Expected nullable Geyser connection");
        return new OfficialIdentityProvider("GEYSER",factory,query,null,null,null,null);
    }
    private static void requireFactory(Method method,Class<?> api) throws NoSuchMethodException { if(!Modifier.isStatic(method.getModifiers())||!api.isAssignableFrom(method.getReturnType())) throw new NoSuchMethodException("Expected static API accessor"); }
    private static Method optional(Class<?> type,String method,Class<?>... args) { try { return type.getMethod(method,args); } catch(NoSuchMethodException missing) { return null; } }
    public IdentityResult query(UUID uuid) {
        try {
            Object api=instance.invoke(null); if(api==null) return IdentityResult.of(name,IdentityResult.Outcome.FAILURE);
            Object result=query.invoke(api,uuid);
            boolean positive=name.equals("FLOODGATE")?Boolean.TRUE.equals(result):result!=null;
            if(!positive) return IdentityResult.of(name,IdentityResult.Outcome.NEGATIVE);
            if(player==null) return IdentityResult.of(name,IdentityResult.Outcome.POSITIVE);
            try {
                Object metadata=player.invoke(api,uuid);
                if(metadata==null) return new IdentityResult(name,IdentityResult.Outcome.POSITIVE,"UNKNOWN","UNKNOWN","unknown",Set.of("METADATA_UNAVAILABLE"));
                return new IdentityResult(name,IdentityResult.Outcome.POSITIVE,enumLabel(device,metadata),enumLabel(input,metadata),versionLabel(version,metadata),Set.of("CLIENT_REPORTED_METADATA"));
            } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) {
                return new IdentityResult(name,IdentityResult.Outcome.POSITIVE,"UNKNOWN","UNKNOWN","unknown",Set.of("METADATA_UNAVAILABLE"));
            }
        } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) { return IdentityResult.of(name,IdentityResult.Outcome.FAILURE); }
    }
    private static String enumLabel(Method method,Object metadata) throws ReflectiveOperationException {
        if(method==null) return "UNKNOWN"; Object value=method.invoke(metadata);
        return value instanceof Enum<?> e&&e.name().matches("[A-Z0-9_]{1,32}")?e.name():"UNKNOWN";
    }
    private static String versionLabel(Method method,Object metadata) throws ReflectiveOperationException {
        if(method==null) return "unknown"; Object value=method.invoke(metadata);
        return value instanceof String s&&s.matches("[A-Za-z0-9._-]{1,32}")?s:"unknown";
    }
}
