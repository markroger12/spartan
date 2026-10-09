package dev.aegisac.api.player;
import java.util.Objects;
/** The client protocol is distinct from the server's Minecraft version. */
public record ClientIdentity(int protocolVersion, String release, BedrockStatus bedrock, String brand) {
    public ClientIdentity { Objects.requireNonNull(release); Objects.requireNonNull(bedrock); Objects.requireNonNull(brand); }
    public ClientIdentity(int protocolVersion, String release, BedrockStatus bedrock) {
        this(protocolVersion, release, bedrock, "unknown");
    }
    public static ClientIdentity unknown() { return new ClientIdentity(-1, "unknown", BedrockStatus.UNKNOWN); }
}
