package dev.aegisac.common.config;
import dev.aegisac.api.player.BedrockStatus;
import java.util.Map;
/** A complete validated generation. All members are immutable. */
public record ConfigSnapshot(long generation, String defaultProfile, Map<String, String> worldProfiles,
                             boolean packetMetrics, PipelineSettings pipeline, PhysicsSettings physics, MovementSettings movement, CombatSettings combat, GuardSettings guard, EditionSettings edition, OutputSettings output, ExemptionSettings exemptions, Map<String, String> messages,
                             Map<String, Map<String, Object>> documents) {
    public ConfigSnapshot {
        worldProfiles = Map.copyOf(worldProfiles);
        messages = Map.copyOf(messages);
        documents = Map.copyOf(documents); // descendants frozen by the loader
    }
    public String message(String key) {
        String message = messages.get(key);
        if (message == null) throw new IllegalArgumentException("Unknown message key: " + key);
        return message;
    }
    /** Difficulty and edition are independent, so a strict world cannot erase Bedrock policy. */
    public ProfileSelection profileFor(String world, BedrockStatus identity) {
        return new ProfileSelection(worldProfiles.getOrDefault(world, defaultProfile),
                identity == BedrockStatus.JAVA ? "java" : "bedrock");
    }
    public record ProfileSelection(String difficulty, String edition) { }
}
