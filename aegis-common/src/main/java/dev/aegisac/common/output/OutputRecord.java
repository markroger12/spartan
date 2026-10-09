package dev.aegisac.common.output;
import dev.aegisac.api.output.ViolationSnapshot;
import dev.aegisac.common.config.OutputSettings;
import java.util.*;
/** Durable scalar record and one-pass presentation values. MSPT is explicitly unavailable. */
public record OutputRecord(long timestamp,UUID uuid,String check,String type,long generation,Map<String,String> values) {
    public OutputRecord { values=Map.copyOf(values); }
    public static OutputRecord create(DetectionEnvelope e,ViolationSnapshot score,OutputSettings settings,String type) {
        var d=e.detection(); var v=new LinkedHashMap<String,String>();
        v.put("timestamp",Long.toString(e.timestamp())); v.put("player",clean(e.player(),64)); v.put("uuid",e.uuid().toString());
        v.put("check",d.check()); v.put("type",type); v.put("category",d.category());
        v.put("description",description(d.check())); v.put("vl",number(score.checks().getOrDefault(d.check(),0.0)));
        v.put("max_vl",number(settings.punishments().rules().get(d.check()).minimumVl())); v.put("buffer",number(d.buffer()));
        v.put("confidence",number(score.confidence())); v.put("risk",number(score.totalRisk()));
        v.put("ping",number(e.ping())); v.put("jitter",number(e.jitter())); v.put("tps",number(e.tps())); v.put("mspt","unavailable");
        v.put("client_version",clean(e.client(),64)); v.put("client_brand",clean(e.brand(),64)); v.put("bedrock",e.edition().name());
        v.put("x",e.position()?number(e.x()):"unavailable"); v.put("y",e.position()?number(e.y()):"unavailable"); v.put("z",e.position()?number(e.z()):"unavailable");
        v.put("world",clean(e.world(),64)); v.put("server",settings.alerts().server());
        v.put("observed",number(d.observed())); v.put("expected",number(d.expected())); v.put("sequence",Long.toString(d.sequence()));
        v.put("session",Long.toString(e.session())); v.put("experimental",Boolean.toString(d.experimental()));
        v.put("evidence",clean(d.reasons().stream().sorted().collect(java.util.stream.Collectors.joining(",")),1024));
        return new OutputRecord(e.timestamp(),e.uuid(),d.check(),type,d.generation(),v);
    }
    private static String description(String check) {
        try { return dev.aegisac.common.check.CheckId.valueOf(check).description; } catch(IllegalArgumentException ignored) { }
        try { return dev.aegisac.common.combat.CombatId.valueOf(check).description; } catch(IllegalArgumentException ignored) { }
        return dev.aegisac.common.guard.GuardId.valueOf(check).description;
    }
    public static String clean(String value,int length) {
        if(value==null) return "unknown";
        String safe=value.replaceAll("[\\p{Cntrl}§]",""); return safe.substring(0,Math.min(length,safe.length()));
    }
    public static String number(double n) { return Double.isFinite(n)?String.format(Locale.ROOT,"%.2f",n):"unavailable"; }
    public static String render(String template,Map<String,String> values) {
        return java.util.regex.Pattern.compile("%([a-z_]+)%").matcher(template).replaceAll(m->java.util.regex.Matcher.quoteReplacement(values.getOrDefault(m.group(1),m.group())));
    }
    public String json() {
        var all=new TreeMap<>(values); all.put("generation",Long.toString(generation));
        return all.entrySet().stream().map(e->quote(e.getKey())+":"+quote(e.getValue())).collect(java.util.stream.Collectors.joining(",","{","}"));
    }
    public static String quote(String s) {
        StringBuilder b=new StringBuilder("\"");
        for(char c:s.toCharArray()) switch(c) {
            case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
            default -> { if(c<32) b.append(String.format(Locale.ROOT,"\\u%04x",(int)c)); else b.append(c); }
        }
        return b.append('"').toString();
    }
}
