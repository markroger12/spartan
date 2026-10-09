package dev.aegisac.common.output;
import dev.aegisac.api.player.BedrockStatus;
import java.util.UUID;
/** Copied only when evidence is emitted; contains no platform objects. */
public record DetectionEnvelope(UUID uuid,String player,long session,long lossEpoch,long providerEpoch,String identityKey,Detection detection,
        long timestamp,String client,String brand,BedrockStatus edition,double ping,double jitter,double tps,
        String world,boolean position,double x,double y,double z) { }
