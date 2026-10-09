package dev.aegisac.common.config;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.*;
/** Stages and validates all documents; atomically replaces exactly one file with an exact-byte backup. */
final class ConfigurationTransaction {
    static ConfigSnapshot apply(ConfigurationLoader loader,ConfigSnapshot current,ConfigEdit edit,
            java.util.function.BooleanSupplier authorize) throws IOException,ConfigurationException {
        edit.validate(); Path stage=Files.createTempDirectory("aegis-config-");
        try {
            var originals=new LinkedHashMap<String,byte[]>();
            for(String file:ConfigurationLoader.FILES) {
                Path source=loader.safePath(file); byte[] bytes;
                try(var input=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS)) { bytes=input.readNBytes(262145); }
                if(bytes.length>262144) throw new IOException("Configuration exceeds size limit");
                originals.put(file,bytes); Path copy=stage.resolve(file); Files.createDirectories(copy.getParent()); Files.write(copy,bytes);
            }
            var staged=loader.staged(stage);
            if(!staged.load(current.generation()).documents().equals(current.documents())) throw new IOException("Configuration changed on disk; reload before editing");
            var document=mutable(current.documents().get(edit.file())); Map<String,Object> cursor=document;
            for(int n=0;n<edit.path().size()-1;n++) cursor=map(cursor.get(edit.path().get(n)));
            cursor.put(edit.path().getLast(),edit.value());
            var options=new DumperOptions(); options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            String yaml="# Edited by AegisAC; previous text/comments preserved in .last-admin-edit.bak\n"+new Yaml(options).dump(document);
            Files.writeString(stage.resolve(edit.file()),yaml);
            var candidate=staged.load(current.generation()+1);
            if(!authorize.getAsBoolean()||Thread.currentThread().isInterrupted()) throw new IOException("Edit authorization expired");
            for(var e:originals.entrySet()) {
                try(var in=Files.newInputStream(loader.safePath(e.getKey()),LinkOption.NOFOLLOW_LINKS)) {
                    if(!Arrays.equals(in.readNBytes(262145),e.getValue())) throw new IOException("Configuration changed during edit; reload first");
                }
            }
            Path target=loader.safePath(edit.file()),backup=target.resolveSibling(target.getFileName()+".last-admin-edit.bak");
            if(Files.isSymbolicLink(backup)) throw new IOException("Backup must not be a symbolic link");
            Path temp=Files.createTempFile(target.getParent(),".aegis-edit-",".tmp"),saved=Files.createTempFile(target.getParent(),".aegis-backup-",".tmp");
            try {
                Files.writeString(temp,yaml); Files.write(saved,originals.get(edit.file()));
                Files.move(saved,backup,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); Files.deleteIfExists(saved); }
            return candidate;
        } finally {
            try(var paths=Files.walk(stage)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } catch(IOException cleanupFailure) { /* Temporary staging cleanup cannot undo a committed publication. */ }
        }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o) { return (Map<String,Object>)o; }
    private static Map<String,Object> mutable(Map<String,Object> source) {
        var result=new LinkedHashMap<String,Object>(); source.forEach((k,v)->result.put(k,v instanceof Map<?,?>?mutable(map(v)):v)); return result;
    }
}
