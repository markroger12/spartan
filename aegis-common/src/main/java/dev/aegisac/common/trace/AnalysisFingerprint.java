package dev.aegisac.common.trace;

import dev.aegisac.common.config.ConfigSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Hash only analysis settings; never serialize output credentials or configuration documents. */
public final class AnalysisFingerprint {
    private AnalysisFingerprint() { }
    public static String of(ConfigSnapshot c) {
        if(c==null) return "unconfigured";
        var text=new StringBuilder();
        for(Object value:List.of(c.defaultProfile(),c.worldProfiles(),c.pipeline(),c.physics(),c.movement(),
                c.combat(),c.guard(),c.edition(),c.exemptions())) append(text,value);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void append(StringBuilder b,Object value) {
        if(value instanceof Map<?,?> map) {
            b.append('{'); map.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().toString()))
                    .forEach(e->{append(b,e.getKey());append(b,e.getValue());}); b.append('}');
        } else if(value instanceof Set<?> set) {
            var values=new ArrayList<String>(); for(Object item:set) { var s=new StringBuilder();append(s,item);values.add(s.toString()); }
            Collections.sort(values);append(b,values);
        } else if(value instanceof Collection<?> list) { b.append('[');for(Object item:list)append(b,item);b.append(']');
        } else if(value!=null&&value.getClass().isRecord()) {
            b.append(value.getClass().getName()).append('(');
            try { for(var component:value.getClass().getRecordComponents())append(b,component.getAccessor().invoke(value)); }
            catch(ReflectiveOperationException error) { throw new IllegalArgumentException(error); }
            b.append(')');
        } else { String s=String.valueOf(value);b.append(s.length()).append(':').append(s); }
    }
}
