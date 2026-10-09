package dev.aegisac.paper.admin;
import dev.aegisac.paper.AegisPlugin;
import dev.aegisac.common.config.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.util.*;
import java.util.concurrent.*;
/** Owner-thread staff controls; disk transactions use a bounded, separate worker. */
public final class AdminService implements Listener,AutoCloseable {
    private record Freeze(dev.aegisac.paper.player.PlayerDirectory.Handle handle,long until,java.util.concurrent.atomic.AtomicBoolean pending) { }
    private final AegisPlugin plugin;
    private final Map<UUID,Freeze> freezes=new ConcurrentHashMap<>();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{ var t=new Thread(r,"AegisAC-admin-edits"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    private volatile boolean closed;
    public AdminService(AegisPlugin plugin) { this.plugin=plugin; }
    public static boolean allowed(CommandSender sender,String action) { return sender.hasPermission("aegisac.admin")||sender.hasPermission("aegisac."+(action.equals("bypass")?"bypass.manage":action)); }
    public static long duration(String text,int maximumSeconds) {
        if(!text.matches("[1-9][0-9]{0,4}[smh]")) throw new IllegalArgumentException("Use a duration such as 30s, 5m or 1h");
        long seconds=Long.parseLong(text.substring(0,text.length()-1))*switch(text.charAt(text.length()-1)) { case 'm'->60; case 'h'->3600; default->1; };
        if(seconds>maximumSeconds) throw new IllegalArgumentException("Duration exceeds "+maximumSeconds+" seconds"); return seconds*1_000_000_000L;
    }
    public dev.aegisac.paper.player.PlayerDirectory.Handle target(CommandSender viewer,String name) { return plugin.directory().find(viewer,name); }
    public void edit(CommandSender sender,long generation,ConfigEdit edit,String permission,Runnable completed) {
        edit(sender,generation,edit,permission,()->true,completed);
    }
    public void edit(CommandSender sender,long generation,ConfigEdit edit,String permission,java.util.function.BooleanSupplier guard,Runnable completed) {
        if(closed||!allowed(sender,permission)) { plugin.reply(sender,"denied",Map.of()); return; }
        dev.aegisac.paper.scheduler.SessionAudience audience;
        try { audience=dev.aegisac.paper.scheduler.SessionAudience.capture(sender,plugin.players()); }
        catch(IllegalArgumentException unavailable) { plugin.reply(sender,"admin-result",Map.of("result",unavailable.getMessage())); return; }
        try {
            worker.execute(()->{
                String result;
                try {
                    var next=plugin.configuration().edit(generation,edit,()->{
                        if(closed) return false;
                        var authorization=new CompletableFuture<Boolean>();
                        try {
                            audience.dispatch(plugin.scheduler(),recipient->{
                                try { authorization.complete(!closed&&allowed(recipient,permission)&&guard.getAsBoolean()); }
                                catch(RuntimeException invalid) { authorization.complete(false); }
                            },()->authorization.complete(false));
                            return authorization.get(5,TimeUnit.SECONDS);
                        } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
                        catch(ExecutionException|TimeoutException|RejectedExecutionException|org.bukkit.plugin.IllegalPluginAccessException expired) { return false; }
                        finally { authorization.cancel(false); }
                    });
                    result="Saved configuration generation "+next.generation()+". Previous file is backed up.";
                } catch(java.io.IOException|ConfigurationException|IllegalArgumentException failure) { result="Edit rejected: "+failure.getMessage(); }
                catch(RuntimeException failure) { result="Edit failed; active configuration retained."; plugin.getLogger().warning("Administrative edit failed: "+failure.getClass().getSimpleName()); }
                String message=result;
                try { if(!closed) audience.dispatch(plugin.scheduler(),recipient->{
                    if(closed||!allowed(recipient,permission)) return;
                    plugin.reply(recipient,"admin-result",Map.of("result",message));
                    if(message.startsWith("Saved")) { plugin.getLogger().info("Administrative configuration edit: "+edit.file()+" "+String.join(".",edit.path())); if(completed!=null) completed.run(); }
                },()->{}); } catch(org.bukkit.plugin.IllegalPluginAccessException|RejectedExecutionException disabled) { }
            });
            plugin.reply(sender,"admin-result",Map.of("result","Configuration edit queued."));
        } catch(RejectedExecutionException busy) { plugin.reply(sender,"output-busy",Map.of()); }
    }
    /** Authorize on the requester, mutate on the target, and reply on the original requester session. */
    public void control(CommandSender sender,dev.aegisac.paper.player.PlayerDirectory.Handle target,String permission,Runnable action,Runnable completed) {
        var audience=dev.aegisac.paper.scheduler.SessionAudience.capture(sender,plugin.players());
        if(closed||!allowed(sender,permission)) { plugin.reply(sender,"denied",Map.of()); return; }
        Runnable unavailable=()->reply(audience,"Player session unavailable");
        try { target.run(()->{
            if(closed||!audience.current()) return;
            try { action.run(); }
            catch(IllegalArgumentException invalid) { reply(audience,invalid.getMessage()); return; }
            try { audience.execute(plugin.scheduler(),recipient->{
                if(!closed&&allowed(recipient,permission)) completed.run();
            },()->{}); } catch(RejectedExecutionException|org.bukkit.plugin.IllegalPluginAccessException stopped) { }
        },unavailable); } catch(RejectedExecutionException|org.bukkit.plugin.IllegalPluginAccessException busy) { plugin.reply(sender,"output-busy",Map.of()); }
    }
    private void reply(dev.aegisac.paper.scheduler.SessionAudience audience,String text) {
        try { if(!closed) audience.execute(plugin.scheduler(),recipient->plugin.reply(recipient,"admin-result",Map.of("result",text)),()->{}); }
        catch(RejectedExecutionException|org.bukkit.plugin.IllegalPluginAccessException stopped) { }
    }
    public void freeze(Player p,long duration) {
        plugin.scheduler().requireEntity(p);
        var handle=plugin.directory().find(p.getUniqueId());
        if(handle==null||handle.address()!=p) throw new IllegalArgumentException("Player session unavailable");
        if(duration<=0||duration>600_000_000_000L) throw new IllegalArgumentException("Freeze duration must be at most 10 minutes");
        long until=System.nanoTime()+duration;
        synchronized(freezes) {
            if(closed) return;
            if(!freezes.containsKey(p.getUniqueId())&&freezes.size()>=128) throw new IllegalArgumentException("Freeze capacity reached");
            freezes.put(p.getUniqueId(),new Freeze(handle,until,new java.util.concurrent.atomic.AtomicBoolean()));
            handle.data().frozenUntil(until,true);
        }
        plugin.reply(p,"admin-result",Map.of("result","A staff member has temporarily frozen your movement."));
    }
    public void unfreeze(UUID uuid) { var f=freezes.get(uuid); if(f!=null) release(uuid,f); }
    private void release(UUID uuid,Freeze freeze) {
        plugin.scheduler().requireEntity(freeze.handle().address());
        if(freezes.remove(uuid,freeze)) {
            freeze.handle().data().frozenUntil(0,false);
            if(freeze.handle().current()) plugin.reply(freeze.handle().address(),"admin-result",Map.of("result","Your staff freeze has ended."));
        }
    }
    private void unfreeze(Player player) {
        var freeze=freezes.get(player.getUniqueId());
        if(freeze!=null&&freeze.handle().address()==player) release(player.getUniqueId(),freeze);
    }
    private boolean frozen(Player p) {
        var f=freezes.get(p.getUniqueId()); if(f==null) return false;
        if(f.handle().address()!=p||!f.handle().current()) return false;
        if(System.nanoTime()-f.until()>=0) { release(p.getUniqueId(),f); return false; } return true;
    }
    public void tick() {
        long now=System.nanoTime();
        freezes.forEach((uuid,f)->{
            if(!f.handle().current()) { if(freezes.remove(uuid,f)) f.handle().data().frozenUntil(0,false); }
            else if(now-f.until()>=0&&f.pending().compareAndSet(false,true)) {
                try { f.handle().run(()->release(uuid,f),()->{ if(freezes.remove(uuid,f)) f.handle().data().frozenUntil(0,false); }); }
                catch(RejectedExecutionException|org.bukkit.plugin.IllegalPluginAccessException busy) { f.pending().set(false); }
            }
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e) {
        if(e instanceof PlayerTeleportEvent||e.getTo()==null||!frozen(e.getPlayer())) return;
        var from=e.getFrom(); var to=e.getTo();
        if(from.getX()!=to.getX()||from.getY()!=to.getY()||from.getZ()!=to.getZ()) { var held=from.clone(); held.setYaw(to.getYaw()); held.setPitch(to.getPitch()); e.setTo(held); }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e) { unfreeze(e.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent e) { unfreeze(e.getPlayer()); }
    @EventHandler public void death(org.bukkit.event.entity.PlayerDeathEvent e) { unfreeze(e.getEntity()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e) { if(frozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interactEntity(PlayerInteractEntityEvent e) { if(frozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(org.bukkit.event.block.BlockPlaceEvent e) { if(frozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void destroy(org.bukkit.event.block.BlockBreakEvent e) { if(frozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(org.bukkit.event.entity.EntityDamageByEntityEvent e) { if(e.getDamager() instanceof Player p&&frozen(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventory(org.bukkit.event.inventory.InventoryClickEvent e) { if(e.getWhoClicked() instanceof Player p&&frozen(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drag(org.bukkit.event.inventory.InventoryDragEvent e) { if(e.getWhoClicked() instanceof Player p&&frozen(p)) e.setCancelled(true); }
    @Override public void close() {
        closed=true; worker.shutdownNow();
        synchronized(freezes) { freezes.values().forEach(f->f.handle().data().frozenUntil(0,false)); freezes.clear(); }
    }
}
