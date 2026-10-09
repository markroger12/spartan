package dev.aegisac.api.check;
import java.util.Set;
/** Buffers are per-check experimental state, not global risk or punishment instructions. */
public record CheckSnapshot(String id,String status,double buffer,long evaluated,long findings,long diagnostics,
                            Set<String> reasons) {
    public CheckSnapshot { reasons=Set.copyOf(reasons); }
}
