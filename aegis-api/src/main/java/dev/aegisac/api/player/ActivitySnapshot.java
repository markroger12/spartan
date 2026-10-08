package dev.aegisac.api.player;
/** -1 inventory/slot values mean unknown. Server corrections stay distinct from client claims. */
public record ActivitySnapshot(boolean sprinting, boolean sneaking, boolean clientFlying,
                               boolean serverAllowsFlight, int heldSlot, int openWindow,
                               int inventoryStateId, long lastAttackNanos, long lastPlaceNanos,
                               long lastDigNanos, long lastUseNanos, long lastInventoryNanos,
                               long lastVelocityNanos, long lastTeleportNanos, int lastTeleportId,
                               int lastConfirmedTeleportId, int lastBlockAcknowledgement,
                               long malformedObservations) { }
