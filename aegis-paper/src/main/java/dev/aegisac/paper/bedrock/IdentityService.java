package dev.aegisac.paper.bedrock;
import dev.aegisac.common.bedrock.*;
import dev.aegisac.common.config.ConfigSnapshot;
import dev.aegisac.common.player.PlayerData;
import dev.aegisac.api.player.BedrockStatus;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
/** Budgeted owner-thread provider queries, session-bound publication and immediate epoch invalidation. */
public final class IdentityService implements AutoCloseable {
    private static final class Entry {
        final PlayerData data; long nextPoll; boolean polled,bedrockSeen;
        Entry(PlayerData data) { this.data=data; }
    }
    private static final class Provider {
        final String name,pluginName;
        Plugin plugin; OfficialIdentityProvider reader; boolean enabled;
        Provider(String name,String pluginName) { this.name=name; this.pluginName=pluginName; }
    }
    @FunctionalInterface interface Binder { OfficialIdentityProvider bind(String name,ClassLoader loader) throws ReflectiveOperationException; }
    private final Binder binder;
    private final Server server;
    private final BooleanSupplier globalOwner;
    private final AtomicBoolean dirty=new AtomicBoolean();
    private final Supplier<ConfigSnapshot> configuration;
    private final LongSupplier clock;
    private final Consumer<String> warnings;
    private final AtomicLong epoch=new AtomicLong();
    private final Map<UUID,Entry> entries=new ConcurrentHashMap<>();
    private final ArrayDeque<UUID> order=new ArrayDeque<>();
    private final Provider floodgate=new Provider("FLOODGATE","floodgate"),geyser=new Provider("GEYSER","Geyser-Spigot");
    private long generation=-1,lastTick;
    private boolean ticked;
    private volatile boolean closed;
    public IdentityService(Server server,Supplier<ConfigSnapshot> config,Consumer<String> warnings) { this(server,config,warnings,System::nanoTime); }
    public IdentityService(Server server,Supplier<ConfigSnapshot> config,Consumer<String> warnings,LongSupplier clock) { this(server,config,warnings,clock,OfficialIdentityProvider::bind); }
    public IdentityService(Server server,Supplier<ConfigSnapshot> config,Consumer<String> warnings,BooleanSupplier globalOwner) { this(server,config,warnings,System::nanoTime,OfficialIdentityProvider::bind,globalOwner); }
    IdentityService(Server server,Supplier<ConfigSnapshot> config,Consumer<String> warnings,LongSupplier clock,Binder binder) { this(server,config,warnings,clock,binder,server::isPrimaryThread); }
    IdentityService(Server server,Supplier<ConfigSnapshot> config,Consumer<String> warnings,LongSupplier clock,Binder binder,BooleanSupplier globalOwner) { this.server=server; this.configuration=config; this.warnings=warnings; this.clock=clock; this.binder=binder; this.globalOwner=globalOwner; }
    public long epoch() { return epoch.get(); }
    public synchronized void attach(UUID uuid,PlayerData data) {
         if(closed) throw new IllegalStateException("Identity service closed");
        if(entries.put(uuid,new Entry(data))==null) order.addLast(uuid);
        data.configureIdentity(this::epoch);
    }
    public synchronized void detach(UUID uuid) { entries.remove(uuid); order.remove(uuid); }
    public void providersChanged() {
        epoch.incrementAndGet(); dirty.set(true);
        // Lifecycle callbacks invalidate immediately; provider references are touched only by the global poller.
    }
    public void tick() {
        owner(); if(closed) return; var config=configuration.get(); var settings=config.edition().identity();
        if(generation!=config.generation()) { generation=config.generation(); providersChanged(); }
        if(dirty.getAndSet(false)) {
            floodgate.plugin=geyser.plugin=null; floodgate.reader=geyser.reader=null;
            entries.values().forEach(entry->entry.polled=false);
        }
        refresh(floodgate,settings.floodgate()); refresh(geyser,settings.geyser());
        long now=clock.getAsLong();
        if(ticked&&now-lastTick<0) { epoch.incrementAndGet(); entries.values().forEach(entry->entry.polled=false); }
        lastTick=now; ticked=true; int count=Math.min(entries.size(),settings.queriesPerTick());
        for(int i=0;i<count;i++) {
            UUID uuid; Entry entry;
            synchronized(this) { uuid=order.pollFirst(); if(uuid==null) break; order.addLast(uuid); entry=entries.get(uuid); }
            if(entry==null) continue;
            if(entry.polled&&now-entry.nextPoll<0) continue;
            entry.polled=true; entry.nextPoll=now+settings.pollMillis()*1_000_000L;
            long observedEpoch=epoch.get();
            var result=IdentityResolver.resolve(List.of(query(floodgate,uuid),query(geyser,uuid)),settings.authoritativeNegatives(),settings.javaOnlyServer(),entry.bedrockSeen,now);
            if(result.status()==BedrockStatus.BEDROCK) entry.bedrockSeen=true;
            if(!closed&&epoch.get()==observedEpoch&&entries.get(uuid)==entry)
                entry.data.identityObservation(new IdentityObservation(generation,observedEpoch,result));
        }
    }
    private void refresh(Provider p,boolean enabled) {
        p.enabled=enabled;
        Plugin current=enabled?server.getPluginManager().getPlugin(p.pluginName):null;
        if(current!=null&&!current.isEnabled()) current=null;
        if(current==p.plugin) return;
        p.plugin=current; p.reader=null; epoch.incrementAndGet();
        if(current!=null) try { p.reader=binder.bind(p.name,current.getClass().getClassLoader()); }
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure) { warnings.accept(p.name+" API unavailable; identity remains uncertain"); }
    }
    private IdentityResult query(Provider provider,UUID uuid) {
        if(!provider.enabled) return IdentityResult.of(provider.name,IdentityResult.Outcome.DISABLED);
        if(provider.plugin==null) return IdentityResult.of(provider.name,IdentityResult.Outcome.ABSENT);
        return provider.reader==null?IdentityResult.of(provider.name,IdentityResult.Outcome.FAILURE):provider.reader.query(uuid);
    }
    private void owner() { if(!globalOwner.getAsBoolean()) throw new IllegalStateException("Identity APIs require the global scheduler"); }
    @Override public synchronized void close() { owner(); closed=true; epoch.incrementAndGet(); entries.clear(); order.clear(); floodgate.reader=geyser.reader=null; floodgate.plugin=geyser.plugin=null; }
}
