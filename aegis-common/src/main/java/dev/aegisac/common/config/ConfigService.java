package dev.aegisac.common.config;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
/** Serialize writers; readers observe either the entire old generation or the entire new one. */
public final class ConfigService {
    private final ConfigurationLoader loader;
    private final AtomicReference<ConfigSnapshot> current = new AtomicReference<>();
    public ConfigService(ConfigurationLoader loader) { this.loader = loader; }
    public synchronized ConfigSnapshot reload() throws IOException, ConfigurationException {
        ConfigSnapshot previous = current.get();
        ConfigSnapshot candidate = loader.load(previous == null ? 1 : previous.generation() + 1);
        validateResources(previous,candidate);
        current.set(candidate);
        return candidate;
    }
    private static void validateResources(ConfigSnapshot previous,ConfigSnapshot candidate) throws ConfigurationException {
        if(previous!=null && !previous.documents().get("config.yml").get("command-aliases").equals(candidate.documents().get("config.yml").get("command-aliases")))
            throw new ConfigurationException("config.yml","command-aliases",-1,"Alias changes require restart");
        if (previous != null && !previous.pipeline().equals(candidate.pipeline()))
            throw new ConfigurationException("performance.yml", "pipeline", -1,
                    "Pipeline resource/timing settings require a server restart; active configuration was retained");
        if (previous != null && !previous.physics().equals(candidate.physics()))
            throw new ConfigurationException("performance.yml", "physics", -1,
                    "Physics resource settings require a server restart; active configuration was retained");
        if(previous!=null && !previous.combat().sameResources(candidate.combat()))
            throw new ConfigurationException("checks/combat.yml", "resources", -1,
                    "Combat history resource settings require a server restart; active configuration was retained");
    }
    public synchronized ConfigSnapshot edit(long expectedGeneration,ConfigEdit edit) throws IOException,ConfigurationException {
        return edit(expectedGeneration,edit,()->true);
    }
    public synchronized ConfigSnapshot edit(long expectedGeneration,ConfigEdit edit,java.util.function.BooleanSupplier authorize) throws IOException,ConfigurationException {
        var previous=current();
        if(previous.generation()!=expectedGeneration) throw new IOException("Configuration generation changed; reopen the menu");
        // Whitelisted edits cannot change restart-only resources; full document validation runs in staging.
        var candidate=ConfigurationTransaction.apply(loader,previous,edit,authorize);
        current.set(candidate); return candidate;
    }
    public ConfigSnapshot current() {
        ConfigSnapshot snapshot = current.get();
        if (snapshot == null) throw new IllegalStateException("Configuration is not initialized");
        return snapshot;
    }
}
