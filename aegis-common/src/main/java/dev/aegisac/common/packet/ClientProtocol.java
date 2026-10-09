package dev.aegisac.common.packet;
/** Capability mapping supplied by the adapter, never inferred from the server version. */
public record ClientProtocol(int id, String release, boolean known, boolean pingPong,
                             boolean teleportConfirmation, boolean modernInventory) {
    public static final ClientProtocol UNKNOWN = new ClientProtocol(-1, "unknown", false, false, false, false);
}
