package dev.aegisac.paper.admin;
import dev.aegisac.paper.AegisPlugin;
import dev.aegisac.common.config.*;
import dev.aegisac.common.output.OutputRecord;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;
/** Inventory identity and immutable server-side actions are authoritative; item metadata is display only. */
@SuppressWarnings("deprecation")
public final class MenuService implements Listener,AutoCloseable {
    private record Action(String type,String value,String permission) { }
    private record Entry(String style,Map<String,String> values,Action action) { }
    private static final class Menu implements InventoryHolder {
        final UUID viewer,target; final long viewerSession,targetSession,generation; final String name,world; final int page;
        final Map<Integer,Action> actions=new HashMap<>(); Inventory inventory; boolean pending,scheduled;
        Menu(UUID viewer,long viewerSession,UUID target,long targetSession,long generation,String name,String world,int page) {
            this.viewer=viewer; this.viewerSession=viewerSession; this.target=target; this.targetSession=targetSession; this.generation=generation; this.name=name; this.world=world; this.page=page;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private final AegisPlugin plugin; private final Map<UUID,Menu> open=new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean closed;
    public MenuService(AegisPlugin plugin) { this.plugin=plugin; }
    private boolean access(Player p,String menu) {
        if(!AdminService.allowed(p,"gui")) return false;
        return switch(menu) {
            case "dashboard"->true;
            case "profile","players"->AdminService.allowed(p,"profile");
            case "history"->AdminService.allowed(p,"violations");
            case "settings"->AdminService.allowed(p,"profile.set")||AdminService.allowed(p,"punishment");
            default->AdminService.allowed(p,"checks");
        };
    }
    public void open(Player viewer,String name,UUID target,int requestedPage) {
        plugin.scheduler().requireEntity(viewer);
        if(closed) return;
        if(!GuiSettings.MENUS.contains(name)||!access(viewer,name)) { plugin.reply(viewer,"denied",Map.of()); return; }
        var config=plugin.configuration().current(); GuiSettings gui;
        try { gui=GuiSettings.load(config.documents().get("gui.yml"),m->true); } catch(ConfigurationException impossible) { throw new IllegalStateException(impossible); }
        if(!gui.enabled()) { plugin.reply(viewer,"admin-result",Map.of("result","Inventory menus are disabled in gui.yml.")); return; }
        var owner=plugin.players().snapshot(viewer.getUniqueId()).orElse(null); if(owner==null) return;
        var player=target==null?null:plugin.directory().find(target);
        var observation=player==null?null:player.observation();
        var subject=target==null?null:plugin.players().snapshot(target).orElse(null);
        if(target!=null&&(player==null||subject==null||observation==null||!plugin.directory().visible(viewer,player))) { plugin.reply(viewer,"player-missing",Map.of()); return; }
        if(Set.of("profile","history").contains(name)&&target==null) return;
        if(!open.containsKey(viewer.getUniqueId())&&open.size()>=128) { plugin.reply(viewer,"output-busy",Map.of()); return; }
        var entries=new ArrayList<Entry>();
        switch(name) {
            case "dashboard" -> { for(String link:gui.links()) if(access(viewer,link)) entries.add(new Entry("link",Map.of("label",link),new Action("open",link,"gui"))); }
            case "settings" -> {
                entries.add(new Entry("stat",Map.of("label","Generation","value",Long.toString(config.generation())),null));
                entries.add(new Entry("stat",Map.of("label","Mode","value","Experimental; diagnostics cannot punish"),null));
            }
            case "players" -> {
                for(var p:plugin.directory().visible(viewer))
                    if(plugin.players().find(p.getUniqueId())!=null) entries.add(new Entry("player",Map.of("player",p.getName(),"risk",OutputRecord.number(plugin.outputs().violations(p.getUniqueId()).totalRisk())),new Action("player",p.getUniqueId().toString(),"profile")));
            }
            case "profile" -> { for(var e:ProfileView.values(plugin,player).entrySet()) entries.add(new Entry("stat",Map.of("label",e.getKey(),"value",e.getValue()),null));
                if(access(viewer,"history")) entries.add(new Entry("link",Map.of("label","Violation history"),new Action("open","history","violations"))); }
            case "history" -> {
                var records=new ArrayList<>(plugin.outputs().recent(target)); Collections.reverse(records);
                for(var r:records) entries.add(new Entry("evidence",Map.of("check",r.check(),"type",r.type(),"value",r.timestamp()+" risk="+r.values().getOrDefault("risk","unknown")+" "+r.values().getOrDefault("evidence","")),null));
            }
            default -> { for(var e:CheckCatalog.ALL) if(name.equals("checks")||name.equals(e.category())) {
                boolean categoryEnabled=Boolean.TRUE.equals(config.documents().get("checks/"+e.category()+".yml").get("enabled"));
                String status=!e.available()?"UNAVAILABLE":!CheckCatalog.enabled(config,e)?"DISABLED":!categoryEnabled?"CATEGORY DISABLED":"ENABLED (experimental; edition policy applies)";
                entries.add(new Entry("check",Map.of("check",e.id(),"status",status,"punishment",Boolean.toString(config.output().punishments().rules().get(e.id()).enabled())),new Action("check",e.id(),"toggle")));
            } }
        }
        int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/gui.content().size())));
        var menu=new Menu(viewer.getUniqueId(),owner.sessionId(),target,subject==null?-1:subject.sessionId(),config.generation(),name,observation==null?null:observation.world(),page);
        menu.inventory=plugin.getServer().createInventory(menu,gui.rows()*9,ChatColor.translateAlternateColorCodes('&',gui.titles().get(name)));
        for(int i=0;i<gui.content().size();i++) { int index=page*gui.content().size()+i; if(index>=entries.size()) break; var e=entries.get(index); put(menu,gui,gui.content().get(i),e.style(),e.values(),e.action()); }
        put(menu,gui,gui.controls().get("close"),"close",Map.of(),new Action("close","","gui"));
        if(!name.equals("dashboard")) put(menu,gui,gui.controls().get("back"),"back",Map.of(),new Action("open","dashboard","gui"));
        if(page>0) put(menu,gui,gui.controls().get("previous"),"previous",Map.of(),new Action("page",Integer.toString(page-1),"gui"));
        if((page+1)*gui.content().size()<entries.size()) put(menu,gui,gui.controls().get("next"),"next",Map.of(),new Action("page",Integer.toString(page+1),"gui"));
        if(name.equals("profile")&&AdminService.allowed(viewer,"reset")) put(menu,gui,gui.controls().get("reset"),"reset",Map.of(),new Action("reset","","reset"));
        if(name.equals("settings")&&AdminService.allowed(viewer,"punishment")) put(menu,gui,gui.controls().get("punishment"),"punishment",Map.of("value",Boolean.toString(config.output().punishments().enabled())),new Action("punishment","","punishment"));
        if((name.equals("profile")||name.equals("settings"))&&AdminService.allowed(viewer,"profile.set")) {
            String value=name.equals("profile")?config.worldProfiles().getOrDefault(menu.world,config.defaultProfile()):config.defaultProfile();
            put(menu,gui,gui.controls().get("profile"),"profile",Map.of("value",value),new Action("profile",value,"profile.set"));
        }
        viewer.closeInventory();
        boolean admitted;
        synchronized(open) { admitted=!closed&&(open.containsKey(menu.viewer)||open.size()<128); if(admitted) open.put(menu.viewer,menu); }
        if(admitted) viewer.openInventory(menu.inventory); else plugin.reply(viewer,"output-busy",Map.of());
    }
    private void put(Menu menu,GuiSettings gui,int slot,String style,Map<String,String> values,Action action) {
        var s=gui.styles().get(style); var item=new ItemStack(Material.valueOf(s.material())); var meta=item.getItemMeta();
        meta.setDisplayName(render(s.name(),values)); meta.setLore(s.lore().stream().map(line->render(line,values)).toList()); item.setItemMeta(meta); menu.inventory.setItem(slot,item);
        if(action!=null) menu.actions.put(slot,action);
    }
    private static String render(String template,Map<String,String> values) {
        var safe=new HashMap<String,String>(); values.forEach((k,v)->safe.put(k,OutputRecord.clean(v,400)));
        return OutputRecord.render(ChatColor.translateAlternateColorCodes('&',template),safe);
    }
    private boolean valid(Menu menu,Player viewer) {
        if(closed||open.get(menu.viewer)!=menu||!viewer.getUniqueId().equals(menu.viewer)||viewer.getOpenInventory().getTopInventory()!=menu.inventory||!access(viewer,menu.name)) return false;
        var d=plugin.players().snapshot(menu.viewer).orElse(null);
        if(d==null||d.sessionId()!=menu.viewerSession||plugin.configuration().current().generation()!=menu.generation) return false;
        if(menu.target!=null) {
            var p=plugin.directory().find(menu.target); var s=plugin.players().snapshot(menu.target).orElse(null);
            var observation=p==null?null:p.observation();
            if(p==null||s==null||!plugin.directory().visible(viewer,p)||s.sessionId()!=menu.targetSession||observation==null||!observation.world().equals(menu.world)) return false;
        }
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean alreadyCancelled=e.isCancelled(); e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player viewer)) return;
        if(!valid(menu,viewer)) { if(!menu.scheduled) { menu.scheduled=true; defer(viewer,()->{ if(viewer.getOpenInventory().getTopInventory()==menu.inventory) viewer.closeInventory(); }); } plugin.reply(viewer,"admin-result",Map.of("result","Menu expired or access changed; open it again.")); return; }
        if(alreadyCancelled||menu.pending||menu.scheduled||e.getRawSlot()<0||e.getRawSlot()>=menu.inventory.getSize()||e.getClick()!=ClickType.LEFT&&e.getClick()!=ClickType.RIGHT) return;
        var a=menu.actions.get(e.getRawSlot()); if(a==null) return;
        String permission=a.type().equals("check")&&e.getClick()==ClickType.RIGHT?"punishment":a.permission();
        if(!AdminService.allowed(viewer,permission)) { plugin.reply(viewer,"denied",Map.of()); return; }
        boolean right=e.getClick()==ClickType.RIGHT;
        // Bukkit requires opening/closing inventories after the inventory event has completed.
        menu.scheduled=true;
        defer(viewer,()->{ menu.scheduled=false; act(menu,viewer,a,permission,right); });
    }
    private void defer(Player viewer,Runnable action) {
        try { dev.aegisac.paper.scheduler.SessionAudience.capture(viewer,plugin.players()).dispatch(plugin.scheduler(),recipient->action.run(),()->{}); }
        catch(IllegalArgumentException|java.util.concurrent.RejectedExecutionException|org.bukkit.plugin.IllegalPluginAccessException unavailable) { plugin.reply(viewer,"output-busy",Map.of()); }
    }
    private void act(Menu menu,Player viewer,Action a,String permission,boolean right) {
        if(viewer==null||!valid(menu,viewer)||menu.pending||!AdminService.allowed(viewer,permission)) return;
        switch(a.type()) {
            case "close"->viewer.closeInventory();
            case "open"->open(viewer,a.value(),a.value().equals("history")?menu.target:null,0);
            case "page"->open(viewer,menu.name,menu.target,Integer.parseInt(a.value()));
            case "player"->open(viewer,"profile",UUID.fromString(a.value()),0);
            case "reset"->{
                var target=plugin.directory().find(menu.target); if(target==null||target.data().snapshot().sessionId()!=menu.targetSession) return;
                menu.pending=true;
                plugin.admin().control(viewer,target,"reset",()->{ plugin.outputs().reset(menu.target); plugin.getLogger().info("Staff reset current VL: "+menu.target); },
                    ()->{ if(open.get(menu.viewer)==menu) open(viewer,menu.name,menu.target,menu.page); });
            }
            default->{
                var c=plugin.configuration().current(); ConfigEdit edit;
                if(a.type().equals("check")) {
                    var check=CheckCatalog.find(a.value());
                    if(!check.available()) { plugin.reply(viewer,"admin-result",Map.of("result","This check model is unavailable.")); return; }
                    edit=right?ConfigEdit.punishment(check.id(),!c.output().punishments().rules().get(check.id()).enabled()):ConfigEdit.check(check.id(),!CheckCatalog.enabled(c,check));
                } else if(a.type().equals("punishment")) edit=ConfigEdit.punishment(null,!c.output().punishments().enabled());
                else { var profiles=List.of("balanced","strict","lenient"); edit=ConfigEdit.profile(menu.name.equals("profile")?menu.world:null,profiles.get((profiles.indexOf(a.value())+1)%3)); }
                menu.pending=true;
                plugin.admin().edit(viewer,menu.generation,edit,permission,()->valid(menu,viewer),()->{
                    if(open.get(menu.viewer)==menu) open(viewer,menu.name,menu.target,menu.page);
                });
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e) { if(e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true); }
    @EventHandler public void close(InventoryCloseEvent e) { if(e.getInventory().getHolder() instanceof Menu m) open.remove(m.viewer,m); }
    @Override public void close() {
        closed=true;
        for(var menu:List.copyOf(open.values())) {
            var handle=plugin.directory().find(menu.viewer);
            if(handle==null) continue;
            // During disable Folia rejects new plugin tasks. Never substitute a global inventory access.
            // Native cross-region inventory teardown remains a live release gate.
            if(plugin.scheduler().owns(handle.address())) {
                var p=handle.address(); if(p.getOpenInventory().getTopInventory()==menu.inventory) p.closeInventory();
            }
        }
        synchronized(open) { open.clear(); }
    }
}
