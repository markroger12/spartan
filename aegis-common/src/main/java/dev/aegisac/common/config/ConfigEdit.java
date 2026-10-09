package dev.aegisac.common.config;
import java.util.*;
/** Explicitly scoped administrative edits: one validated scalar in one document. */
public record ConfigEdit(String file,List<String> path,Object value) {
    public ConfigEdit { path=List.copyOf(path); }
    public static ConfigEdit check(String id,boolean enabled) { var e=CheckCatalog.find(id); return new ConfigEdit("checks/"+e.category()+".yml",List.of("checks",e.id(),"enabled"),enabled); }
    public static ConfigEdit punishment(String id,boolean enabled) { return new ConfigEdit("punishments.yml",id==null?List.of("enabled"):List.of("checks",CheckCatalog.find(id).id(),"enabled"),enabled); }
    public static ConfigEdit profile(String world,String profile) { return new ConfigEdit("config.yml",world==null?List.of("default-profile"):List.of("world-profiles",world),profile); }
    public void validate() {
        if(file.equals("config.yml") && value instanceof String s && Set.of("strict","balanced","lenient").contains(s) && (path.equals(List.of("default-profile"))||path.size()==2&&path.getFirst().equals("world-profiles")&&!path.get(1).isBlank()&&path.get(1).length()<=128)) return;
        if(value instanceof Boolean) {
            if(file.equals("punishments.yml")&&path.equals(List.of("enabled"))) return;
            if(path.size()==3&&path.getFirst().equals("checks")&&path.getLast().equals("enabled")) {
                var e=CheckCatalog.find(path.get(1));
                if(file.equals("punishments.yml")||file.equals("checks/"+e.category()+".yml")) return;
            }
        }
        throw new IllegalArgumentException("Unsupported administrative edit");
    }
}
