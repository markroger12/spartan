package dev.aegisac.api.check;
import java.util.List;
import java.util.Map;
/** Immutable current-generation check state and bounded recent evidence, not persistent logs. */
public record MovementChecksSnapshot(long generation,Map<String,CheckSnapshot> checks,List<MovementEvidence> evidence,
                                      SafePositionSnapshot safePosition) {
    public MovementChecksSnapshot { checks=Map.copyOf(checks); evidence=List.copyOf(evidence); }
    public static MovementChecksSnapshot empty() { return new MovementChecksSnapshot(0,Map.of(),List.of(),null); }
}
