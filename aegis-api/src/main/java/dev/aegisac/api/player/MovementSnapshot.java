package dev.aegisac.api.player;
/** Client reports, not authoritative position, collision or legal movement predictions. */
public record MovementSnapshot(boolean positionKnown, boolean rotationKnown,
                               double x, double y, double z, float yaw, float pitch,
                               boolean onGround, boolean horizontalCollision, long observedNanos) { }
