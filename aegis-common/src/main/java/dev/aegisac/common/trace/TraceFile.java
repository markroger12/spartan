package dev.aegisac.common.trace;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** A footer is mandatory: a crash/truncated file must never look like a complete recording. */
public final class TraceFile {
    public static final int MAX_RECORDS=2000, MAX_BYTES=16*1024*1024;
    private static final int MAGIC=0x41454754, VERSION=1;
    private TraceFile() { }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); }catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    public record Recording(List<TraceEntry> entries,long dropped,String reason) {
        public Recording { entries=List.copyOf(entries); }
    }
    public static final class Writer implements AutoCloseable {
        private final DataOutputStream out;
        private final MessageDigest digest=digest();
        private int count,bytes=8;
        private boolean finished;
        public Writer(Path path)throws IOException {
            out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)));
            out.writeInt(MAGIC);out.writeInt(VERSION);
        }
        public boolean append(TraceEntry entry)throws IOException {
            if(finished)throw new IOException("Trace already sealed");
            byte[] value=TraceCodec.encode(entry);
            if(count>=MAX_RECORDS||value.length+bytes+4>MAX_BYTES-512)return false;
            out.writeInt(value.length);out.write(value);digest.update(value);count++;bytes+=value.length+4;return true;
        }
        public void finish(long dropped,String reason)throws IOException {
            if(finished)return;
            if(dropped<0||reason.length()>128)throw new IOException("Invalid trace footer");
            out.writeInt(-1);out.writeInt(count);out.writeLong(dropped);out.writeUTF(reason);out.write(digest.digest());out.flush();finished=true;
        }
        @Override public void close()throws IOException { out.close(); }
    }
    public static Recording read(Path path)throws IOException {
        if(Files.size(path)>MAX_BYTES)throw new IOException("Trace file budget exceeded");
        try(var raw=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=raw.readNBytes(MAX_BYTES+1);if(bytes.length>MAX_BYTES)throw new IOException("Trace file budget exceeded");
            try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
                if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Unsupported trace format/version");
                var entries=new ArrayList<TraceEntry>();var digest=digest();long sequence=0;
                for(;;) {
                    int n=in.readInt();
                    if(n==-1) {
                        int count=in.readInt();long dropped=in.readLong();String reason=in.readUTF();byte[] hash=in.readNBytes(32);
                        if(count!=entries.size()||dropped<0||reason.length()>128||in.available()!=0||!MessageDigest.isEqual(hash,digest.digest()))throw new IOException("Invalid trace footer/checksum");
                        return new Recording(entries,dropped,reason);
                    }
                    if(n<1||n>TraceCodec.MAX_ENTRY_BYTES||entries.size()>=MAX_RECORDS)throw new IOException("Invalid trace record length/count");
                    byte[] value=in.readNBytes(n);if(value.length!=n)throw new EOFException("Truncated trace record");
                    digest.update(value);var entry=TraceCodec.decode(value);
                    if(entry.frame()==null||entry.frame().packet()==null||entry.frame().protocol()==null||entry.frame().direction()==null
                            ||entry.frame().sequence()<=sequence||entry.world()==null||entry.health()==null||entry.analysisFingerprint()==null)
                        throw new IOException("Invalid or unordered trace input");
                    sequence=entry.frame().sequence();entries.add(entry);
                }
            }
        }
    }
}
