package dev.aegisac.api.player;
import java.util.UUID;
/** Immutable observation. nanoTime values are relative to this JVM, not wall time. */
public record PlayerSnapshot(UUID uuid, String name, long sessionId, long joinedAtNanos,
                             ClientIdentity client, long inboundPackets, long outboundPackets,
                             long lastInboundNanos, long lastOutboundNanos,
                             ConnectionSnapshot connection, MovementSnapshot movement, ActivitySnapshot activity, PhysicsSnapshot physics, dev.aegisac.api.check.MovementChecksSnapshot movementChecks, dev.aegisac.api.check.CombatSnapshot combat, dev.aegisac.api.check.GuardSnapshot guard, EditionSnapshot edition, BedrockSnapshot bedrockAnalysis) { }
