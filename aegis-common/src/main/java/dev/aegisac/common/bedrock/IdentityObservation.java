package dev.aegisac.common.bedrock;
import dev.aegisac.api.player.EditionSnapshot;
public record IdentityObservation(long generation,long providerEpoch,EditionSnapshot identity) { }
