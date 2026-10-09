package dev.aegisac.common.packet;
/**
 * Immutable bounded values copied before PacketEvents releases its event/buffer.
 * Values are observations, not trusted geometry, inventory state or cheat evidence.
 * No platform objects, raw buffers, item NBT or entity/world references are retained.
 */
public sealed interface NormalizedPacket {
    PacketKind kind();
    record Movement(boolean position, boolean rotation, double x, double y, double z,
                    float yaw, float pitch, boolean onGround, boolean horizontalCollision) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.MOVEMENT; }
    }
    enum TimingKind { KEEP_ALIVE, PING, WINDOW, TELEPORT }
    record Timing(TimingKind type, long id, int window, boolean accepted) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.TIMING; }
    }
    record Interaction(int entityId, String action, String hand, boolean targetPresent,
                       double targetX, double targetY, double targetZ) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.ENTITY_INTERACTION; }
    }
    record BlockAction(boolean placement, int x, int y, int z, int face, int sequence,
                       String action, String hand, float cursorX, float cursorY, float cursorZ) implements NormalizedPacket {
        public PacketKind kind() { return placement ? PacketKind.PLACEMENT : PacketKind.DIGGING; }
    }
    record UseItem(String hand, int sequence, float yaw, float pitch) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.USE_ITEM; }
    }
    record HeldItem(int slot) implements NormalizedPacket { public PacketKind kind() { return PacketKind.HELD_ITEM; } }
    enum InventoryAction { OPEN, CLOSE, CLICK, CONTENTS, SLOT, BUTTON }
    record Inventory(InventoryAction action, int window, int stateId, int slot, int button,
                     String clickType) implements NormalizedPacket { public PacketKind kind() { return PacketKind.INVENTORY; } }
    record EntityAction(int entityId, String action, int jumpBoost) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.ENTITY_ACTION; }
    }
    record Abilities(boolean flying, boolean serverAuthority, boolean flightAllowed, boolean creative,
                     float flySpeed, float walkSpeed) implements NormalizedPacket { public PacketKind kind() { return PacketKind.ABILITIES; } }
    record Vehicle(double x, double y, double z, float yaw, float pitch) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.VEHICLE; }
    }
    record Input(float forward, float sideways, boolean jump, boolean sneak, boolean sprint,
                 boolean dismount, int directionalMask) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.INPUT; }
    }
    record RotationCorrection(float yaw, float pitch, boolean relativeYaw, boolean relativePitch) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.TELEPORT; }
    }
    /** Relative flags remain raw: no assumed absolute position or velocity correction. */
    record Teleport(int id, double x, double y, double z, float yaw, float pitch,
                    int relativeFlags, double deltaX, double deltaY, double deltaZ) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.TELEPORT; }
    }
    record Impulse(boolean explosion, int entityId, double x, double y, double z) implements NormalizedPacket {
        public PacketKind kind() { return explosion ? PacketKind.EXPLOSION : PacketKind.VELOCITY; }
    }
    record Effect(int entityId, String effect, int amplifier, int duration, boolean removed) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.EFFECT; }
    }
    record EntityStatus(int entityId, int status) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.ENTITY_STATUS; }
    }
    record BlockAcknowledgement(int sequence) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.BLOCK_ACKNOWLEDGEMENT; }
    }
    /** Small enum-derived labels only; no client-supplied free-form payload strings. */
    record Action(PacketKind kind, String action) implements NormalizedPacket { }
    /** Body intentionally not retained. Size is readable bytes at normalization, not total wire bytes. */
    record Payload(int bytes, String channel, String brand) implements NormalizedPacket { public PacketKind kind() { return PacketKind.PAYLOAD; } }
    record EntitySpawn(int entityId, java.util.UUID uuid, boolean player, double x, double y, double z) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.ENTITY_TRACKING; }
    }
    record EntityMove(int entityId, boolean relative, boolean discontinuity, int relativeFlags,
                      double x, double y, double z) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.ENTITY_TRACKING; }
    }
    /** Metadata/attributes can change dimensions; no platform metadata/NBT is retained. */
    record EntityDimensionsUnknown(int entityId) implements NormalizedPacket {
        public PacketKind kind() { return PacketKind.ENTITY_TRACKING; }
    }
    record EntityRemove(java.util.List<Integer> entityIds) implements NormalizedPacket {
        public EntityRemove {
            entityIds=java.util.List.copyOf(entityIds);
            if(entityIds.size()>1024) throw new IllegalArgumentException("Removal budget exceeded");
        }
        public PacketKind kind() { return PacketKind.ENTITY_TRACKING; }
    }
    enum Other implements NormalizedPacket {
        INSTANCE;
        public PacketKind kind() { return PacketKind.OTHER; }
    }
}
