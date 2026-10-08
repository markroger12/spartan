package dev.aegisac.common.config;
import java.util.*;
/** Stable identifiers shared by commands, configuration edits and menus. */
public final class CheckCatalog {
    public record Entry(String id,String category,boolean available,String description) { }
    public static final List<Entry> ALL;
    static {
        var entries=new ArrayList<Entry>();
        for(var id:dev.aegisac.common.check.CheckId.values()) entries.add(new Entry(id.name(),"movement",id.implemented,id.description));
        for(var id:dev.aegisac.common.combat.CombatId.values()) entries.add(new Entry(id.name(),"combat",true,id.description));
        for(var id:dev.aegisac.common.guard.GuardId.values()) entries.add(new Entry(id.name(),id.category,id.implemented,id.description));
        ALL=List.copyOf(entries);
    }
    public static Entry find(String id) { return ALL.stream().filter(e->e.id().equalsIgnoreCase(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown check")); }
    @SuppressWarnings("unchecked") public static boolean enabled(ConfigSnapshot c,Entry e) {
        var checks=(Map<String,Object>)c.documents().get("checks/"+e.category()+".yml").get("checks");
        return Boolean.TRUE.equals(((Map<String,Object>)checks.get(e.id())).get("enabled"));
    }
    private CheckCatalog() { }
}
