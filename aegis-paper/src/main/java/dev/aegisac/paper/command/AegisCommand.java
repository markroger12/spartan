package dev.aegisac.paper.command;
import dev.aegisac.paper.AegisPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("deprecation") // Use the Bukkit metadata API for Spigot compatibility.
public final class AegisCommand implements CommandExecutor, TabCompleter {
    private static final List<String> COMMANDS = List.of("help", "version", "reload", "profile", "connection", "physics", "checks", "movement", "safeposition", "combat", "cps", "inspect", "edition", "bedrock", "alerts", "verbose", "violations", "logs", "panic", "output", "performance", "debug", "toggle", "reset", "exempt", "bypass", "freeze", "unfreeze", "top", "gui");
    private final AegisPlugin plugin;
    public AegisCommand(AegisPlugin plugin) { this.plugin = plugin; }
    private boolean allowed(CommandSender sender, String action) {
        return dev.aegisac.paper.admin.AdminService.allowed(sender,action);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!COMMANDS.contains(action)) { plugin.reply(sender, "unknown-command", Map.of()); return true; }
        if (!allowed(sender, action)) { plugin.reply(sender, "denied", Map.of()); return true; }
        switch (action) {
            case "help" -> {
                plugin.reply(sender,"help",Map.of());
                plugin.reply(sender,"admin-help",Map.of("commands",COMMANDS.stream().filter(a->allowed(sender,a)).collect(java.util.stream.Collectors.joining(", "))));
            }
            case "debug", "toggle", "reset", "exempt", "bypass", "freeze", "unfreeze", "top", "gui" -> staff(sender,action,args);
            case "version" -> plugin.reply(sender, "version", Map.of("version", plugin.getDescription().getVersion()));
            case "reload" -> plugin.reload(sender);
            case "profile" -> {
                if (args.length != 2) { plugin.reply(sender, "profile-usage", Map.of()); break; }
                var target = plugin.admin().target(sender,args[1]);
                var snapshot = target == null ? null : plugin.players().snapshot(target.getUniqueId()).orElse(null);
                if (snapshot == null) { plugin.reply(sender, "player-missing", Map.of()); break; }
                dev.aegisac.paper.admin.ProfileView.values(plugin,target).forEach((key,value)->plugin.reply(sender,"profile-detail",Map.of("label",key,"value",value)));
            }
            case "edition", "bedrock" -> {
                if(args.length!=2) { plugin.reply(sender,"movement-usage",Map.of("command",action)); break; }
                var target=plugin.admin().target(sender,args[1]);
                var snapshot=target==null?null:plugin.players().snapshot(target.getUniqueId()).orElse(null);
                if(snapshot==null) { plugin.reply(sender,"player-missing",Map.of()); break; }
                if(action.equals("edition")) {
                    var identity=snapshot.edition();
                    plugin.reply(sender,"edition-summary",Map.of("player",snapshot.name(),"edition",identity.status().name(),
                            "source",identity.source(),"device",identity.device(),"input",identity.input(),"version",identity.version(),
                            "reasons",identity.reasons().stream().sorted().collect(java.util.stream.Collectors.joining(","))));
                } else {
                    var history=snapshot.bedrockAnalysis();
                    String counts=history.histories().entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .map(e->e.getKey()+"="+e.getValue().size()).collect(java.util.stream.Collectors.joining(", "));
                    plugin.reply(sender,"bedrock-summary",Map.of("player",snapshot.name(),"status",history.status(),"histories",counts,
                            "reasons",history.reasons().stream().sorted().collect(java.util.stream.Collectors.joining(","))));
                }
            }
            case "connection" -> {
                if (args.length != 2) { plugin.reply(sender, "connection-usage", Map.of()); break; }
                var target = plugin.admin().target(sender,args[1]);
                var snapshot = target == null ? null : plugin.players().snapshot(target.getUniqueId()).orElse(null);
                if (snapshot == null || snapshot.connection() == null) { plugin.reply(sender, "player-missing", Map.of()); break; }
                var connection = snapshot.connection(); var timing = connection.timing();
                plugin.reply(sender, "connection", Map.of("player", snapshot.name(),
                        "transaction_rtt", String.format(Locale.ROOT, "%.1f", timing.transactionRttMillis()),
                        "keepalive_rtt", String.format(Locale.ROOT, "%.1f", timing.keepAliveRttMillis()),
                        "jitter", String.format(Locale.ROOT, "%.1f", timing.jitterMillis()),
                        "pending", Integer.toString(timing.pending()), "uncertain", Boolean.toString(connection.uncertain()),
                        "loss_epoch", Long.toString(connection.lossEpoch()), "brand", snapshot.client().brand()));
            }
            case "physics" -> {
                if (args.length != 2) { plugin.reply(sender, "physics-usage", Map.of()); break; }
                var target = plugin.admin().target(sender,args[1]);
                var snapshot = target == null ? null : plugin.players().snapshot(target.getUniqueId()).orElse(null);
                if (snapshot == null) { plugin.reply(sender, "player-missing", Map.of()); break; }
                var result = snapshot.physics();
                plugin.reply(sender, "physics", Map.of("player", snapshot.name(), "model", result.profile(),
                        "candidates", Integer.toString(result.candidates()),
                        "residual", String.format(Locale.ROOT, "%.5f", result.residual()),
                        "uncertainty", result.uncertainty().stream().sorted().collect(java.util.stream.Collectors.joining(","))));
            }
            case "checks" -> {
                String category=args.length==1?"all":args[1].toLowerCase(Locale.ROOT);
                if(args.length>2||!List.of("all","movement","combat","world","player","inventory","protocol","exploit").contains(category)) { plugin.reply(sender,"admin-result",Map.of("result","Usage: /ac checks [all|movement|combat|world|player|inventory|protocol|exploit]")); break; }
                var settings=plugin.configuration().current().movement();
                for(var id:dev.aegisac.common.check.CheckId.values()) {
                    if(!category.equals("all")&&!category.equals("movement")) continue;
                    var rule=settings.rules().get(id);
                    String status=!id.implemented?"UNAVAILABLE":settings.enabled() && rule.enabled()?"EXPERIMENTAL":"DISABLED";
                    plugin.reply(sender,"check-entry",Map.of("check",id.name(),"status",status,"description",id.description,"bedrock",plugin.configuration().current().edition().bedrockChecks().get(id.name()).mode().name()+"/"+rule.bedrockMode()));
                }
                var combat=plugin.configuration().current().combat();
                for(var id:dev.aegisac.common.combat.CombatId.values()) {
                    if(!category.equals("all")&&!category.equals("combat")) continue;
                    var rule=combat.rules().get(id);
                    plugin.reply(sender,"check-entry",Map.of("check",id.name(),"status",combat.enabled()&&rule.enabled()?"EXPERIMENTAL":"DISABLED",
                            "description",id.description,"bedrock",plugin.configuration().current().edition().bedrockChecks().get(id.name()).mode().name()+"/"+rule.bedrockMode()));
                }
                var guard=plugin.configuration().current().guard();
                for(var id:dev.aegisac.common.guard.GuardId.values()) {
                    if(!category.equals("all")&&!category.equals(id.category)) continue;
                    var rule=guard.rules().get(id);
                    String status=!id.implemented?"UNAVAILABLE":guard.category(id).enabled()&&rule.enabled()?"EXPERIMENTAL":"DISABLED";
                    plugin.reply(sender,"check-entry",Map.of("check",id.name(),"status",status,"description",id.description,"bedrock",plugin.configuration().current().edition().bedrockChecks().get(id.name()).mode().name()+"/"+rule.bedrockMode()));
                }
            }
            case "combat", "cps" -> {
                if(args.length!=2) { plugin.reply(sender,"movement-usage",Map.of("command",action)); break; }
                var target=plugin.admin().target(sender,args[1]);
                var snapshot=target==null?null:plugin.players().snapshot(target.getUniqueId()).orElse(null);
                if(snapshot==null) { plugin.reply(sender,"player-missing",Map.of()); break; }
                var combat=snapshot.combat();
                if(action.equals("cps")) plugin.reply(sender,"cps-summary",Map.of("player",snapshot.name(),
                        "rate",String.format(Locale.ROOT,"%.2f",combat.swingRate()),"variation",String.format(Locale.ROOT,"%.4f",combat.intervalVariation())));
                else {
                    String states=combat.checks().values().stream().sorted(java.util.Comparator.comparing(dev.aegisac.api.check.CheckSnapshot::id))
                            .map(c->c.id()+"="+c.status()).collect(java.util.stream.Collectors.joining(", "));
                    plugin.reply(sender,"combat-summary",Map.of("player",snapshot.name(),"generation",Long.toString(combat.generation()),
                            "attacks",Long.toString(combat.attacks()),"swings",Long.toString(combat.swings()),"targets",Integer.toString(combat.trackedTargets()),"states",states));
                    if(!combat.evidence().isEmpty()) {
                        var last=combat.evidence().getLast();
                        plugin.reply(sender,"movement-evidence",Map.of("check",last.check(),"sequence",Long.toString(last.sequence()),
                                "diagnostic",Boolean.toString(last.diagnostic()),"excess",String.format(Locale.ROOT,"%.5f",last.excess()),
                                "reasons",last.reasons().stream().sorted().collect(java.util.stream.Collectors.joining(","))));
                    }
                }
            }
            case "movement", "safeposition" -> {
                if(args.length!=2) { plugin.reply(sender,"movement-usage",Map.of("command",action)); break; }
                var target=plugin.admin().target(sender,args[1]);
                var snapshot=target==null?null:plugin.players().snapshot(target.getUniqueId()).orElse(null);
                if(snapshot==null) { plugin.reply(sender,"player-missing",Map.of()); break; }
                var checks=snapshot.movementChecks();
                if(action.equals("safeposition")) {
                    var position=checks.safePosition();
                    if(position==null) plugin.reply(sender,"safe-unavailable",Map.of("player",snapshot.name()));
                    else plugin.reply(sender,"safe-position",Map.of("player",snapshot.name(),"verified",Boolean.toString(position.verified()),
                            "x",Double.toString(position.x()),"y",Double.toString(position.y()),"z",Double.toString(position.z()),
                            "samples",Integer.toString(position.consecutiveSamples()),
                            "reasons",position.reasons().stream().sorted().collect(java.util.stream.Collectors.joining(","))));
                } else {
                    long findings=checks.checks().values().stream().mapToLong(dev.aegisac.api.check.CheckSnapshot::findings).sum();
                    long diagnostics=checks.checks().values().stream().mapToLong(dev.aegisac.api.check.CheckSnapshot::diagnostics).sum();
                    String states=checks.checks().values().stream().sorted(java.util.Comparator.comparing(dev.aegisac.api.check.CheckSnapshot::id))
                            .map(c->c.id()+"="+c.status()).collect(java.util.stream.Collectors.joining(", "));
                    plugin.reply(sender,"movement-summary",Map.of("player",snapshot.name(),"generation",Long.toString(checks.generation()),
                            "findings",Long.toString(findings),"diagnostics",Long.toString(diagnostics),"states",states));
                    if(!checks.evidence().isEmpty()) {
                        var last=checks.evidence().getLast();
                        plugin.reply(sender,"movement-evidence",Map.of("check",last.check(),"sequence",Long.toString(last.sequence()),
                                "diagnostic",Boolean.toString(last.diagnostic()),"excess",String.format(Locale.ROOT,"%.5f",last.excess()),
                                "reasons",last.reasons().stream().sorted().collect(java.util.stream.Collectors.joining(","))));
                    }
                }
            }
            case "alerts", "verbose" -> {
                if(!(sender instanceof Player player)) { plugin.reply(sender,"player-command",Map.of()); break; }
                try { boolean enabled=plugin.outputs().toggle(player.getUniqueId(),action.equals("verbose")); plugin.reply(sender,"output-toggle",Map.of("channel",action,"enabled",Boolean.toString(enabled))); }
                catch(IllegalStateException full) { plugin.reply(sender,"output-busy",Map.of()); }
            }
            case "panic" -> {
                boolean enabled=plugin.outputs().togglePanic(); plugin.reply(sender,"panic",Map.of("enabled",Boolean.toString(enabled)));
                plugin.getLogger().warning("AegisAC panic="+enabled+"; automatic punishment "+(enabled?"blocked":"subject to configured eligibility gates"));
            }
            case "output" -> plugin.reply(sender,"output-status",Map.of("panic",Boolean.toString(plugin.outputs().panic()),"metrics",plugin.outputs().metrics().toString()));
            case "violations" -> {
                if(args.length!=2) { plugin.reply(sender,"movement-usage",Map.of("command",action)); break; }
                var target=plugin.admin().target(sender,args[1]);
                if(target==null) { plugin.reply(sender,"player-missing",Map.of()); break; }
                var score=plugin.outputs().violations(target.getUniqueId());
                plugin.reply(sender,"violations",Map.of("player",target.getName(),"risk",dev.aegisac.common.output.OutputRecord.number(score.totalRisk()),"confidence",dev.aegisac.common.output.OutputRecord.number(score.confidence()),"findings",Integer.toString(score.findings()),"checks",score.checks().toString()));
            }
            case "logs" -> logs(sender,args);
            case "inspect" -> inspect(sender,args);
            case "performance" -> {
                var metrics = plugin.metrics().snapshot();
                var pipeline = plugin.metrics().pipeline();
                var values = new java.util.HashMap<String, String>();
                values.put("captures", Long.toString(plugin.captures().captured()));
                values.put("capture_failures", Long.toString(plugin.captures().failed()));
                values.put("players", Integer.toString(plugin.players().size()));
                values.put("inbound", Long.toString(metrics.inbound())); values.put("outbound", Long.toString(metrics.outbound()));
                values.put("untracked", Long.toString(metrics.untracked()));
                values.put("generation", Long.toString(plugin.configuration().current().generation()));
                values.put("processed", Long.toString(pipeline.processed())); values.put("queued", Long.toString(pipeline.queued()));
                values.put("dropped", Long.toString(pipeline.dropped())); values.put("decode_rejected", Long.toString(pipeline.decodeRejected()));
                values.put("failures", Long.toString(pipeline.failures())); values.put("executor_rejected", Long.toString(pipeline.executorRejected()));
                plugin.reply(sender, "performance", values);
            }
            default -> throw new IllegalStateException("Unreachable command");
        }
        return true;
    }
    private void staff(CommandSender sender,String action,String[] args) {
        try {
            if(action.equals("gui")) {
                if(!(sender instanceof Player viewer)) { plugin.reply(sender,"player-command",Map.of()); return; }
                if(args.length>2) throw new IllegalArgumentException("Usage: /ac gui [player]");
                var subject=args.length==2?plugin.admin().target(sender,args[1]):null;
                if(args.length==2&&subject==null) { plugin.reply(sender,"player-missing",Map.of()); return; }
                plugin.menus().open(viewer,subject==null?"dashboard":"profile",subject==null?null:subject.getUniqueId(),0); return;
            }
            if(action.equals("top")) {
                if(args.length!=1) throw new IllegalArgumentException("Usage: /ac top");
                var ranked=plugin.directory().visible(sender).stream()
                    .map(p->Map.entry(p.getName(),plugin.outputs().violations(p.getUniqueId()).totalRisk()))
                    .sorted(Map.Entry.<String,Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(10).toList();
                plugin.reply(sender,"admin-result",Map.of("result","Current online experimental risk (not a cheating verdict): "+ranked)); return;
            }
            if(action.equals("toggle")) {
                if(args.length!=2) throw new IllegalArgumentException("Usage: /ac toggle <check>");
                var e=dev.aegisac.common.config.CheckCatalog.find(args[1]); var c=plugin.configuration().current();
                if(!e.available()) throw new IllegalArgumentException("This check model is unavailable");
                plugin.admin().edit(sender,c.generation(),dev.aegisac.common.config.ConfigEdit.check(e.id(),!dev.aegisac.common.config.CheckCatalog.enabled(c,e)),"toggle",null); return;
            }
            int expected=action.equals("exempt")||action.equals("bypass")?4:2;
            if(args.length!=expected&&!(action.equals("freeze")&&args.length==3)) throw new IllegalArgumentException("Usage: /ac "+action+" <player>"+(expected==4?" <check|*> <time (1s..1h)>":action.equals("freeze")?" [time (1s..10m)]":""));
            var target=plugin.admin().target(sender,args[1]); if(target==null) { plugin.reply(sender,"player-missing",Map.of()); return; }
            var data=plugin.players().find(target.getUniqueId());
            if(action.equals("debug")) {
                var frames=data.recentPackets(); var recent=frames.subList(Math.max(0,frames.size()-16),frames.size());
                plugin.reply(sender,"admin-result",Map.of("result","Bounded packet debug: "+target.getName()+" session="+data.snapshot().sessionId()+" loss="+data.lossEpoch()+" frames="+recent.size()));
                for(var frame:recent) plugin.reply(sender,"admin-result",Map.of("result",frame.sequence()+" "+frame.direction()+" "+frame.packet().kind()+" observed="+frame.observedNanos()));
                return;
            }
            long duration=action.equals("freeze")?(args.length==3?dev.aegisac.paper.admin.AdminService.duration(args[2],600):60_000_000_000L):
                    action.equals("exempt")||action.equals("bypass")?dev.aegisac.paper.admin.AdminService.duration(args[3],3600):0;
            String actor=sender.getName();
            plugin.admin().control(sender,target,action,()->{
                switch(action) {
                    case "reset" -> plugin.outputs().reset(target.getUniqueId());
                    case "exempt", "bypass" -> data.exempt(args[2],System.nanoTime(),duration);
                    case "freeze" -> plugin.admin().freeze(target.address(),duration);
                    case "unfreeze" -> plugin.admin().unfreeze(target.getUniqueId());
                    default -> throw new IllegalStateException("Unknown staff action");
                }
                plugin.getLogger().info("Staff action "+action+" by "+actor+" for "+target.getUniqueId());
            },()->plugin.reply(sender,"admin-result",Map.of("result",action+" applied to "+target.getName()+". Session controls are temporary; persistent logs are retained.")));
        } catch(IllegalArgumentException invalid) { plugin.reply(sender,"admin-result",Map.of("result",invalid.getMessage())); }
    }
    private void logs(CommandSender sender,String[] args) {
        if(args.length!=2) { plugin.reply(sender,"movement-usage",Map.of("command","logs")); return; }
        var target=plugin.admin().target(sender,args[1]); java.util.UUID uuid;
        try { uuid=target!=null?target.getUniqueId():java.util.UUID.fromString(args[1]); }
        catch(IllegalArgumentException invalid) { plugin.reply(sender,"player-missing",Map.of()); return; }
        dev.aegisac.paper.scheduler.SessionAudience audience;
        try { audience=dev.aegisac.paper.scheduler.SessionAudience.capture(sender,plugin.players()); }
        catch(IllegalArgumentException unavailable) { plugin.reply(sender,"admin-result",Map.of("result",unavailable.getMessage())); return; }
        boolean queued=plugin.outputs().logs().query(uuid,records->{
            try { if(plugin.isEnabled()) audience.dispatch(plugin.scheduler(),viewer->{
                if(!allowed(viewer,"logs")) return;
                plugin.reply(viewer,"logs-result",Map.of("count",Integer.toString(records.size())));
                for(String record:records) plugin.reply(viewer,"log-line",Map.of("record",record));
            },()->{}); } catch(org.bukkit.plugin.IllegalPluginAccessException|java.util.concurrent.RejectedExecutionException disabled) { }
        });
        plugin.reply(sender,queued?"logs-queued":"output-busy",Map.of());
    }
    private void inspect(CommandSender sender,String[] args) {
        String category=args.length==3?args[2].toLowerCase(Locale.ROOT):"all";
        if(args.length<2||args.length>3||!category.equals("all")&&!dev.aegisac.common.config.GuardSettings.CATEGORIES.contains(category)) {
            plugin.reply(sender,"inspect-usage",Map.of()); return;
        }
        var target=plugin.admin().target(sender,args[1]);
        var snapshot=target==null?null:plugin.players().snapshot(target.getUniqueId()).orElse(null);
        if(snapshot==null) { plugin.reply(sender,"player-missing",Map.of()); return; }
        var guard=snapshot.guard();
        String states=guard.checks().values().stream().filter(c->category.equals("all")||dev.aegisac.common.guard.GuardId.valueOf(c.id()).category.equals(category))
                .sorted(java.util.Comparator.comparing(dev.aegisac.api.check.CheckSnapshot::id))
                .map(c->c.id()+"="+c.status()).collect(java.util.stream.Collectors.joining(", "));
        plugin.reply(sender,"guard-summary",Map.of("player",snapshot.name(),"category",category,"generation",Long.toString(guard.generation()),"states",states));
        var evidence=guard.evidence().stream().filter(e->category.equals("all")||e.category().equals(category)).toList();
        if(!evidence.isEmpty()) {
            var last=evidence.getLast();
            plugin.reply(sender,"guard-evidence",Map.of("check",last.check(),"sequence",Long.toString(last.sequence()),
                    "observed",Double.toString(last.observed()),"limit",Double.toString(last.limit()),
                    "reasons",last.reasons().stream().sorted().collect(java.util.stream.Collectors.joining(","))));
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String action : COMMANDS) if (action.startsWith(prefix) && allowed(sender, action)) result.add(action);
        } else if (args.length == 2 && (List.of("gui","debug","reset","exempt","bypass","freeze","unfreeze").contains(args[0].toLowerCase(Locale.ROOT)) || args[0].equalsIgnoreCase("logs") || args[0].equalsIgnoreCase("violations") || args[0].equalsIgnoreCase("edition") || args[0].equalsIgnoreCase("bedrock") || args[0].equalsIgnoreCase("profile") || args[0].equalsIgnoreCase("connection") || args[0].equalsIgnoreCase("physics") || args[0].equalsIgnoreCase("movement") || args[0].equalsIgnoreCase("safeposition") || args[0].equalsIgnoreCase("combat") || args[0].equalsIgnoreCase("cps") || args[0].equalsIgnoreCase("inspect"))
                && allowed(sender, args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (var player : plugin.directory().visible(sender)) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(player.getName());
            }
        }
        if(args.length==3&&args[0].equalsIgnoreCase("inspect")&&allowed(sender,"inspect")) {
            String prefix=args[2].toLowerCase(Locale.ROOT);
            for(String category:dev.aegisac.common.config.GuardSettings.CATEGORIES) if(category.startsWith(prefix)) result.add(category);
            if("all".startsWith(prefix)) result.add("all");
        }
        if(args.length>=2&&allowed(sender,args[0].toLowerCase(Locale.ROOT))) {
            String action=args[0].toLowerCase(Locale.ROOT),prefix=args[args.length-1].toLowerCase(Locale.ROOT); var choices=new ArrayList<String>();
            if(args.length==2&&action.equals("checks")) choices.addAll(List.of("all","combat","movement","world","player","inventory","protocol","exploit"));
            if(args.length==2&&action.equals("toggle")||args.length==3&&List.of("exempt","bypass").contains(action)) {
                dev.aegisac.common.config.CheckCatalog.ALL.forEach(e->choices.add(e.id())); if(!action.equals("toggle")) choices.add("*");
            }
            if(args.length==4&&List.of("exempt","bypass").contains(action)) choices.addAll(List.of("30s","5m","1h"));
            if(args.length==3&&action.equals("freeze")) choices.addAll(List.of("30s","1m","5m"));
            choices.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).forEach(result::add);
        }
        return result;
    }
}
