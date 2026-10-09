package dev.aegisac.paper.scheduler;
import dev.aegisac.common.player.PlayerData;
import dev.aegisac.common.player.PlayerRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.function.Consumer;
/** A session-addressed reply target. No UUID lookup can redirect old work to a reconnected player. */
public final class SessionAudience {
    private final CommandSender sender; private final PlayerRegistry players; private final UUID uuid; private final PlayerData session;
    private SessionAudience(CommandSender sender,PlayerRegistry players,UUID uuid,PlayerData session) { this.sender=sender; this.players=players; this.uuid=uuid; this.session=session; }
    public static SessionAudience capture(CommandSender sender,PlayerRegistry players) {
        if(sender instanceof Player p) {
            UUID uuid=p.getUniqueId(); var session=players.find(uuid);
            if(session==null) throw new IllegalArgumentException("No active player session");
            return new SessionAudience(sender,players,uuid,session);
        }
        if(!(sender instanceof ConsoleCommandSender)) throw new IllegalArgumentException("This asynchronous command requires a player or server console");
        return new SessionAudience(sender,players,null,null);
    }
    public boolean current() { return uuid==null||players.find(uuid)==session; }
    public void execute(PlatformScheduler scheduler,Consumer<CommandSender> action,Runnable retired) {
        boolean owned=sender instanceof Player p?scheduler.owns(p):scheduler.globalThread();
        if(owned) { if(current()) action.accept(sender); else retired.run(); }
        else dispatch(scheduler,action,retired);
    }
    /** Callbacks and permission checks execute on the appropriate owner. Retirement cleanup must be data-only. */
    public PlatformScheduler.Task dispatch(PlatformScheduler scheduler,Consumer<CommandSender> action,Runnable retired) {
        if(sender instanceof Player p) return scheduler.entity(p,1,0,this::current,()->action.accept(p),retired);
        return scheduler.global(1,0,()->action.accept(sender));
    }
}
