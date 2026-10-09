package dev.aegisac.paper.packet;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import dev.aegisac.common.packet.ClientProtocol;
import dev.aegisac.common.packet.ClientVersionProvider;
import java.util.EnumMap;
import java.util.Map;
/** Builds capability mappings once; no reflection, dynamic lookup or latest-version guess per packet. */
public final class PacketEventsVersionProvider implements ClientVersionProvider<User> {
    private final Map<ClientVersion, ClientProtocol> versions;
    @SuppressWarnings("deprecation") // Explicitly preserve unknown semantics for legacy PacketEvents sentinels.
    public PacketEventsVersionProvider() {
        EnumMap<ClientVersion, ClientProtocol> values = new EnumMap<>(ClientVersion.class);
        for (ClientVersion version : ClientVersion.values()) {
            boolean known = version != ClientVersion.UNKNOWN && version != ClientVersion.LOWER_THAN_SUPPORTED_VERSIONS
                    && version != ClientVersion.HIGHER_THAN_SUPPORTED_VERSIONS;
            values.put(version, known ? new ClientProtocol(version.getProtocolVersion(), version.getReleaseName(), true,
                    version.isNewerThanOrEquals(ClientVersion.V_1_17), version.isNewerThanOrEquals(ClientVersion.V_1_9),
                    version.isNewerThanOrEquals(ClientVersion.V_1_17_1)) : ClientProtocol.UNKNOWN);
        }
        versions = Map.copyOf(values);
    }
    @Override public ClientProtocol protocol(User user) {
        ClientVersion version = user.getClientVersion();
        return version == null ? ClientProtocol.UNKNOWN : versions.get(version);
    }
}
