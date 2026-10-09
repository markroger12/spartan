package dev.aegisac.common.check;
import java.util.Set;
/** Owning-thread policy observation. Never retain Player, World, PermissionAttachment or other platform objects. */
public record OwnerObservation(long observedNanos,String world,boolean creativeOrSpectator,boolean flight,
                               boolean vehicle,boolean gliding,Set<String> bypasses) {
    public OwnerObservation { world=world==null?"":world; bypasses=Set.copyOf(bypasses); }
}
