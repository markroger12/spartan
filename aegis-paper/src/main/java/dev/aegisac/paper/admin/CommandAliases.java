package dev.aegisac.paper.admin;
import dev.aegisac.paper.AegisPlugin;
import dev.aegisac.paper.command.AegisCommand;
import org.bukkit.command.*;
import java.util.*;
/** Additional aliases only; never replace another plugin's command. Public API reflection keeps older Bukkit linkage optional. */
public final class CommandAliases implements AutoCloseable {
    private final List<Command> owned=new ArrayList<>(); private final CommandMap commands; private final Map<String,Command> known;
    @SuppressWarnings("unchecked") public CommandAliases(AegisPlugin plugin,AegisCommand executor) {
        var aliases=(Map<String,Object>)plugin.configuration().current().documents().get("config.yml").get("command-aliases");
        if(aliases.values().stream().noneMatch(Boolean.TRUE::equals)) { commands=null; known=null; return; }
        try {
            commands=(CommandMap)plugin.getServer().getClass().getMethod("getCommandMap").invoke(plugin.getServer());
            known=(Map<String,Command>)commands.getClass().getMethod("getKnownCommands").invoke(commands);
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException("This server does not expose the public command map required for additional aliases",failure); }
        for(var entry:aliases.entrySet()) if(Boolean.TRUE.equals(entry.getValue())) {
            String name=entry.getKey();
            if(commands.getCommand(name)!=null||commands.getCommand("aegisac:"+name)!=null) { plugin.getLogger().warning("Additional command alias is already in use: "+name); continue; }
            Command alias=new Command(name) {
                @Override public boolean execute(CommandSender sender,String label,String[] args) { return plugin.isEnabled()&&executor.onCommand(sender,this,label,args); }
                @Override public List<String> tabComplete(CommandSender sender,String label,String[] args) { return plugin.isEnabled()?executor.onTabComplete(sender,this,label,args):List.of(); }
            };
            commands.register("aegisac",alias); owned.add(alias);
        }
    }
    @Override public void close() { if(known==null) return; for(String key:List.copyOf(known.keySet())) { var value=known.get(key); if(owned.contains(value)) known.remove(key,value); } for(var command:owned) command.unregister(commands); owned.clear(); }
}
