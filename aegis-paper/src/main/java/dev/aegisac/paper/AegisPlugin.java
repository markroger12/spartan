package dev.aegisac.paper;

import com.github.retrooper.packetevents.PacketEvents;
import dev.aegisac.api.AntiCheatApi;
import dev.aegisac.api.player.PlayerSnapshot;
import dev.aegisac.common.config.ConfigService;
import dev.aegisac.common.config.ConfigurationException;
import dev.aegisac.common.config.ConfigurationLoader;
import dev.aegisac.common.packet.PacketMetrics;
import dev.aegisac.common.packet.PacketRouter;
import dev.aegisac.common.player.PlayerRegistry;
import dev.aegisac.paper.command.AegisCommand;
import dev.aegisac.paper.compatibility.PlatformCapabilities;
import dev.aegisac.paper.packet.PacketEventsEngine;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Composition root. Phase 10 uses explicit task domains; the Folia runtime release gate remains closed. */
@SuppressWarnings("deprecation") // Bukkit text/metadata APIs also exist on Spigot; Paper-only replacements do not.
public class AegisPlugin extends JavaPlugin implements Listener {
    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("%([a-z_]+)%");
    private final PlayerRegistry players = new PlayerRegistry();
    private final PacketMetrics metrics = new PacketMetrics();
    private final AtomicBoolean reloading = new AtomicBoolean();
    private volatile boolean stopping;
    private final Object[] lifecycleLocks=java.util.stream.IntStream.range(0,64).mapToObj(i->new Object()).toArray();
    private Object lifecycle(UUID uuid) { return lifecycleLocks[(uuid.hashCode()&Integer.MAX_VALUE)%lifecycleLocks.length]; }
    private ConfigService configuration;
    private dev.aegisac.paper.player.PlayerDirectory directory;
    public dev.aegisac.paper.player.PlayerDirectory directory() { return directory; }
    private dev.aegisac.paper.scheduler.PlatformScheduler scheduler;
    public dev.aegisac.paper.scheduler.PlatformScheduler scheduler() { return scheduler; }
    private PacketEventsEngine packets;
    private dev.aegisac.paper.world.WorldCaptureService captures;
    private ThreadPoolExecutor configWorker;
    private dev.aegisac.paper.bedrock.IdentityService identities;
    private dev.aegisac.paper.output.OutputService outputs;
    private dev.aegisac.paper.admin.AdminService admin;
    private dev.aegisac.paper.admin.MenuService menus;
    private dev.aegisac.paper.admin.CommandAliases aliases;
    public dev.aegisac.paper.admin.AdminService admin() { return admin; }
    public dev.aegisac.paper.admin.MenuService menus() { return menus; }
    public dev.aegisac.paper.output.OutputService outputs() { return outputs; }

    @Override public void onEnable() {
        try {
            var capabilities = PlatformCapabilities.detect(getServer().getPluginManager());
            if (capabilities.folia()) throw new IllegalStateException("Phase 10 Folia release gate is closed: service ownership migration and independent live validation are required");
            scheduler=new dev.aegisac.paper.scheduler.BukkitPlatformScheduler(this);
            directory=new dev.aegisac.paper.player.PlayerDirectory(players,scheduler);
            configuration = new ConfigService(new ConfigurationLoader(getDataFolder().toPath(),name->{ var m=org.bukkit.Material.matchMaterial(name); return m!=null&&m.isItem()&&!m.isAir(); }));
            configuration.reload();
            if (!getServer().getPluginManager().isPluginEnabled("packetevents") || PacketEvents.getAPI() == null)
                throw new IllegalStateException("Install and enable PacketEvents 2.14.0 before AegisAC");
            configWorker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "AegisAC-configuration");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
            packets = new PacketEventsEngine(PacketEvents.getAPI(),
                    new PacketRouter<>(metrics, () -> configuration.current().packetMetrics(), configuration.current().pipeline(),
                            problem -> getLogger().log(Level.SEVERE, "Packet processing failed; state marked uncertain", problem)));
            captures = new dev.aegisac.paper.world.WorldCaptureService(getServer(), configuration.current().physics(),
                    problem -> getLogger().log(Level.WARNING, "World capture failed; physics remains uncertain", problem),scheduler);
            getServer().getPluginManager().registerEvents(new dev.aegisac.paper.world.WorldInvalidationListener(captures), this);
            scheduler.global(1,1,captures::tick);
            identities=new dev.aegisac.paper.bedrock.IdentityService(getServer(),configuration::current,message->getLogger().warning(message),scheduler::globalThread);
            scheduler.global(1,1,identities::tick);
            outputs=new dev.aegisac.paper.output.OutputService(this,players,configuration::current,scheduler,directory);
            scheduler.global(1,1,outputs::tick);
            outputs.startup();
            admin=new dev.aegisac.paper.admin.AdminService(this); menus=new dev.aegisac.paper.admin.MenuService(this);
            getServer().getPluginManager().registerEvents(admin,this); getServer().getPluginManager().registerEvents(menus,this);
            scheduler.global(1,1,admin::tick);
            packets.start();
            if (configuration.current().pipeline().activeProbes()) {
                int interval = configuration.current().pipeline().probeIntervalTicks();
                scheduler.global(interval,interval,packets::probeConnections);
            }
            getServer().getPluginManager().registerEvents(this, this);
            for (Player player : getServer().getOnlinePlayers()) {
                if(scheduler.owns(player)) attach(player,true);
                else scheduler.entity(player,1,0,()->!stopping,()->attach(player,true),()->{});
            }
            AegisCommand commands = new AegisCommand(this);
            var command = Objects.requireNonNull(getCommand("ac"), "plugin.yml must declare ac");
            command.setExecutor(commands);
            command.setTabCompleter(commands);
            aliases=new dev.aegisac.paper.admin.CommandAliases(this,commands);
            getServer().getServicesManager().register(AntiCheatApi.class, new AntiCheatApi() {
                @Override public Optional<PlayerSnapshot> player(UUID uuid) { return players.snapshot(uuid); }
                @Override public dev.aegisac.api.output.ViolationSnapshot violations(UUID uuid) { return outputs.violations(uuid); }
                @Override public int onlinePlayers() { return players.size(); }
                @Override public long configurationGeneration() { return configuration.current().generation(); }
            }, this, ServicePriority.Normal);
            getLogger().info("Enabled " + getDescription().getVersion() + "; server=" + getServer().getName()
                    + "; PacketEvents=" + PacketEvents.getAPI().getVersion()
                    + "; profile=" + configuration.current().defaultProfile()
                    + "; Floodgate present=" + capabilities.floodgate() + "; Geyser present=" + capabilities.geyser()
                    + "; ViaVersion present=" + capabilities.viaVersion()
                    + "; Bedrock identity=official API observations; unknown=conservative; movement-evaluators=15 combat-evaluators=15; guard-evaluators=31; guard-unavailable=8; experimental=true; diagnostics-never-punish=true; Folia=false; punishment-policy-enabled=" + configuration.current().output().punishments().enabled()+"; panic="+outputs.panic());
        } catch (ConfigurationException invalid) {
            getLogger().severe("Configuration rejected: " + invalid.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        } catch (IOException failure) {
            getLogger().severe("Cannot read or write configuration (" + failure.getClass().getSimpleName() + "). Check data-directory permissions.");
            getServer().getPluginManager().disablePlugin(this);
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(Level.SEVERE, "AegisAC bootstrap failed; check the supported platform and dependency versions", failure);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { attach(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        synchronized(lifecycle(uuid)) {
            var existing=directory.find(uuid);
            if(stopping||existing==null||existing.address()!=event.getPlayer()) return;
            directory.detach(uuid);
            outputs.detach(uuid);
            identities.detach(uuid);
            captures.detach(uuid);
            packets.detach(uuid);
            players.quit(uuid);
        }
    }
    private void attach(Player player) { attach(player,false); }
    private void attach(Player player,boolean reconciliation) {
        scheduler.requireEntity(player);
        UUID uuid=player.getUniqueId();
        synchronized(lifecycle(uuid)) {
            if(stopping||!player.isOnline()) return;
            var existing=directory.find(uuid);
            if(existing!=null&&(reconciliation||existing.address()==player)) return;
            var data=players.join(uuid,player.getName(),System.nanoTime());
            data.configurePhysics(configuration.current().physics());
            data.configureChecks(configuration::current,()->directory.health(uuid));
            if(!packets.attach(player,data)) {
                directory.detach(uuid); outputs.detach(uuid); identities.detach(uuid); captures.detach(uuid); packets.detach(uuid); players.quit(uuid);
                getLogger().warning("PacketEvents has no transport for joined player "+uuid+"; session not monitored");
            } else {
                directory.attach(player,data);
                outputs.attach(uuid,data);
                identities.attach(uuid,data);
                captures.attach(player,data);
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void providerDisabled(org.bukkit.event.server.PluginDisableEvent event) { providerChanged(event.getPlugin().getName()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void providerEnabled(org.bukkit.event.server.PluginEnableEvent event) { providerChanged(event.getPlugin().getName()); }
    private void providerChanged(String name) {
        if(identities!=null&&(name.equalsIgnoreCase("floodgate")||name.equalsIgnoreCase("Geyser-Spigot"))) identities.providersChanged();
    }
    /** Disk work stays off the server thread; completions are marshalled back to Bukkit. */
    public void reload(CommandSender sender) {
        dev.aegisac.paper.scheduler.SessionAudience audience;
        try { audience=dev.aegisac.paper.scheduler.SessionAudience.capture(sender,players); }
        catch(IllegalArgumentException unavailable) { reply(sender,"admin-result",Map.of("result",unavailable.getMessage())); return; }
        if (!reloading.compareAndSet(false, true)) { reply(sender, "reload-busy", Map.of()); return; }
        reply(sender, "reloading", Map.of());
        // Audience retains only a scheduler address/session fence; live reads and delivery stay on its owner.

        try {
            configWorker.execute(() -> {
                String result = "reload-success";
                Map<String, String> values;
                try {
                    var snapshot = configuration.reload();
                    values = Map.of("generation", Long.toString(snapshot.generation()));
                } catch (ConfigurationException invalid) {
                    result = "reload-failed";
                    values = Map.of("error", invalid.getMessage());
                } catch (IOException io) {
                    result = "reload-failed";
                    values = Map.of("error", "configuration I/O failed; check file permissions and concurrent edits");
                } catch (RuntimeException failure) {
                    getLogger().log(Level.SEVERE, "Unexpected configuration reload failure", failure);
                    result = "reload-failed";
                    values = Map.of("error", "internal validation error; see server log");
                }
                String key = result;
                Map<String, String> replacements = values;
                try {
                    reloading.set(false);
                    if (!stopping) audience.dispatch(scheduler,target->{
                        if(target.hasPermission("aegisac.admin")||target.hasPermission("aegisac.reload")) reply(target,key,replacements);
                    },()->{});
                } catch (org.bukkit.plugin.IllegalPluginAccessException | RejectedExecutionException disabled) {
                    // The server disabled the plugin between the stopping check and scheduling.
                    reloading.set(false);
                }
            });
        } catch (RejectedExecutionException busy) {
            reloading.set(false);
            reply(sender, "reload-busy", Map.of());
        }
    }
    public void reply(CommandSender recipient, String key, Map<String, String> replacements) {
        var snapshot = configuration.current();
        String template = ChatColor.translateAlternateColorCodes('&', snapshot.message("prefix") + snapshot.message(key));
        // Client-reported brands stay literal: no color interpretation or recursive placeholder expansion.
        String text = PLACEHOLDER.matcher(template).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
                replacements.getOrDefault(match.group(1), match.group())));
        recipient.sendMessage(text);
    }
    @Override public void onDisable() {
        stopping = true;
        if (menus != null) menus.close();
        if (admin != null) admin.close();
        if (aliases != null) aliases.close();
        if (packets != null) packets.close();
        HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this);
        if (scheduler != null) scheduler.close();
        getServer().getServicesManager().unregisterAll(this);
        if (directory != null) directory.close();
        if (outputs != null) outputs.close();
        if (identities != null) identities.close();
        if (captures != null) captures.close();
        players.close();
        if (configWorker != null) configWorker.shutdownNow();
    }
    public PlayerRegistry players() { return players; }
    public dev.aegisac.paper.world.WorldCaptureService captures() { return captures; }
    public PacketMetrics metrics() { return metrics; }
    public ConfigService configuration() { return configuration; }
}
