package dev.aegisac.api.player;
import java.util.Set;
/** Official API observation or explicit operator topology assertion; device metadata is informational. */
public record EditionSnapshot(BedrockStatus status,String source,String device,String input,String version,
                              long observedNanos,Set<String> reasons) {
    public EditionSnapshot { reasons=Set.copyOf(reasons); }
    public static EditionSnapshot unknown(String reason) { return new EditionSnapshot(BedrockStatus.UNKNOWN,"NONE","UNKNOWN","UNKNOWN","unknown",0,Set.of(reason)); }
    public String analysisKey() { return status+":"+source+":"+device+":"+input+":"+version; }
}
