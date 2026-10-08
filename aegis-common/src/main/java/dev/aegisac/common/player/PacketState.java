package dev.aegisac.common.player;
import dev.aegisac.api.player.ActivitySnapshot;
import dev.aegisac.api.player.MovementSnapshot;
import dev.aegisac.common.connection.TransactionTracker;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;

/** Mutable observations owned by the session worker; no legal-movement or inventory assumptions. */
final class PacketState {
    private boolean position, rotation, ground, collision, sprint, sneak, flying, allowed;
    private double x, y, z;
    private float yaw, pitch;
    private int slot = -1, window = -1, inventoryState = -1, teleport = -1, confirmedTeleport = -1, blockAck = -1;
    private long movementTime, attackTime, placeTime, digTime, useTime, inventoryTime, velocityTime, teleportTime, malformed;
    private int selfId = -1;
    void bindEntity(int entityId) { selfId = entityId; }
    void reset() {
        position = rotation = sprint = sneak = flying = allowed = ground = collision = false;
        slot = window = inventoryState = teleport = confirmedTeleport = blockAck = -1;
        movementTime = attackTime = placeTime = digTime = useTime = inventoryTime = velocityTime = teleportTime = 0;
    }
    boolean apply(PacketFrame frame, TransactionTracker.Acknowledgement acknowledgement) {
        long now = frame.observedNanos();
        boolean inbound = frame.direction() == PacketDirection.INBOUND;
        switch (frame.packet()) {
            case Movement value -> {
                if ((value.position() && (!Double.isFinite(value.x()) || !Double.isFinite(value.y()) || !Double.isFinite(value.z())))
                        || (value.rotation() && (!Float.isFinite(value.yaw()) || !Float.isFinite(value.pitch())))) {
                    position = rotation = false; malformed++; return false;
                }
                if (value.position()) { position = true; x = value.x(); y = value.y(); z = value.z(); }
                if (value.rotation()) { rotation = true; yaw = value.yaw(); pitch = value.pitch(); }
                ground = value.onGround(); collision = value.horizontalCollision(); movementTime = now;
            }
            case Interaction value -> { if (value.action().equals("ATTACK")) attackTime = now; }
            case BlockAction value -> { if (value.placement()) placeTime = now; else digTime = now; }
            case UseItem value -> useTime = now;
            case HeldItem value -> {
                if (value.slot() < 0 || value.slot() > 8) { slot = -1; malformed++; return false; }
                slot = value.slot();
            }
            case Inventory value -> {
                inventoryTime = now;
                if (value.action() == InventoryAction.CLOSE && value.window() == window) { window = -1; inventoryState = -1; }
                else if (!inbound && value.action() == InventoryAction.OPEN) { window = value.window(); inventoryState = value.stateId(); }
                else if (!inbound && value.window() == window && value.stateId() >= 0) inventoryState = value.stateId();
                // A client click never authoritatively opens a window or advances its state ID.
                // Cursor/player-inventory updates must not replace the currently open container.
            }
            case EntityAction value -> {
                switch (value.action()) {
                    case "START_SPRINTING" -> sprint = true;
                    case "STOP_SPRINTING" -> sprint = false;
                    case "START_SNEAKING" -> sneak = true;
                    case "STOP_SNEAKING" -> sneak = false;
                    default -> { /* Other actions remain available in bounded packet history. */ }
                }
            }
            case Abilities value -> { if (value.serverAuthority()) allowed = value.flightAllowed(); else flying = value.flying(); }
            case Teleport value -> {
                position = rotation = false; teleportTime = now; teleport = value.id();
                if (!finite(value.x(), value.y(), value.z(), value.yaw(), value.pitch(), value.deltaX(), value.deltaY(), value.deltaZ())) {
                    malformed++; return false;
                }
            }
            case Timing value -> {
                if (value.type() == TimingKind.TELEPORT && inbound && acknowledgement != null && acknowledgement.sampled())
                    confirmedTeleport = (int) value.id();
            }
            case Impulse value -> { if (value.explosion() || (selfId != -1 && value.entityId() == selfId)) velocityTime = now; }
            case RotationCorrection value -> { rotation = false; teleportTime = now; }
            case BlockAcknowledgement value -> blockAck = value.sequence();
            case Action value -> { if (value.kind() == PacketKind.WORLD_RESET) reset(); }
            default -> { /* Observed packet family stays in bounded history; later engines consume its values. */ }
        }
        return true;
    }
    private static boolean finite(double... numbers) {
        for (double number : numbers) if (!Double.isFinite(number)) return false;
        return true;
    }
    MovementSnapshot movement() { return new MovementSnapshot(position, rotation, x, y, z, yaw, pitch, ground, collision, movementTime); }
    ActivitySnapshot activity() { return new ActivitySnapshot(sprint, sneak, flying, allowed, slot, window, inventoryState,
            attackTime, placeTime, digTime, useTime, inventoryTime, velocityTime, teleportTime, teleport, confirmedTeleport, blockAck, malformed); }
}
