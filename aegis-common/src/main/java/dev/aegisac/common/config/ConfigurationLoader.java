package dev.aegisac.common.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded, safe YAML loading. No application classes may be instantiated from YAML.
 * All documents validate before migrations are written or a snapshot is published.
 * Filesystem migration is per-file atomic; the runtime publication is all-or-nothing.
 */
public final class ConfigurationLoader {
    public static final List<String> FILES = List.of(
        "config.yml", "messages.yml", "performance.yml", "compatibility.yml",
        "checks/combat.yml", "checks/movement.yml", "checks/world.yml", "checks/player.yml",
        "checks/inventory.yml", "checks/protocol.yml", "checks/exploit.yml",
        "profiles/java.yml", "profiles/bedrock.yml", "profiles/strict.yml",
        "profiles/balanced.yml", "profiles/lenient.yml", "alerts.yml", "punishments.yml",
        "setbacks.yml", "exemptions.yml", "storage.yml", "webhooks.yml", "gui.yml", "logging.yml");
    private static final int MAX_BYTES = 262_144;
    private static final Set<String> DIFFICULTIES = Set.of("strict", "balanced", "lenient");
    private final Path directory;
    private final java.util.function.Predicate<String> material;
    public ConfigurationLoader(Path directory) { this(directory, value->true); }
    public ConfigurationLoader(Path directory,java.util.function.Predicate<String> material) { this.directory=directory.toAbsolutePath().normalize(); this.material=material; }
    Path directory() { return directory; }
    ConfigurationLoader staged(Path path) { return new ConfigurationLoader(path,material); }

    public ConfigSnapshot load(long generation) throws IOException, ConfigurationException {
        Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
        List<Migration> migrations = new ArrayList<>();
        for (String file : FILES) {
            byte[] defaults = resource(file);
            Path path = safePath(file);
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                // CREATE_NEW preserves concurrent administrator creation, unlike REPLACE_EXISTING.
                try { Files.write(path, defaults, StandardOpenOption.CREATE_NEW); }
                catch (java.nio.file.FileAlreadyExistsException concurrentCreation) {
                    // The administrator's file is parsed below and never overwritten here.
                }
            }
            byte[] original;
            try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                original = input.readNBytes(MAX_BYTES + 1);
            }
            if (original.length > MAX_BYTES) throw error(file, "$", "File exceeds 256 KiB limit");
            Map<String, Object> raw = parse(file, original);
            int version = version(file, raw);
            if (version == 0) migrateZero(file, raw);
            Map<String, Object> baseline = parse(file, defaults);
            Map<String, Object> merged = merge(file, "", baseline, raw);
            validate(file, merged);
            documents.put(file, freeze(merged));
            if (version == 0) migrations.add(new Migration(path, original, merged));
        }
        Map<String, Object> root = documents.get("config.yml");
        Map<String, String> worlds = strings(map(root.get("world-profiles")));
        String difficulty = (String) root.get("default-profile");
        Map<String, String> messages = strings(map(documents.get("messages.yml").get("messages")));
        boolean metrics = (Boolean) documents.get("performance.yml").get("packet-metrics");
        Map<String, Object> pipeline = map(documents.get("performance.yml").get("pipeline"));
        PipelineSettings settings = new PipelineSettings(
                (Integer)pipeline.get("workers"), (Integer)pipeline.get("executor-capacity"),
                (Integer)pipeline.get("session-capacity"), (Integer)pipeline.get("batch-size"),
                (Integer)pipeline.get("maximum-packet-bytes"), (Integer)pipeline.get("pending-transactions"),
                (Integer)pipeline.get("history-capacity"), (Integer)pipeline.get("transaction-timeout-ms"),
                (Integer)pipeline.get("stall-ms"), (Integer)pipeline.get("uncertainty-ms"),
                ((Number)pipeline.get("smoothing")).doubleValue(), (Boolean)pipeline.get("active-probes"),
                (Integer)pipeline.get("probe-interval-ticks"));
        Map<String,Object> physics = map(documents.get("performance.yml").get("physics"));
        PhysicsSettings simulation = new PhysicsSettings((Boolean)physics.get("enabled"),
                (Integer)physics.get("captures-per-tick"), (Integer)physics.get("radius"),
                (Integer)physics.get("maximum-blocks"), (Integer)physics.get("maximum-boxes"),
                (Integer)physics.get("maximum-entities"), (Integer)physics.get("maximum-age-ms"));
        MovementSettings movement = movement(documents.get("checks/movement.yml"));
        Map<String,Object> exempt = documents.get("exemptions.yml");
        Set<String> exemptWorlds = map(exempt.get("worlds")).entrySet().stream().filter(e->Boolean.TRUE.equals(e.getValue()))
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
        ExemptionSettings exemptions = new ExemptionSettings((Boolean)exempt.get("enabled"),(Boolean)exempt.get("permission-bypass"),
                (Boolean)exempt.get("creative-or-spectator"),(Boolean)exempt.get("flight"),exemptWorlds);
        ConfigSnapshot candidate = new ConfigSnapshot(generation, difficulty, worlds, metrics, settings, simulation, movement, combat(documents.get("checks/combat.yml")), guard(documents), edition(documents), OutputSettings.load(documents), exemptions, messages, documents);
        for (Migration migration : migrations) writeMigration(migration);
        return candidate;
    }

    Path safePath(String file) throws IOException {
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) throw new IOException("Configuration directory must not be a symbolic link");
        Path result = directory.resolve(file);
        Path relativeParent = directory.relativize(result.getParent());
        Path cursor = directory;
        for (Path part : relativeParent) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw new IOException("Configuration subdirectory must not be a symbolic link");
            Files.createDirectories(cursor);
        }
        if (Files.isSymbolicLink(result)) throw new IOException("Configuration file must not be a symbolic link: " + file);
        return result;
    }
    private static byte[] resource(String file) throws IOException {
        try (InputStream in = ConfigurationLoader.class.getResourceAsStream("/defaults/" + file)) {
            if (in == null) throw new IOException("Missing bundled configuration: " + file);
            return in.readAllBytes();
        }
    }
    private static Map<String, Object> parse(String file, byte[] bytes) throws ConfigurationException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(20);
        options.setCodePointLimit(MAX_BYTES);
        try {
            Object value = new Yaml(new SafeConstructor(options)).load(new String(bytes, StandardCharsets.UTF_8));
            if (!(value instanceof Map<?, ?> raw)) throw error(file, "$", "Expected a YAML mapping");
            return normalize(file, "$", raw);
        } catch (MarkedYAMLException malformed) {
            int line = malformed.getProblemMark() == null ? -1 : malformed.getProblemMark().getLine() + 1;
            throw new ConfigurationException(file, "$", line, "Malformed YAML, duplicate key or unsafe YAML tag");
        } catch (YAMLException malformed) {
            throw error(file, "$", "Invalid YAML (aliases, excessive nesting and unsafe tags are prohibited)");
        }
    }
    private static Map<String, Object> normalize(String file, String path, Map<?, ?> raw) throws ConfigurationException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw error(file, path, "Expected string mapping keys");
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) value = normalize(file, path + "." + key, nested);
            else if (value instanceof List<?> list) {
                if(!(file.equals("gui.yml") && list.size()<=54 && list.stream().allMatch(v->v instanceof String||v instanceof Integer)) && (!file.equals("punishments.yml")||!key.equals("commands")||list.size()>8||list.stream().anyMatch(v->!(v instanceof String))))
                    throw error(file,path+"."+key,"Only bounded punishment command string lists are supported");
                value=List.copyOf(list);
            }
            else if (!(value instanceof String || value instanceof Number || value instanceof Boolean))
                throw error(file, path + "." + key, "Expected scalar or mapping");
            result.put(key, value);
        }
        return result;
    }
    private static int version(String file, Map<String, Object> raw) throws ConfigurationException {
        Object version = raw.get("config-version");
        if (!(version instanceof Integer number) || number < 0 || number > 1)
            throw error(file, "config-version", "Expected supported integer version 0 or 1; newer files cannot be downgraded");
        return (Integer) version;
    }
    private static void migrateZero(String file, Map<String, Object> raw) throws ConfigurationException {
        if (file.equals("config.yml") && raw.containsKey("profile")) {
            if (raw.containsKey("default-profile")) throw error(file, "profile", "Both old and new profile keys exist; resolve the ambiguity");
            raw.put("default-profile", raw.remove("profile"));
        }
        raw.put("config-version", 1);
    }
    private static Map<String, Object> merge(String file, String path, Map<String, Object> defaults,
                                             Map<String, Object> supplied) throws ConfigurationException {
        Map<String, Object> result = new LinkedHashMap<>(defaults);
        for (Map.Entry<String, Object> entry : supplied.entrySet()) {
            String key = entry.getKey();
            String childPath = path.isEmpty() ? key : path + "." + key;
            if (!defaults.containsKey(key)) throw error(file, childPath, "Unknown configuration key");
            Object baseline = defaults.get(key);
            Object value = entry.getValue();
            if (baseline instanceof Map<?, ?>) {
                if (!(value instanceof Map<?, ?>)) throw error(file, childPath, "Expected a mapping");
                value = (file.equals("config.yml") && (childPath.equals("world-profiles")||childPath.equals("command-aliases")) || file.equals("exemptions.yml") && childPath.equals("worlds"))
                        ? value : merge(file, childPath, map(baseline), map(value));
            } else if (baseline instanceof List<?> && value instanceof List<?>) {
                value=List.copyOf((List<?>)value);
            } else if (baseline instanceof Double && value instanceof Number number) {
                value = number.doubleValue();
            } else if (!baseline.getClass().isInstance(value)) {
                throw error(file, childPath, "Expected " + baseline.getClass().getSimpleName());
            }
            result.put(key, value);
        }
        return result;
    }
    private void validate(String file, Map<String, Object> doc) throws ConfigurationException {
        if (file.equals("config.yml")) {
            var aliases=map(doc.get("command-aliases"));
            if(aliases.size()>8) throw error(file,"command-aliases","At most 8 aliases");
            for(var alias:aliases.entrySet()) if(!alias.getKey().matches("[a-z][a-z0-9_-]{0,23}")||Set.of("ac","aegisac").contains(alias.getKey())||!(alias.getValue() instanceof Boolean)) throw error(file,"command-aliases","Expected additional lowercase aliases mapped to booleans");
            if (!DIFFICULTIES.contains(doc.get("default-profile")))
                throw error(file, "default-profile", "Expected strict, balanced or lenient");
            for (Map.Entry<String, Object> world : map(doc.get("world-profiles")).entrySet()) {
                if (!(world.getValue() instanceof String profile) || !DIFFICULTIES.contains(profile))
                    throw error(file, "world-profiles." + world.getKey(), "Expected strict, balanced or lenient");
            }
        } else if (file.equals("gui.yml")) {
            GuiSettings.load(doc,material);
        } else if (file.equals("messages.yml")) {
            for (Map.Entry<String, Object> message : map(doc.get("messages")).entrySet()) {
                String text = (String) message.getValue();
                if (text.isBlank() || text.length() > 2048)
                    throw error(file, "messages." + message.getKey(), "Expected nonblank text of at most 2048 characters");
            }
        } else if (file.equals("performance.yml")) {
            Map<String,Object> physics = map(doc.get("physics"));
            physicsBound(physics,"captures-per-tick",1,16);
            physicsBound(physics,"radius",1,4);
            physicsBound(physics,"maximum-blocks",32,4096);
            physicsBound(physics,"maximum-boxes",32,4096);
            physicsBound(physics,"maximum-entities",1,64);
            physicsBound(physics,"maximum-age-ms",50,2000);
            Map<String, Object> pipeline = map(doc.get("pipeline"));
            bounded(file, pipeline, "workers", 1, 8);
            bounded(file, pipeline, "executor-capacity", 1, 16384);
            bounded(file, pipeline, "session-capacity", 8, 4096);
            bounded(file, pipeline, "batch-size", 1, 256);
            bounded(file, pipeline, "maximum-packet-bytes", 256, 65536);
            bounded(file, pipeline, "pending-transactions", 4, 1024);
            bounded(file, pipeline, "history-capacity", 1, 256);
            bounded(file, pipeline, "transaction-timeout-ms", 1000, 120000);
            bounded(file, pipeline, "stall-ms", 50, 10000);
            bounded(file, pipeline, "uncertainty-ms", 100, 60000);
            bounded(file, pipeline, "probe-interval-ticks", 10, 1200);
            double smoothing = ((Number)pipeline.get("smoothing")).doubleValue();
            if (!Double.isFinite(smoothing) || smoothing <= 0 || smoothing > 1)
                throw error(file, "pipeline.smoothing", "Expected finite decimal greater than 0 and at most 1");
        } else if (file.equals("checks/movement.yml")) {
            movement(doc);
        } else if (file.equals("checks/combat.yml")) {
            combat(doc);
        } else if (GuardSettings.CATEGORIES.stream().anyMatch(c->file.equals("checks/"+c+".yml"))) {
            guardCategory(file,doc); guardRules(file,doc);
        } else if (Set.of("alerts.yml","logging.yml","storage.yml","webhooks.yml","punishments.yml","setbacks.yml").contains(file)) {
            // Cross-document output policy is validated before publication below.
        } else if (file.equals("exemptions.yml")) {
            for(var entry:map(doc.get("worlds")).entrySet())
                if(!(entry.getValue() instanceof Boolean)) throw error(file,"worlds."+entry.getKey(),"Expected boolean");
        } else if (file.equals("profiles/java.yml")) {
            javaProfile(doc);
        } else if (file.equals("profiles/bedrock.yml")) {
            bedrockProfile(doc);
        } else if (file.equals("compatibility.yml")) {
            identitySettings(doc);
            if (!"bedrock".equals(doc.get("unknown-identity-profile")))
                throw error(file, "unknown-identity-profile", "Unknown identities require the conservative bedrock profile");
        } else if (file.startsWith("profiles/")) {
            if (!"observe-only".equals(doc.get("mode")))
                throw error(file, "mode", "Only observe-only is supported");
        } else if (!file.equals("performance.yml") && !Boolean.FALSE.equals(doc.get("enabled"))) {
            throw error(file, "enabled", "This subsystem is scheduled for a later phase and must remain disabled");
        }
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    private static void bounded(String file, Map<String, Object> values, String key, int minimum, int maximum)
            throws ConfigurationException {
        int value = (Integer)values.get(key);
        if (value < minimum || value > maximum)
            throw error(file, "pipeline." + key, "Expected integer from " + minimum + " to " + maximum);
    }
    private static EditionSettings edition(Map<String,Map<String,Object>> documents) throws ConfigurationException {
        var java=javaProfile(documents.get("profiles/java.yml")); var bedrock=documents.get("profiles/bedrock.yml");
        return new EditionSettings(identitySettings(documents.get("compatibility.yml")),(Boolean)bedrock.get("capture-translated-history"),
                integer(bedrock,"history-size",1,64,"profiles/bedrock.yml",""),integer(bedrock,"history-max-age-ms",100,30000,"profiles/bedrock.yml",""),
                java,bedrockProfile(bedrock));
    }
    private static EditionSettings.Identity identitySettings(Map<String,Object> doc) throws ConfigurationException {
        String file="compatibility.yml"; var values=map(doc.get("identity"));
        int poll=integer(values,"poll-interval-ms",50,10000,file,"identity.");
        int age=integer(values,"maximum-age-ms",100,30000,file,"identity.");
        if(age<poll) throw error(file,"identity.maximum-age-ms","Must be at least poll-interval-ms");
        return new EditionSettings.Identity((Boolean)values.get("floodgate"),(Boolean)values.get("geyser"),poll,
                integer(values,"queries-per-tick",1,128,file,"identity."),age,(Boolean)values.get("negative-results-authoritative"),(Boolean)values.get("java-only-server"));
    }
    private static Map<String,Boolean> javaProfile(Map<String,Object> doc) throws ConfigurationException {
        String file="profiles/java.yml"; if(!"observe-only".equals(doc.get("mode"))) throw error(file,"mode","Only observe-only is supported");
        var result=new java.util.LinkedHashMap<String,Boolean>(); var checks=map(doc.get("checks"));
        for(var entry:EditionSettings.catalog().entrySet()) {
            boolean enabled=(Boolean)checks.get(entry.getKey());
            if(enabled&&!entry.getValue()) throw error(file,"checks."+entry.getKey(),"Model is unavailable");
            result.put(entry.getKey(),enabled);
        }
        return Map.copyOf(result);
    }
    private static Map<String,EditionSettings.Rule> bedrockProfile(Map<String,Object> doc) throws ConfigurationException {
        String file="profiles/bedrock.yml"; if(!"observe-only".equals(doc.get("mode"))) throw error(file,"mode","Only observe-only is supported");
        integer(doc,"history-size",1,64,file,""); integer(doc,"history-max-age-ms",100,30000,file,"");
        var result=new java.util.LinkedHashMap<String,EditionSettings.Rule>(); var checks=map(doc.get("checks"));
        for(var entry:EditionSettings.catalog().entrySet()) {
            var value=map(checks.get(entry.getKey())); String path="checks."+entry.getKey()+".";
            EditionSettings.Mode mode;
            try { mode=EditionSettings.Mode.valueOf((String)value.get("mode")); }
            catch(IllegalArgumentException bad) { throw error(file,path+"mode","Expected BEDROCK_SUPPORTED, BEDROCK_ADJUSTED or BEDROCK_DISABLED"); }
            if(!entry.getValue()&&mode!=EditionSettings.Mode.BEDROCK_DISABLED) throw error(file,path+"mode","Model is unavailable");
            double multiplier=decimal(value,"sensitivity-multiplier",.1,10,file,path),extra=decimal(value,"extra-tolerance",0,10000,file,path);
            if(mode==EditionSettings.Mode.BEDROCK_SUPPORTED&&(multiplier!=1||extra!=0)) throw error(file,path+"mode","Use BEDROCK_ADJUSTED for threshold changes");
            result.put(entry.getKey(),new EditionSettings.Rule(mode,multiplier,extra));
        }
        return Map.copyOf(result);
    }
    private static GuardSettings guard(Map<String,Map<String,Object>> documents) throws ConfigurationException {
        var categories=new java.util.LinkedHashMap<String,GuardSettings.Category>();
        var rules=new java.util.EnumMap<dev.aegisac.common.guard.GuardId,GuardSettings.Rule>(dev.aegisac.common.guard.GuardId.class);
        for(String category:GuardSettings.CATEGORIES) {
            String file="checks/"+category+".yml"; var doc=documents.get(file);
            categories.put(category,guardCategory(file,doc)); rules.putAll(guardRules(file,doc));
        }
        return new GuardSettings(categories,rules);
    }
    private static GuardSettings.Category guardCategory(String file,Map<String,Object> doc) throws ConfigurationException {
        return new GuardSettings.Category((Boolean)doc.get("enabled"),integer(doc,"evidence-capacity",1,256,file,""),
                integer(doc,"grace-ms",0,60000,file,""),integer(doc,"maximum-delay-ms",50,2000,file,""));
    }
    private static Map<dev.aegisac.common.guard.GuardId,GuardSettings.Rule> guardRules(String file,Map<String,Object> doc) throws ConfigurationException {
        var result=new java.util.EnumMap<dev.aegisac.common.guard.GuardId,GuardSettings.Rule>(dev.aegisac.common.guard.GuardId.class);
        var raw=map(doc.get("checks"));
        for(var id:dev.aegisac.common.guard.GuardId.values()) {
            if(!file.equals("checks/"+id.category+".yml")) continue;
            var rule=map(raw.get(id.name())); String path="checks."+id.name()+".";
            boolean enabled=(Boolean)rule.get("enabled");
            if(enabled&&!id.implemented) throw error(file,path+"enabled",id.description+"; this model must remain disabled");
            String bedrock=(String)rule.get("bedrock-mode");
            if(!Set.of("disabled","diagnostic").contains(bedrock)) throw error(file,path+"bedrock-mode","Expected disabled or diagnostic");
            result.put(id,new GuardSettings.Rule(enabled,decimal(rule,"limit",0,1000000,file,path),
                    integer(rule,"cooldown-ms",1,60000,file,path),bedrock));
        }
        return result;
    }
    private static CombatSettings combat(Map<String,Object> doc) throws ConfigurationException {
        String file="checks/combat.yml";
        var rules=new java.util.EnumMap<dev.aegisac.common.combat.CombatId,MovementSettings.Rule>(dev.aegisac.common.combat.CombatId.class);
        var raw=map(doc.get("checks"));
        for(var id:dev.aegisac.common.combat.CombatId.values()) {
            var rule=map(raw.get(id.name())); String path="checks."+id.name()+".";
            String bedrock=(String)rule.get("bedrock-mode");
            if(!Set.of("disabled","diagnostic").contains(bedrock)) throw error(file,path+"bedrock-mode","Expected disabled or diagnostic");
            rules.put(id,new MovementSettings.Rule((Boolean)rule.get("enabled"),
                    decimal(rule,"tolerance",0,10000,file,path),decimal(rule,"increment",.01,100,file,path),
                    decimal(rule,"decay-per-second",0,100,file,path),decimal(rule,"buffer-threshold",.01,1000,file,path),
                    integer(rule,"minimum-samples",1,1000,file,path),integer(rule,"cooldown-ms",1,60000,file,path),bedrock));
        }
        if((Integer)doc.get("minimum-statistics")>(Integer)doc.get("statistics-size"))
            throw error(file,"minimum-statistics","Must not exceed statistics-size");
        return new CombatSettings((Boolean)doc.get("enabled"),
                integer(doc,"maximum-targets",1,256,file,""),
                integer(doc,"history-size",2,32,file,""),
                integer(doc,"pending-attacks",4,256,file,""),
                integer(doc,"statistics-size",4,256,file,""),
                integer(doc,"evidence-capacity",1,256,file,""),
                integer(doc,"history-ms",100,10000,file,""),
                integer(doc,"interpolation-ms",1,1000,file,""),
                integer(doc,"swing-window-ms",1,2000,file,""),
                integer(doc,"target-window-ms",1,1000,file,""),
                integer(doc,"minimum-statistics",4,256,file,""),
                integer(doc,"grace-ms",0,60000,file,""),
                integer(doc,"maximum-delay-ms",50,2000,file,""),
                integer(doc,"velocity-window-ms",50,2000,file,""),
                decimal(doc,"box-expansion",0,1,file,""),
                decimal(doc,"click-variation",0,1,file,""),
                decimal(doc,"repeated-intervals",0.5,1,file,""),
                decimal(doc,"snap-degrees",1,180,file,""),
                decimal(doc,"alignment-degrees",0.01,45,file,""),
                decimal(doc,"rotation-variation",0,1,file,""),
                decimal(doc,"tiny-fall",0.001,0.1,file,""),
                decimal(doc,"velocity-fraction",0,1,file,""),rules);
    }
    private static MovementSettings movement(Map<String,Object> doc) throws ConfigurationException {
        String file="checks/movement.yml";
        Map<String,Object> grace=map(doc.get("grace")), health=map(doc.get("health")), safe=map(doc.get("safe-position")), timing=map(doc.get("timing"));
        var rules=new java.util.EnumMap<dev.aegisac.common.check.CheckId,MovementSettings.Rule>(dev.aegisac.common.check.CheckId.class);
        var raw=map(doc.get("checks"));
        for(var id:dev.aegisac.common.check.CheckId.values()) {
            var rule=map(raw.get(id.name())); String path="checks."+id.name()+".";
            boolean enabled=(Boolean)rule.get("enabled");
            if(enabled && !id.implemented) throw error(file,path+"enabled",id.description+"; this check must remain disabled");
            String bedrock=(String)rule.get("bedrock-mode");
            if(!Set.of("disabled","diagnostic").contains(bedrock)) throw error(file,path+"bedrock-mode","Expected disabled or diagnostic; adjusted enforcement is not validated");
            rules.put(id,new MovementSettings.Rule(enabled,
                    decimal(rule,"tolerance",0,10000,file,path),decimal(rule,"increment",.01,100,file,path),
                    decimal(rule,"decay-per-second",0,100,file,path),decimal(rule,"buffer-threshold",.01,1000,file,path),
                    integer(rule,"minimum-samples",1,1000,file,path),integer(rule,"cooldown-ms",1,60000,file,path),bedrock));
        }
        return new MovementSettings((Boolean)doc.get("enabled"),integer(doc,"evidence-capacity",1,256,file,""),
                integer(grace,"join-ms",0,60000,file,"grace."),integer(grace,"teleport-ms",0,60000,file,"grace."),
                integer(grace,"velocity-ms",0,60000,file,"grace."),integer(grace,"lag-ms",0,60000,file,"grace."),
                integer(health,"maximum-delay-ms",50,5000,file,"health."),integer(health,"maximum-jitter-ms",1,5000,file,"health."),
                integer(safe,"minimum-samples",2,200,file,"safe-position."),integer(safe,"maximum-age-ms",50,10000,file,"safe-position."),
                new MovementSettings.Timing(integer(timing,"window-ms",1000,10000,file,"timing."),
                        integer(timing,"nominal-tick-ms",50,50,file,"timing."),integer(timing,"bank-ms",0,5000,file,"timing."),
                        integer(timing,"silence-ms",250,10000,file,"timing."),integer(timing,"burst-ms",50,1000,file,"timing."),
                        integer(timing,"burst-packets",3,100,file,"timing.")),rules);
    }
    private static int integer(Map<String,Object> doc,String key,int min,int max,String file,String prefix) throws ConfigurationException {
        int value=(Integer)doc.get(key);
        if(value<min || value>max) throw error(file,prefix+key,"Expected integer from "+min+" to "+max);
        return value;
    }
    private static double decimal(Map<String,Object> doc,String key,double min,double max,String file,String prefix) throws ConfigurationException {
        double value=((Number)doc.get(key)).doubleValue();
        if(!Double.isFinite(value) || value<min || value>max) throw error(file,prefix+key,"Expected finite decimal from "+min+" to "+max);
        return value;
    }
    private static void physicsBound(Map<String,Object> values, String key, int min, int max) throws ConfigurationException {
        int value=(Integer)values.get(key);
        if(value<min || value>max) throw error("performance.yml","physics."+key,"Expected integer from "+min+" to "+max);
    }
    private static Map<String, String> strings(Map<String, Object> source) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, (String) value));
        return Map.copyOf(result);
    }
    private static Map<String, Object> freeze(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, value instanceof Map<?, ?> ? freeze(map(value)) : value instanceof List<?> list ? List.copyOf(list) : value));
        return Collections.unmodifiableMap(result);
    }
    private static void writeMigration(Migration migration) throws IOException {
        // Do not clobber an administrator edit made while validation was in progress.
        if (!java.util.Arrays.equals(Files.readAllBytes(migration.path()), migration.original()))
            throw new IOException("Configuration changed during migration: " + migration.path().getFileName());
        Path parent = migration.path().getParent();
        Path backup = Files.createTempFile(parent, migration.path().getFileName() + ".v0-", ".bak");
        Files.write(backup, migration.original());
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Path temporary = Files.createTempFile(parent, ".aegis-migration-", ".tmp");
        try {
            Files.writeString(temporary, "# Migrated by AegisAC; original comments are preserved in the adjacent backup.\n"
                    + new Yaml(options).dump(migration.document()), StandardCharsets.UTF_8);
            Files.move(temporary, migration.path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static ConfigurationException error(String file, String path, String reason) {
        return new ConfigurationException(file, path, -1, reason);
    }
    private record Migration(Path path, byte[] original, Map<String, Object> document) { }
}
