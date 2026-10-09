package dev.aegisac.common.config;
import dev.aegisac.common.guard.GuardId;
import java.util.*;
public record GuardSettings(Map<String,Category> categories,Map<GuardId,Rule> rules) {
    public static final List<String> CATEGORIES=List.of("world","player","inventory","protocol","exploit");
    public record Rule(boolean enabled,double limit,int cooldownMillis,String bedrockMode) { }
    public record Category(boolean enabled,int evidenceCapacity,int graceMillis,int maximumDelayMillis) { }
    public GuardSettings { categories=Map.copyOf(categories); rules=Map.copyOf(rules); }
    public Category category(GuardId id) { return categories.get(id.category); }
    public static GuardSettings defaults() {
        var categories=new LinkedHashMap<String,Category>();
        CATEGORIES.forEach(c->categories.put(c,new Category(true,32,2000,250)));
        var rules=new EnumMap<GuardId,Rule>(GuardId.class);
        for(var id:GuardId.values()) rules.put(id,new Rule(id.implemented,id.defaultLimit,1000,"diagnostic"));
        return new GuardSettings(categories,rules);
    }
}
