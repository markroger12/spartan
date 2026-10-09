package dev.aegisac.paper.output;
import dev.aegisac.api.output.ViolationSnapshot;
import dev.aegisac.api.player.BedrockStatus;
import dev.aegisac.common.config.*;
import dev.aegisac.common.output.*;
import dev.aegisac.common.player.*;
import dev.aegisac.paper.event.*;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
/** Owner-thread effects behind session, policy, freshness and trust gates. */
@SuppressWarnings("deprecation")
public final class OutputService implements AutoCloseable {
    private static final class Session {
        final PlayerData data;
        final dev.aegisac.paper.player.PlayerDirectory.Handle handle;
        Session(PlayerData data,dev.aegisac.paper.player.PlayerDirectory.Handle handle) { this.data=data; this.handle=handle; }
        final ViolationLedger ledger=new ViolationLedger();
        final Map<String,Long> alerts=new HashMap<>(),sequences=new HashMap<>();
        final ArrayDeque<OutputRecord> recent=new ArrayDeque<>();
        final java.util.concurrent.atomic.AtomicBoolean correctionPending=new java.util.concurrent.atomic.AtomicBoolean();
        DetectionEnvelope anchor; long lastPunish,lastSetback; boolean punished,setback;
        void reset() { ledger.clear(); alerts.clear(); sequences.clear(); recent.clear(); }
    }
    private record Delivery(UUID staff,Session recipient,DetectionEnvelope envelope,OutputRecord record) { }
    private final JavaPlugin plugin; private final PlayerRegistry players; private final Supplier<ConfigSnapshot> config; private final LongSupplier clock;
    private final ArrayBlockingQueue<DetectionEnvelope> pending=new ArrayBlockingQueue<>(1024);
    private final ArrayBlockingQueue<Delivery> deliveries=new ArrayBlockingQueue<>(256);
    private final ConcurrentMap<UUID,Session> sessions=new ConcurrentHashMap<>();
    private final Set<UUID> alerts=ConcurrentHashMap.newKeySet(),verbose=ConcurrentHashMap.newKeySet();
    private final AtomicLong dropped=new AtomicLong(),stale=new AtomicLong();
    private final dev.aegisac.paper.scheduler.PlatformScheduler scheduler;
    private final dev.aegisac.paper.player.PlayerDirectory directory;
    private final AsyncLogStore logs; private final WebhookWorker webhooks;
    private volatile boolean closed,panic; private long failureCount,lastFailureWarning,reportedLogFailures;
    public OutputService(JavaPlugin plugin,PlayerRegistry players,Supplier<ConfigSnapshot> config) { this(plugin,players,config,System::nanoTime); }
    OutputService(JavaPlugin plugin,PlayerRegistry players,Supplier<ConfigSnapshot> config,LongSupplier clock) {
        this(plugin,players,config,clock,null,null);
    }
    public OutputService(JavaPlugin plugin,PlayerRegistry players,Supplier<ConfigSnapshot> config,dev.aegisac.paper.scheduler.PlatformScheduler scheduler,dev.aegisac.paper.player.PlayerDirectory directory) {
        this(plugin,players,config,System::nanoTime,scheduler,directory);
    }
    OutputService(JavaPlugin plugin,PlayerRegistry players,Supplier<ConfigSnapshot> config,LongSupplier clock,dev.aegisac.paper.scheduler.PlatformScheduler scheduler,dev.aegisac.paper.player.PlayerDirectory directory) {
        this.plugin=plugin; this.players=players; this.config=config; this.clock=clock; this.scheduler=scheduler; this.directory=directory;
        logs=new AsyncLogStore(plugin.getDataFolder().toPath(),config); webhooks=new WebhookWorker(config);
    }
    public void startup() { webhooks.offer(config.get().generation(),"STARTUP","AegisAC started; diagnostic evidence cannot punish; panic="+panic); }
    public void attach(UUID uuid,PlayerData data) { entityOwner(uuid); sessions.put(uuid,new Session(data,directory==null?null:directory.find(uuid))); data.configureOutput(this::offer); }
    public void detach(UUID uuid) { sessions.remove(uuid); alerts.remove(uuid); verbose.remove(uuid); deliveries.removeIf(d->d.staff.equals(uuid)||d.envelope.uuid().equals(uuid)); }
    public synchronized boolean offer(DetectionEnvelope e) { if(closed||!pending.offer(e)) { dropped.incrementAndGet(); return false; } return true; }
    public synchronized boolean toggle(UUID uuid,boolean detailed) {
        entityOwner(uuid); Set<UUID> set=detailed?verbose:alerts; if(set.remove(uuid)) return false;
        if(!alerts.contains(uuid)&&!verbose.contains(uuid)&&unionSize()>=128) throw new IllegalStateException("Staff subscription capacity reached");
        set.add(uuid); return true;
    }
    private int unionSize() { var all=new HashSet<>(alerts); all.addAll(verbose); return all.size(); }
    public boolean panic() { return panic; }
    public synchronized boolean togglePanic() { if(scheduler==null) owner(); panic=!panic; return panic; }
    public ViolationSnapshot violations(UUID uuid) {
        Session s=sessions.get(uuid); if(s==null) return ViolationSnapshot.empty();
        synchronized(s) { var anchor=s.anchor; long now=clock.getAsLong(); return anchor!=null&&current(anchor,now)?s.ledger.snapshot(now,config.get().output().punishments()):ViolationSnapshot.empty(); }
    }
    public List<OutputRecord> recent(UUID uuid) {
        Session s=sessions.get(uuid); if(s==null) return List.of(); synchronized(s) { return List.copyOf(s.recent); }
    }
    public void reset(UUID uuid) {
        entityOwner(uuid); var data=players.find(uuid); if(data!=null) data.markGap();
        Session s=sessions.get(uuid); if(s!=null) synchronized(s) { s.reset(); s.anchor=null; }
        deliveries.removeIf(d->d.envelope.uuid().equals(uuid));
    }
    public AsyncLogStore logs() { return logs; }
    public Map<String,Long> metrics() { return Map.of("queued",(long)pending.size(),"dropped",dropped.get(),"stale",stale.get(),"log-dropped",logs.dropped(),"log-failed",logs.failed(),"log-written",logs.written(),"webhook-dropped",webhooks.dropped(),"webhook-failed",webhooks.failed(),"webhook-sent",webhooks.sent()); }
    private boolean current(DetectionEnvelope e,long now) {
        PlayerData data=players.find(e.uuid());
        return data!=null&&!data.temporarilyExempt(e.detection().check(),now)&&data.currentOutput(e.session(),e.lossEpoch(),e.providerEpoch(),e.detection().generation(),e.identityKey(),now);
    }
    public void tick() {
        owner(); if(closed) return;
        for(int n=0;n<64;n++) {
            var e=pending.poll(); if(e==null) break;
            var session=sessions.get(e.uuid());
            onEntity(e.uuid(),session,()->accept(e));
        }
        for(int n=0;n<128;n++) {
            var delivery=deliveries.poll(); if(delivery==null) break;
            onEntity(delivery.staff,delivery.recipient,()->deliver(delivery));
        }
        long failures=logs.failed()+webhooks.failed();
        if(failures>failureCount&&(failureCount==0||clock.getAsLong()-lastFailureWarning>=60_000_000_000L)) { failureCount=failures; lastFailureWarning=clock.getAsLong(); plugin.getLogger().warning("AegisAC output I/O failed; inspect /ac output for counters (endpoint and payload redacted)"); if(logs.failed()>reportedLogFailures) { reportedLogFailures=logs.failed(); webhooks.offer(config.get().generation(),"ERROR","AegisAC local log I/O failure; inspect local counters"); } }
    }
    private void onEntity(UUID uuid,Session session,Runnable action) {
        if(session==null||sessions.get(uuid)!=session||players.find(uuid)!=session.data||closed) { stale.incrementAndGet(); return; }
        if(scheduler==null) { action.run(); return; }
        if(session.handle==null) { stale.incrementAndGet(); return; }
        try { session.handle.run(()->{
            if(!closed&&sessions.get(uuid)==session&&players.find(uuid)==session.data) action.run(); else stale.incrementAndGet();
        },stale::incrementAndGet); }
        catch(RejectedExecutionException|org.bukkit.plugin.IllegalPluginAccessException busy) { dropped.incrementAndGet(); }
    }
    private void deliver(Delivery delivery) {
        long now=clock.getAsLong();
        if(!current(delivery.envelope,now)||now<delivery.envelope.detection().time()||now-delivery.envelope.detection().time()>2_000_000_000L) { stale.incrementAndGet(); return; }
        Player staff=player(delivery.staff); if(staff==null) return;
        boolean diagnostic=delivery.record.type().equals("DIAGNOSTIC");
        if(diagnostic?(!verbose.contains(delivery.staff)||!staff.hasPermission("aegisac.verbose")):
                !(alerts.contains(delivery.staff)&&staff.hasPermission("aegisac.alerts")||verbose.contains(delivery.staff)&&staff.hasPermission("aegisac.verbose"))) return;
        send(staff,delivery.record,config.get().output().alerts());
    }
    private Player player(UUID uuid) {
        if(directory==null) return plugin.getServer().getPlayer(uuid);
        var handle=directory.find(uuid); if(handle==null) return null;
        scheduler.requireEntity(handle.address()); return handle.address();
    }
    private void entityOwner(UUID uuid) {
        if(scheduler==null) { owner(); return; }
        var handle=directory.find(uuid); if(handle==null) throw new IllegalArgumentException("Player session unavailable");
        scheduler.requireEntity(handle.address());
    }
    private void accept(DetectionEnvelope e) {
        long now=clock.getAsLong(); var d=e.detection(); Session session=sessions.get(e.uuid());
        if(session==null||!current(e,now)||now<d.time()||now-d.time()>2_000_000_000L) { stale.incrementAndGet(); return; }
        var c=config.get(); var policy=c.output();
        if(!policy.punishments().rules().containsKey(d.check())) { dropped.incrementAndGet(); return; }
        ViolationSnapshot before;
        synchronized(session) {
            var old=session.anchor;
            if(old==null||old.session()!=e.session()||old.lossEpoch()!=e.lossEpoch()||old.providerEpoch()!=e.providerEpoch()||old.detection().generation()!=d.generation()||!old.identityKey().equals(e.identityKey())) session.reset();
            session.anchor=e;
            if(d.sequence()<=session.sequences.getOrDefault(d.check(),-1L)) return;
            session.sequences.put(d.check(),d.sequence());
            before=session.ledger.snapshot(now,policy.punishments());
        }
        String type=d.scoreEligible()?"EXPERIMENTAL_FINDING":"DIAGNOSTIC";
        var event=new PlayerFlagEvent(OutputRecord.create(e,before,policy,type),before);
        // Third-party callbacks must never run under a shared ledger monitor.
        plugin.getServer().getPluginManager().callEvent(event);
        if(event.isCancelled()||closed||sessions.get(e.uuid())!=session||!current(e,clock.getAsLong())) return;
        boolean added; ViolationSnapshot score; OutputRecord record;
        synchronized(session) {
            if(!current(e,clock.getAsLong())) return;
            added=session.ledger.accept(d,policy.punishments()); score=session.ledger.snapshot(now,policy.punishments());
            record=OutputRecord.create(e,score,policy,type);
            if(session.recent.size()>=32) session.recent.removeFirst(); session.recent.addLast(record);
        }
        if(added) plugin.getServer().getPluginManager().callEvent(new ViolationChangeEvent(record,score));
        if(closed||sessions.get(e.uuid())!=session||!current(e,clock.getAsLong())) return;
        log(record,policy); alert(e,record,session,policy);
        setback(e,record,score,session,policy);
        punish(e,record,score,session,policy);
    }
    private void log(OutputRecord record,OutputSettings policy) { if(policy.logging().enabled()&&(policy.logging().diagnostics()||!record.type().equals("DIAGNOSTIC"))) logs.offer(record); }
    private void alert(DetectionEnvelope e,OutputRecord record,Session s,OutputSettings policy) {
        var a=policy.alerts(); long now=clock.getAsLong(); Long last=s.alerts.get(record.check());
        if(last!=null&&now-last<a.cooldownMillis()*1_000_000L) return;
        s.alerts.put(record.check(),now);
        String text=OutputRecord.render(ChatColor.translateAlternateColorCodes('&',a.format()),record.values());
        webhooks.offer(record.generation(),record.type().equals("DIAGNOSTIC")?"DIAGNOSTIC":"ALERT",ChatColor.stripColor(text));
        if(!a.enabled()) return;
        if(a.console()) plugin.getLogger().info(ChatColor.stripColor(text));
        var recipients=new HashSet<>(verbose); if(!record.type().equals("DIAGNOSTIC")) recipients.addAll(alerts);
        for(UUID uuid:recipients) {
            var recipient=sessions.get(uuid); if(recipient==null) continue;
            if(!deliveries.offer(new Delivery(uuid,recipient,e,record))) { dropped.incrementAndGet(); break; }
        }
    }
    private static void send(Player player,OutputRecord record,OutputSettings.Alerts a) {
        var components=TextComponent.fromLegacyText(OutputRecord.render(ChatColor.translateAlternateColorCodes('&',a.format()),record.values()));
        for(var component:components) {
            if(a.hover()) component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,TextComponent.fromLegacyText(OutputRecord.render(ChatColor.translateAlternateColorCodes('&',a.hoverFormat()),record.values()))));
            String target=record.values().get("player");
            if(a.suggestTeleport()&&target.matches("[A-Za-z0-9_.-]{1,32}")) component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/tp "+target));
        }
        if(a.chat()) player.spigot().sendMessage(components);
        if(a.actionBar()) player.spigot().sendMessage(ChatMessageType.ACTION_BAR,components);
    }
    private boolean canPunish(DetectionEnvelope e,OutputSettings.Punishments policy) {
        return canAffect(e,policy.maximumAgeMillis(),true);
    }
    private boolean canAffect(DetectionEnvelope e,int maximumAgeMillis,boolean disableInPanic) {
        long now=clock.getAsLong(); if(panic&&disableInPanic||closed||now<e.detection().time()||now-e.detection().time()>maximumAgeMillis*1_000_000L||!current(e,now)||e.edition()!=BedrockStatus.JAVA) return false;
        Player player=player(e.uuid());
        if(player==null||!player.isOnline()||!player.getName().equals(e.player())||!e.player().matches("[A-Za-z0-9_]{1,16}")||player.hasPermission("aegisac.bypass")||player.hasPermission("aegisac.bypass."+e.detection().check().toLowerCase(Locale.ROOT))) return false;
        if(player.isDead()||player.isInsideVehicle()||player.isGliding()||!player.getWorld().getName().equals(e.world())) return false;
        var c=config.get();
        if(!c.edition().javaChecks().getOrDefault(e.detection().check(),false)||!checkEnabled(c,e.detection().check())) return false;
        var exemptions=c.exemptions();
        if(exemptions.enabled()&&(exemptions.worlds().contains(player.getWorld().getName())||exemptions.flight()&&player.getAllowFlight()||exemptions.creativeOrSpectator()&&(player.getGameMode()==org.bukkit.GameMode.CREATIVE||player.getGameMode()==org.bukkit.GameMode.SPECTATOR))) return false;
        return true;
    }
    private void setback(DetectionEnvelope e,OutputRecord record,ViolationSnapshot score,Session session,OutputSettings policy) {
        var p=policy.setbacks(); var d=e.detection(); long now=clock.getAsLong();
        if(!p.enabled()||!p.mode().equals("LAST_VALID_POSITION")||!p.checks().getOrDefault(d.check(),false)||!d.scoreEligible()||d.experimental()&&!p.allowExperimental()
                ||score.checks().getOrDefault(d.check(),0.0)<p.minimumVl()||score.confidence()<p.minimumConfidence()
                ||session.setback&&now-session.lastSetback<p.cooldownMillis()*1_000_000L||!canAffect(e,p.maximumAgeMillis(),p.disableInPanic())) return;
        var data=players.find(e.uuid()); var candidate=data.snapshot(now).movementChecks().safePosition();
        if(candidate==null||!candidate.verified()) return;
        var event=new PlayerSetbackEvent(record,score); plugin.getServer().getPluginManager().callEvent(event);
        if(event.isCancelled()||!canAffect(e,p.maximumAgeMillis(),p.disableInPanic())) return;
        session.setback=true; session.lastSetback=now;
        var safe=data.snapshot(clock.getAsLong()).movementChecks().safePosition();
        if(scheduler!=null&&scheduler.mode()==dev.aegisac.paper.scheduler.PlatformScheduler.Mode.FOLIA) {
            if(!session.correctionPending.compareAndSet(false,true)) return;
            try {
                VerifiedSetback.moveAsync(player(e.uuid()),safe,e.session(),data.world().read().revision(),clock.getAsLong(),p,
                        dev.aegisac.paper.world.WorldAccess.scheduled(scheduler),()->canAffect(e,p.maximumAgeMillis(),p.disableInPanic()))
                    .whenComplete((moved,failure)->{
                        session.correctionPending.set(false);
                        if(failure==null&&Boolean.TRUE.equals(moved)&&!closed&&sessions.get(e.uuid())==session&&players.find(e.uuid())==data)
                            appliedSetback(e,score,policy,data);
                    });
            } catch(RuntimeException unavailable) { session.correctionPending.set(false); }
        } else if(VerifiedSetback.move(plugin.getServer(),player(e.uuid()),safe,e.session(),data.world().read().revision(),clock.getAsLong(),p))
            appliedSetback(e,score,policy,data);
    }
    /** Completion is data-only; native teleport futures need not complete on an entity owner. */
    private void appliedSetback(DetectionEnvelope e,ViolationSnapshot score,OutputSettings policy,PlayerData data) {
        data.markGap(); var outcome=OutputRecord.create(e,score,policy,"SETBACK_APPLIED"); log(outcome,policy);
        webhooks.offer(outcome.generation(),"PUNISHMENT",e.player()+" "+e.detection().check()+" SETBACK_APPLIED");
    }
    private static boolean checkEnabled(ConfigSnapshot c,String check) {
        try { var id=dev.aegisac.common.check.CheckId.valueOf(check); return id.implemented&&c.movement().enabled()&&c.movement().rules().get(id).enabled(); } catch(IllegalArgumentException ignored) { }
        try { var id=dev.aegisac.common.combat.CombatId.valueOf(check); return c.combat().enabled()&&c.combat().rules().get(id).enabled(); } catch(IllegalArgumentException ignored) { }
        var id=dev.aegisac.common.guard.GuardId.valueOf(check); return id.implemented&&c.guard().category(id).enabled()&&c.guard().rules().get(id).enabled();
    }
    private void punish(DetectionEnvelope e,OutputRecord record,ViolationSnapshot score,Session session,OutputSettings policy) {
        var p=policy.punishments(); long now=clock.getAsLong();
        if(!ViolationLedger.qualifies(e.detection(),score,p)||!canPunish(e,p)||session.punished&&now-session.lastPunish<p.cooldownMillis()*1_000_000L) return;
        session.punished=true; session.lastPunish=now;
        var event=new PlayerPunishEvent(record,score); plugin.getServer().getPluginManager().callEvent(event);
        if(event.isCancelled()||!canPunish(e,p)) return;
        if(scheduler!=null&&scheduler.mode()==dev.aegisac.paper.scheduler.PlatformScheduler.Mode.FOLIA) {
            nativeCommand(e,score,session,policy,0); return;
        }
        for(String command:p.rules().get(e.detection().check()).commands()) {
            if(!canPunish(e,p)) break;
            boolean success;
            try { success=plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),OutputRecord.render(command,Map.of("player",e.player(),"uuid",e.uuid().toString(),"check",e.detection().check()))); }
            catch(RuntimeException failure) { success=false; }
            var outcome=OutputRecord.create(e,score,policy,success?"PUNISHMENT_DISPATCHED":"PUNISHMENT_DISPATCH_FAILED"); log(outcome,policy);
            webhooks.offer(outcome.generation(),"PUNISHMENT",e.player()+" "+e.detection().check()+" "+outcome.type());
            if(!success) break;
        }
    }
    /** Each command gets a fresh source-owner authorization, then a bounded global dispatch. */
    private void nativeCommand(DetectionEnvelope e,ViolationSnapshot score,Session session,OutputSettings policy,int index) {
        var p=policy.punishments(); var commands=p.rules().get(e.detection().check()).commands();
        if(index>=commands.size()||!canPunish(e,p)||session.handle==null||!session.handle.current()) return;
        long authorized=clock.getAsLong();
        try { scheduler.global(1,0,()->{
            long now=clock.getAsLong();
            // Do not read a remote player on the global scheduler. This is a short-lived grant
            // from its owner; evidence/session/policy/panic are checked again at dispatch.
            if(closed||panic||sessions.get(e.uuid())!=session||!session.handle.current()||!current(e,now)
                    ||now<authorized||now-authorized>100_000_000L||now<e.detection().time()
                    ||now-e.detection().time()>p.maximumAgeMillis()*1_000_000L) return;
            boolean success;
            try { success=plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),OutputRecord.render(commands.get(index),Map.of("player",e.player(),"uuid",e.uuid().toString(),"check",e.detection().check()))); }
            catch(RuntimeException failure) { success=false; }
            var outcome=OutputRecord.create(e,score,policy,success?"PUNISHMENT_DISPATCHED":"PUNISHMENT_DISPATCH_FAILED"); log(outcome,policy);
            webhooks.offer(outcome.generation(),"PUNISHMENT",e.player()+" "+e.detection().check()+" "+outcome.type());
            if(success&&index+1<commands.size()) onEntity(e.uuid(),session,()->nativeCommand(e,score,session,policy,index+1));
        }); } catch(RejectedExecutionException|org.bukkit.plugin.IllegalPluginAccessException unavailable) { dropped.incrementAndGet(); }
    }
    private void owner() { if(scheduler==null?!plugin.getServer().isPrimaryThread():!scheduler.globalThread()) throw new IllegalStateException("Output effects require the owning server thread"); }
    @Override public synchronized void close() { closed=true; pending.clear(); deliveries.clear(); sessions.clear(); alerts.clear(); verbose.clear(); logs.close(); webhooks.close(); }
}
