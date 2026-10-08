package dev.aegisac.common.config;
import java.util.*;
import java.util.function.Predicate;
/** Validated, bounded inventory layout. No item text carries an administrative action. */
public record GuiSettings(boolean enabled,int rows,List<Integer> content,Map<String,Integer> controls,
        Map<String,String> titles,Map<String,Style> styles,List<String> links) {
    public record Style(String material,String name,List<String> lore) { }
    public static final List<String> MENUS=List.of("dashboard","checks","combat","movement","world","player","inventory","protocol","exploit","players","profile","history","settings");
    @SuppressWarnings("unchecked") public static GuiSettings load(Map<String,Object> doc,Predicate<String> material) throws ConfigurationException {
        int rows=(Integer)doc.get("rows"); if(rows<2||rows>6) throw bad("rows","Expected 2..6");
        var raw=(List<?>)doc.get("content-slots"); var content=new ArrayList<Integer>(); var used=new HashSet<Integer>();
        if(raw.isEmpty()) throw bad("content-slots","At least one content slot is required");
        for(Object o:raw) { if(!(o instanceof Integer n)||n<0||n>=rows*9||!used.add(n)) throw bad("content-slots","Slots must be unique integers within inventory"); content.add(n); }
        var controls=new LinkedHashMap<String,Integer>();
        for(var e:((Map<String,Object>)doc.get("controls")).entrySet()) { int n=(Integer)e.getValue(); if(n<0||n>=rows*9||!used.add(n)) throw bad("controls."+e.getKey(),"Slot overlaps or exceeds inventory"); controls.put(e.getKey(),n); }
        var titles=new LinkedHashMap<String,String>();
        for(var e:((Map<String,Object>)doc.get("titles")).entrySet()) { String s=(String)e.getValue(); text(s,128,"titles"); titles.put(e.getKey(),s); }
        var styles=new LinkedHashMap<String,Style>();
        for(var e:((Map<String,Object>)doc.get("items")).entrySet()) {
            var item=(Map<String,Object>)e.getValue(); String m=(String)item.get("material"),name=(String)item.get("name");
            if(!m.matches("[A-Z_]{1,64}")||!material.test(m)) throw bad("items."+e.getKey()+".material","Expected a supported item material");
            text(name,256,"items.name"); var lore=(List<?>)item.get("lore"); if(lore.size()>8) throw bad("items.lore","At most 8 lines");
            var lines=new ArrayList<String>(); for(Object line:lore) { if(!(line instanceof String s)) throw bad("items.lore","Expected strings"); text(s,512,"items.lore"); lines.add(s); }
            styles.put(e.getKey(),new Style(m,name,List.copyOf(lines)));
        }
        var links=new ArrayList<String>(); for(Object o:(List<?>)doc.get("dashboard-links")) { if(!(o instanceof String s)||!MENUS.contains(s)||Set.of("dashboard","profile","history").contains(s)||links.contains(s)) throw bad("dashboard-links","Expected unique navigable menus"); links.add(s); }
        return new GuiSettings((Boolean)doc.get("enabled"),rows,List.copyOf(content),Map.copyOf(controls),Map.copyOf(titles),Map.copyOf(styles),List.copyOf(links));
    }
    private static void text(String s,int max,String path) throws ConfigurationException { if(s.isBlank()||s.length()>max||s.indexOf('\n')>=0) throw bad(path,"Text is blank, multiline or too long"); }
    private static ConfigurationException bad(String path,String reason) { return new ConfigurationException("gui.yml",path,-1,reason); }
}
