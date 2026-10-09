package dev.aegisac.common.config;
import java.util.*;
/** Immutable output and scoring policy. Queue/resource budgets are fixed and documented. */
public record OutputSettings(Alerts alerts,Logging logging,Webhook webhook,Punishments punishments,Setbacks setbacks) {
    public record Setbacks(boolean enabled,String mode,boolean allowExperimental,boolean disableInPanic,double minimumVl,double minimumConfidence,int cooldownMillis,int maximumAgeMillis,double maximumDistance,Map<String,Boolean> checks) {
        public Setbacks { checks=Map.copyOf(checks); }
    }
    public record Alerts(boolean enabled,boolean chat,boolean console,boolean actionBar,boolean hover,boolean suggestTeleport,int cooldownMillis,String format,String hoverFormat,String server) { }
    public record Logging(boolean enabled,boolean diagnostics,boolean sqlite,boolean text,int maximumRows,int maximumFileBytes) { }
    public record Webhook(boolean enabled,String url,boolean alerts,boolean diagnostics,boolean punishments,boolean startup,boolean errors,int timeoutMillis,int retries,int intervalMillis) {
        @Override public String toString() { return "Webhook[enabled="+enabled+",url=<redacted>]"; }
    }
    public record Rule(boolean enabled,double minimumVl,double minimumCategoryRisk,double minimumTotalRisk,double minimumConfidence,int minimumFindings,double weight,double confidence,List<String> commands) {
        public Rule { commands=List.copyOf(commands); }
    }
    public record Punishments(boolean enabled,boolean allowExperimental,int windowMillis,int decayMillis,int cooldownMillis,int maximumAgeMillis,Map<String,Rule> rules) {
        public Punishments { rules=Map.copyOf(rules); }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o) { return (Map<String,Object>)o; }
    private static boolean bool(Map<String,Object> m,String k) { return (Boolean)m.get(k); }
    private static int integer(String f,Map<String,Object> m,String k,int low,int high) throws ConfigurationException {
        int n=(Integer)m.get(k); if(n<low||n>high) throw bad(f,k,"Expected integer from "+low+" to "+high); return n;
    }
    private static double number(String f,Map<String,Object> m,String k,double low,double high) throws ConfigurationException {
        double n=((Number)m.get(k)).doubleValue(); if(!Double.isFinite(n)||n<low||n>high) throw bad(f,k,"Expected finite number from "+low+" to "+high); return n;
    }
    private static String text(String f,Map<String,Object> m,String k,int max) throws ConfigurationException {
        String s=(String)m.get(k); if(s.isBlank()||s.length()>max||s.chars().anyMatch(c->c<32||c==127)) throw bad(f,k,"Expected bounded single-line text"); return s;
    }
    private static ConfigurationException bad(String f,String k,String message) { return new ConfigurationException(f,k,-1,message); }
    public static OutputSettings load(Map<String,Map<String,Object>> docs) throws ConfigurationException {
        var a=docs.get("alerts.yml"); var l=docs.get("logging.yml"); var s=docs.get("storage.yml"); var w=docs.get("webhooks.yml"); var p=docs.get("punishments.yml");
        String url=(String)w.get("url");
        if(!url.isEmpty()&&!url.matches("https://discord\\.com/api/webhooks/[0-9]{1,24}/[A-Za-z0-9_-]{20,200}")) throw bad("webhooks.yml","url","Expected an official HTTPS Discord webhook URL (value redacted)");
        if(bool(w,"enabled")&&url.isEmpty()) throw bad("webhooks.yml","url","An enabled webhook requires a URL (value redacted)");
        if(!"sqlite".equals(s.get("backend"))) throw bad("storage.yml","backend","Only sqlite is supported");
        var rules=new LinkedHashMap<String,Rule>(); var raw=map(p.get("checks"));
        for(var entry:EditionSettings.catalog().entrySet()) {
            String id=entry.getKey(); var r=map(raw.get(id)); boolean enabled=bool(r,"enabled");
            if(enabled&&!entry.getValue()) throw bad("punishments.yml","checks."+id,"Cannot punish using an unavailable model");
            Object list=r.get("commands");
            if(!(list instanceof List<?> commands)||commands.isEmpty()||commands.size()>8) throw bad("punishments.yml","checks."+id+".commands","Expected 1 to 8 commands");
            for(Object item:commands) {
                String command=(String)item;
                if(command.isBlank()||command.length()>256||command.startsWith("/")||command.chars().anyMatch(c->c<32||c==127)) throw bad("punishments.yml","checks."+id+".commands","Expected bounded console command without slash or control characters");
                String literals=command.replace("%player%","").replace("%uuid%","").replace("%check%","");
                if(literals.contains("%")) throw bad("punishments.yml","checks."+id+".commands","Only player, uuid and check placeholders are supported");
            }
            List<String> commandTexts=commands.stream().map(String.class::cast).toList();
            rules.put(id,new Rule(enabled,number("punishments.yml",r,"minimum-vl",1,10000),number("punishments.yml",r,"minimum-category-risk",0,100000),number("punishments.yml",r,"minimum-total-risk",0,100000),number("punishments.yml",r,"minimum-confidence",0,100),integer("punishments.yml",r,"minimum-findings",1,256),number("punishments.yml",r,"risk-weight",.1,100),number("punishments.yml",r,"confidence",0,100),commandTexts));
        }
        var b=docs.get("setbacks.yml"); String mode=(String)b.get("mode");
        if(!Set.of("NONE","LAST_VALID_POSITION").contains(mode)) throw bad("setbacks.yml","mode","Only NONE and verified LAST_VALID_POSITION are available; cancellation and raw position modes lack authoritative models");
        Map<String,Boolean> setbackChecks=new LinkedHashMap<>(); var selected=map(b.get("checks"));
        for(var id:dev.aegisac.common.check.CheckId.values()) {
            boolean enabled=(Boolean)selected.get(id.name());
            if(enabled&&!id.implemented) throw bad("setbacks.yml","checks."+id.name(),"Model is unavailable");
            setbackChecks.put(id.name(),enabled);
        }
        var setbacks=new Setbacks(bool(b,"enabled"),mode,bool(b,"allow-experimental"),bool(b,"disable-in-panic"),number("setbacks.yml",b,"minimum-vl",1,10000),number("setbacks.yml",b,"minimum-confidence",0,100),integer("setbacks.yml",b,"cooldown-ms",1000,60000),integer("setbacks.yml",b,"maximum-age-ms",50,1000),number("setbacks.yml",b,"maximum-distance",.1,16),setbackChecks);
        return new OutputSettings(new Alerts(bool(a,"enabled"),bool(a,"chat"),bool(a,"console"),bool(a,"action-bar"),bool(a,"hover"),bool(a,"suggest-teleport"),integer("alerts.yml",a,"cooldown-ms",100,60000),text("alerts.yml",a,"format",1024),text("alerts.yml",a,"hover-format",2048),text("alerts.yml",a,"server",64)),
                new Logging(bool(l,"enabled"),bool(l,"diagnostics"),bool(s,"enabled"),bool(l,"plain-text"),integer("storage.yml",s,"maximum-rows",100,1000000),integer("logging.yml",l,"maximum-file-bytes",4096,100000000)),
                new Webhook(bool(w,"enabled"),url,bool(w,"alerts"),bool(w,"diagnostics"),bool(w,"punishments"),bool(w,"startup"),bool(w,"errors"),integer("webhooks.yml",w,"timeout-ms",250,10000),integer("webhooks.yml",w,"retry-limit",0,3),integer("webhooks.yml",w,"minimum-interval-ms",1000,60000)),
                new Punishments(bool(p,"enabled"),bool(p,"allow-experimental"),integer("punishments.yml",p,"window-ms",1000,300000),integer("punishments.yml",p,"decay-ms",1000,300000),integer("punishments.yml",p,"cooldown-ms",1000,3600000),integer("punishments.yml",p,"maximum-evidence-age-ms",50,2000),rules),setbacks);
    }
}
