package dev.aegisac.tools.release;

import dev.aegisac.common.config.*;
import dev.aegisac.common.trace.AnalysisFingerprint;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Validates an isolated bounded copy; never migrates, creates or edits operator configuration. */
public final class ConfigurationPreflight {
    private ConfigurationPreflight() { }
    public record Report(int documents,int suppliedFiles,List<String> defaultedFiles,List<String> migratedFiles,
                         boolean alertOnly,String analysisFingerprint) {
        public Report { defaultedFiles=List.copyOf(defaultedFiles);migratedFiles=List.copyOf(migratedFiles); }
    }
    public static Report check(Path source,boolean requireAlertOnly)throws IOException,ConfigurationException {
        source=source.toAbsolutePath().normalize();
        if(!Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS))throw new IOException("Expected an existing configuration directory");
        Path stage=Files.createTempDirectory("aegis-preflight-");
        try {
            Map<String,byte[]> originals=new LinkedHashMap<>();var defaulted=new ArrayList<String>();
            for(String name:ConfigurationLoader.FILES) {
                Path file=source.resolve(name),parent=source;
                for(Path part:source.relativize(file.getParent())) { parent=parent.resolve(part);if(Files.isSymbolicLink(parent))throw new IOException("Configuration subdirectory is a symlink: "+name); }
                if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) { defaulted.add(name);continue; }
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Expected a regular configuration file: "+name);
                byte[] bytes;
                try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) { bytes=input.readNBytes(262145); }
                if(bytes.length>262144)throw new IOException("Configuration file exceeds 256 KiB: "+name);
                originals.put(name,bytes);Path target=stage.resolve(name);Files.createDirectories(target.getParent());Files.write(target,bytes);
            }
            var config=new ConfigurationLoader(stage).load(1);var migrated=new ArrayList<String>();
            for(var original:originals.entrySet())if(!Arrays.equals(original.getValue(),Files.readAllBytes(stage.resolve(original.getKey()))))migrated.add(original.getKey());
            boolean alertOnly=!config.output().punishments().enabled()&&!config.output().setbacks().enabled();
            if(requireAlertOnly&&!alertOnly)throw new IOException("Alert-only trial requires punishments.yml and setbacks.yml enabled: false");
            return new Report(config.documents().size(),originals.size(),defaulted,migrated,alertOnly,AnalysisFingerprint.of(config));
        } finally {
            try(var paths=Files.walk(stage)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path); }
        }
    }
}
