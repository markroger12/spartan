package dev.aegisac.common.trace;

import dev.aegisac.common.packet.PacketFrame;
import dev.aegisac.common.world.WorldView;
import dev.aegisac.common.check.OwnerObservation;
import dev.aegisac.common.check.ServerTickHealth;
import dev.aegisac.common.combat.CombatOwnerObservation;
import dev.aegisac.common.guard.GuardOwnerObservation;
import dev.aegisac.common.bedrock.IdentityObservation;

/** Development observation at processing entry; asynchronous owner publications may race analysis.
 * No player name/UUID, configuration documents, webhook URLs or raw payload bodies are stored.
 * Coordinates, entity UUIDs, brands and world names can still identify a session: keep files private.
 */
public record TraceEntry(PacketFrame frame, long processedNanos, long lossEpoch, long generation,
        String analysisFingerprint, long providerEpoch, int entityId, WorldView.State world,
        OwnerObservation owner, CombatOwnerObservation combatOwner, GuardOwnerObservation guardOwner,
        IdentityObservation identity, ServerTickHealth.Snapshot health, boolean manualExemptions) { }
