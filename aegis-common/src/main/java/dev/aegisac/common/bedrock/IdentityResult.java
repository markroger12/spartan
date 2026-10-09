package dev.aegisac.common.bedrock;
import java.util.Set;
public record IdentityResult(String provider,Outcome outcome,String device,String input,String version,Set<String> reasons) {
    public enum Outcome { POSITIVE,NEGATIVE,ABSENT,DISABLED,FAILURE }
    public IdentityResult { reasons=Set.copyOf(reasons); }
    public static IdentityResult of(String provider,Outcome outcome) { return new IdentityResult(provider,outcome,"UNKNOWN","UNKNOWN","unknown",Set.of()); }
}
