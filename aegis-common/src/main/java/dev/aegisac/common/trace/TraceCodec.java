package dev.aegisac.common.trace;

import dev.aegisac.common.packet.*;
import dev.aegisac.common.world.*;
import dev.aegisac.common.physics.*;
import dev.aegisac.common.collision.Aabb;
import dev.aegisac.api.player.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Versioned, bounded binary DTO codec. Reflection runs only on the writer/offline reader.
 * Names are resolved exclusively through this fixed allowlist, never Class.forName or Java deserialization.
 */
public final class TraceCodec {
    public static final int MAX_ENTRY_BYTES=1_048_576, MAX_STRING_BYTES=4096, MAX_ITEMS=4096;
    private static final Map<String,Class<?>> TYPES=new HashMap<>();
    static {
        register(TraceEntry.class,PacketFrame.class,ClientProtocol.class,PacketDirection.class,PacketKind.class,
                WorldView.State.class,WorldSnapshot.class,BlockSample.class,BlockSample.Surface.class,Aabb.class,
                MotionContext.class,Uncertainty.class,dev.aegisac.common.check.OwnerObservation.class,
                dev.aegisac.common.check.ServerTickHealth.Snapshot.class,
                dev.aegisac.common.combat.CombatOwnerObservation.class,dev.aegisac.common.guard.GuardOwnerObservation.class,
                dev.aegisac.common.bedrock.IdentityObservation.class,EditionSnapshot.class,BedrockStatus.class);
        register(NormalizedPacket.class.getPermittedSubclasses());
        register(NormalizedPacket.TimingKind.class,NormalizedPacket.InventoryAction.class);
    }
    private TraceCodec() { }
    private static void register(Class<?>... types) { for(Class<?> type:types)TYPES.put(type.getName(),type); }
    public static byte[] encode(TraceEntry entry) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new FilterOutputStream(bytes) {
            private int count;
            @Override public void write(int value)throws IOException { if(++count>MAX_ENTRY_BYTES)throw new IOException("Trace entry budget exceeded");out.write(value); }
            @Override public void write(byte[] b,int off,int len)throws IOException { if(len>MAX_ENTRY_BYTES-count)throw new IOException("Trace entry budget exceeded");count+=len;out.write(b,off,len); }
        })) { write(out,entry,0); }
        return bytes.toByteArray();
    }
    public static TraceEntry decode(byte[] bytes)throws IOException {
        if(bytes.length>MAX_ENTRY_BYTES)throw new IOException("Trace entry budget exceeded");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            Object value=read(in,0);if(!(value instanceof TraceEntry entry)||in.available()!=0)throw new IOException("Invalid trace entry");return entry;
        } catch(ReflectiveOperationException|IllegalArgumentException|ClassCastException error) { throw new IOException("Invalid trace DTO",error); }
    }
    private static void string(DataOutputStream out,String value)throws IOException {
        byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(bytes.length>MAX_STRING_BYTES)throw new IOException("Trace string budget exceeded");out.writeInt(bytes.length);out.write(bytes);
    }
    private static String string(DataInputStream in)throws IOException {
        int n=in.readInt();if(n<0||n>MAX_STRING_BYTES)throw new IOException("Invalid trace string length");
        byte[] bytes=in.readNBytes(n);if(bytes.length!=n)throw new EOFException();
        return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
    }
    private static void write(DataOutputStream out,Object value,int depth)throws IOException {
        if(depth>24)throw new IOException("Trace nesting budget exceeded");
        if(value==null) { out.writeByte(0);return; }
        if(value instanceof Boolean v) { out.writeByte(1);out.writeBoolean(v); }
        else if(value instanceof Integer v) { out.writeByte(2);out.writeInt(v); }
        else if(value instanceof Long v) { out.writeByte(3);out.writeLong(v); }
        else if(value instanceof Double v) { out.writeByte(4);out.writeDouble(v); }
        else if(value instanceof Float v) { out.writeByte(5);out.writeFloat(v); }
        else if(value instanceof String v) { out.writeByte(6);string(out,v); }
        else if(value instanceof UUID v) { out.writeByte(7);out.writeLong(v.getMostSignificantBits());out.writeLong(v.getLeastSignificantBits()); }
        else if(value instanceof Collection<?> v) {
            if(v.size()>MAX_ITEMS)throw new IOException("Trace collection budget exceeded");
            out.writeByte(v instanceof Set<?>?9:8);out.writeInt(v.size());for(Object item:v)write(out,item,depth+1);
        } else {
            Class<?> type=value.getClass();if(!TYPES.containsKey(type.getName()))throw new IOException("Unsupported trace DTO");
            if(value instanceof Enum<?> v) { out.writeByte(10);string(out,type.getName());string(out,v.name()); }
            else {
                out.writeByte(11);string(out,type.getName());
                try { for(var field:type.getRecordComponents())write(out,field.getAccessor().invoke(value),depth+1); }
                catch(ReflectiveOperationException error) { throw new IOException(error); }
            }
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Object read(DataInputStream in,int depth)throws IOException,ReflectiveOperationException {
        if(depth>24)throw new IOException("Trace nesting budget exceeded");
        int tag=in.readUnsignedByte();
        return switch(tag) {
            case 0->null;case 1->in.readBoolean();case 2->in.readInt();case 3->in.readLong();
            case 4->in.readDouble();case 5->in.readFloat();case 6->string(in);case 7->new UUID(in.readLong(),in.readLong());
            case 8,9->{ int n=in.readInt();if(n<0||n>MAX_ITEMS)throw new IOException("Invalid collection length");
                var values=new ArrayList<Object>(n);for(int i=0;i<n;i++)values.add(read(in,depth+1));
                yield tag==9?Set.copyOf(values):List.copyOf(values); }
            case 10->{ Class<?> type=TYPES.get(string(in));if(type==null||!type.isEnum())throw new IOException("Unknown trace enum");yield Enum.valueOf((Class)type,string(in)); }
            case 11->{ Class<?> type=TYPES.get(string(in));if(type==null||!type.isRecord())throw new IOException("Unknown trace record");
                var fields=type.getRecordComponents();var args=new Object[fields.length];var types=new Class<?>[fields.length];
                for(int i=0;i<fields.length;i++) { types[i]=fields[i].getType();args[i]=read(in,depth+1); }
                yield type.getConstructor(types).newInstance(args); }
            default->throw new IOException("Unknown trace value tag");
        };
    }
}
