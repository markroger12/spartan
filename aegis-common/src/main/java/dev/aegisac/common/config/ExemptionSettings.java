package dev.aegisac.common.config;
import java.util.Set;
public record ExemptionSettings(boolean enabled,boolean permissions,boolean creativeOrSpectator,boolean flight,Set<String> worlds) {
    public ExemptionSettings { worlds=Set.copyOf(worlds); }
}
